package com.nszconverter.worker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.nszconverter.MainActivity
import com.nszconverter.NSZConverterApp
import com.nszconverter.R
import com.nszconverter.data.local.JobDao
import com.nszconverter.data.local.JobEntity
import com.nszconverter.data.prefs.PreferencesRepository
import com.nszconverter.data.repository.PythonBridge
import com.nszconverter.domain.model.FailureReason
import com.nszconverter.domain.model.JobStatus
import com.nszconverter.util.FileManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.io.File

@HiltWorker
class ConversionWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val dao: JobDao,
    private val python: PythonBridge,
    private val fileManager: FileManager,
    private val prefs: PreferencesRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val jobId = inputData.getString(KEY_JOB_ID) ?: return Result.failure()
        val entity = dao.get(jobId) ?: return Result.failure()

        if (entity.status == JobStatus.CANCELLED.name || entity.status == JobStatus.SUCCESS.name) {
            return Result.success()
        }

        setForeground(makeForegroundInfo(entity.displayName, 0))
        markRunning(jobId)

        // 1. Verificar keys
        val keysFile = fileManager.keysFile()
        if (!keysFile.exists() || keysFile.length() == 0L) {
            fail(jobId, FailureReason.INVALID_KEYS, "no_keys_configured")
            return Result.failure()
        }
        python.setupKeys(keysFile.absolutePath)

        // 2. Verificar espacio
        val needCache = entity.sizeBytes + 64L * 1024 * 1024
        if (fileManager.availableInternalBytes() < needCache) {
            fail(jobId, FailureReason.INSUFFICIENT_SPACE_CACHE, "cache_low")
            return Result.failure()
        }

        // 3. Copiar URI source → cache
        val sourceUri = runCatching { Uri.parse(entity.sourceUri) }.getOrNull()
            ?: run { fail(jobId, FailureReason.FILE_NOT_FOUND, "bad_source_uri"); return Result.failure() }

        val cacheInput = File(fileManager.cacheInputDir(), "${entity.id}.nsz")
        val outputDir = File(fileManager.cacheOutputDir(), entity.id).apply { mkdirs() }

        try {
            val copyOk = fileManager.copyUriToFile(sourceUri, cacheInput)
            if (!copyOk) {
                fail(jobId, FailureReason.FILE_NOT_FOUND, "copy_failed")
                return Result.failure()
            }

            if (cacheInput.length() == 0L) {
                fail(jobId, FailureReason.FILE_EMPTY, "zero_bytes")
                return Result.failure()
            }

            updateProgress(jobId, 0.25f, cacheInput.length())

            // 4. Ejecutar conversión Python
            val result = python.decompress(cacheInput.absolutePath, outputDir.absolutePath)

            if (!result.success) {
                val reason = FailureReason.fromRawError(result.error)
                fail(jobId, reason, "${result.error}\n${result.stderrTail.take(2000)}")
                return Result.failure()
            }

            updateProgress(jobId, 0.85f, cacheInput.length())

            // 5. Copiar resultado al destino SAF
            val outputFile = File(result.outputPath)
            if (!outputFile.exists() || outputFile.length() == 0L) {
                fail(jobId, FailureReason.PYTHON_ERROR, "output_missing")
                return Result.failure()
            }

            val outputTree = runCatching { Uri.parse(entity.outputDirUri) }.getOrNull()
                ?: run { fail(jobId, FailureReason.FILE_NOT_FOUND, "bad_output_uri"); return Result.failure() }

            val outputName = fileManager.sanitizeFileName(
                entity.displayName.replace(Regex("\\.nsz$", RegexOption.IGNORE_CASE), "") + ".nsp"
            )

            val finalUri = fileManager.copyFileToTree(outputFile, outputTree, outputName, "application/octet-stream")
                ?: run { fail(jobId, FailureReason.PERMISSION_DENIED, "write_failed"); return Result.failure() }

            success(jobId, finalUri.toString(), result.durationSeconds)

            // 6. Borrar fuente si así lo configuró el usuario
            if (prefs.preferences.first().deleteSourceOnSuccess) {
                runCatching {
                    androidx.documentfile.provider.DocumentFile
                        .fromSingleUri(context, sourceUri)?.delete()
                }
            }

            return Result.success()
        } catch (e: Exception) {
            fail(jobId, FailureReason.UNKNOWN, e.message ?: e.javaClass.simpleName)
            return Result.failure()
        } finally {
            fileManager.cleanupCache(cacheInput)
            outputDir.listFiles()?.forEach { it.delete() }
            outputDir.delete()
        }
    }

    private suspend fun markRunning(id: String) {
        dao.updateProgress(id, JobStatus.RUNNING.name, 0f, 0L, 0f, -1)
    }

    private suspend fun updateProgress(id: String, progress: Float, bytes: Long) {
        dao.updateProgress(id, JobStatus.RUNNING.name, progress, bytes, 0f, -1)
        setForegroundAsyncSafe(null, (progress * 100).toInt())
    }

    private suspend fun success(id: String, outputPath: String, duration: Float) {
        dao.markSuccess(id, JobStatus.SUCCESS.name, outputPath, duration, System.currentTimeMillis())
    }

    private suspend fun fail(id: String, reason: FailureReason, detail: String) {
        dao.markFailure(id, JobStatus.FAILED.name, reason.name, detail, System.currentTimeMillis())
    }

    private suspend fun setForegroundAsyncSafe(name: String?, percent: Int) {
        runCatching { setForeground(makeForegroundInfo(name ?: "Conversión", percent)) }
    }

    private fun makeForegroundInfo(displayName: String, percent: Int): ForegroundInfo {
        val mgr = NotificationManager.IMPORTANCE_LOW
        val tapIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, NSZConverterApp.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.notif_title_running, 1, 1))
            .setContentText(displayName.take(60))
            .setProgress(100, percent.coerceIn(0, 100), false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(tapIntent)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            @Suppress("DEPRECATION")
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_JOB_ID = "job_id"
        const val NOTIFICATION_ID = 1042
    }
}
