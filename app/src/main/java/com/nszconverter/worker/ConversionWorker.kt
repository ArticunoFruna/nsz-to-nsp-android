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
import com.nszconverter.data.prefs.PreferencesRepository
import com.nszconverter.data.repository.PythonBridge
import com.nszconverter.domain.model.FailureReason
import com.nszconverter.domain.model.JobStatus
import com.nszconverter.util.FileManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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

        // ---- 1. Keys
        val keysFile = fileManager.keysFile()
        if (!keysFile.exists() || keysFile.length() == 0L) {
            fail(jobId, FailureReason.INVALID_KEYS, "no_keys_configured")
            return Result.failure()
        }
        python.setupKeys(keysFile.absolutePath)

        // ---- 2. Resolver URIs
        val sourceUri = runCatching { Uri.parse(entity.sourceUri) }.getOrNull()
            ?: run { fail(jobId, FailureReason.FILE_NOT_FOUND, "bad_source_uri"); return Result.failure() }
        val outputTreeUri = runCatching { Uri.parse(entity.outputDirUri) }.getOrNull()
            ?: run { fail(jobId, FailureReason.FILE_NOT_FOUND, "bad_output_uri"); return Result.failure() }

        // Intentamos resolver el source URI a un file path real. Para archivos
        // en /storage/emulated/0/... (Downloads, etc.) esto evita la copia de
        // 15 GB al cache y ahorra mucho tiempo.
        val sourceDirectPath: File? = fileManager.resolveDocumentToPath(sourceUri, requireExists = true)
        // Idem para el destino: si resuelve a path, escribimos ahí directo.
        val outputDirectDir: File? = fileManager.resolveTreeToDir(outputTreeUri)

        // ---- 3. Espacio: input (si va a copia) + output estimado
        val needInputCopy = sourceDirectPath == null
        val estimatedOutput = entity.estimatedOutputBytes.coerceAtLeast(entity.sizeBytes)
        val needCacheBytes = (if (needInputCopy) entity.sizeBytes else 0L) + estimatedOutput + 256L * 1024 * 1024

        val workingRoot = if (outputDirectDir != null) {
            // Destino conocido → escribimos al lado y movemos cero-copia al final.
            outputDirectDir
        } else {
            fileManager.workingOutputDir()
        }
        val availableForWork = fileManager.availableBytes(workingRoot)
        if (availableForWork < needCacheBytes) {
            fail(
                jobId,
                FailureReason.INSUFFICIENT_SPACE_OUTPUT,
                "need=${needCacheBytes / 1_000_000}MB available=${availableForWork / 1_000_000}MB at=${workingRoot.absolutePath}",
            )
            return Result.failure()
        }

        // ---- 4. Determinar el input path para Python
        val cacheInput: File? = if (needInputCopy) {
            File(fileManager.cacheInputDir(), "${entity.id}.nsz")
        } else null

        val pythonInputPath: String = if (sourceDirectPath != null) {
            sourceDirectPath.absolutePath
        } else {
            val ci = cacheInput!!
            val copyOk = fileManager.copyUriToFile(sourceUri, ci)
            if (!copyOk) {
                fail(jobId, FailureReason.FILE_NOT_FOUND, "copy_failed")
                return Result.failure()
            }
            if (ci.length() == 0L) {
                fail(jobId, FailureReason.FILE_EMPTY, "zero_bytes")
                return Result.failure()
            }
            ci.absolutePath
        }

        updateProgress(jobId, if (needInputCopy) 0.10f else 0.02f, entity.sizeBytes)

        // ---- 5. Output dir para Python
        // Si tenemos outputDirectDir, escribimos directo ahí en un subdir tmp
        // del que después renombramos el .nsp. Si no, externalCacheDir.
        val pythonOutputDir = File(workingRoot, ".nsz_tmp_${entity.id}").apply { mkdirs() }

        // ---- 6. Coroutine de progress watcher: vigila los .nsp que crecen
        val progressJob: Job = launchProgressWatcher(jobId, pythonOutputDir, estimatedOutput)

        try {
            // ---- 7. Decompress
            val result = python.decompress(pythonInputPath, pythonOutputDir.absolutePath)

            progressJob.cancelAndJoin()

            if (!result.success) {
                val reason = FailureReason.fromRawError(result.error)
                fail(jobId, reason, "${result.error}\n${result.stderrTail.take(2000)}")
                return Result.failure()
            }

            val outputFile = File(result.outputPath)
            if (!outputFile.exists() || outputFile.length() == 0L) {
                fail(jobId, FailureReason.PYTHON_ERROR, "output_missing")
                return Result.failure()
            }

            updateProgress(jobId, 0.92f, outputFile.length())

            // ---- 8. Mover/copiar al destino SAF
            val outputName = fileManager.sanitizeFileName(
                entity.displayName.replace(Regex("\\.nsz$", RegexOption.IGNORE_CASE), "") + ".nsp"
            )

            val finalUri = fileManager.moveOrCopyToTree(outputFile, outputTreeUri, outputName)
                ?: run {
                    fail(jobId, FailureReason.PERMISSION_DENIED, "write_failed")
                    return Result.failure()
                }

            success(jobId, finalUri.toString(), result.durationSeconds)

            if (prefs.preferences.first().deleteSourceOnSuccess) {
                runCatching {
                    androidx.documentfile.provider.DocumentFile
                        .fromSingleUri(context, sourceUri)?.delete()
                }
            }

            return Result.success()
        } catch (e: Exception) {
            progressJob.cancelAndJoin()
            fail(jobId, FailureReason.UNKNOWN, e.message ?: e.javaClass.simpleName)
            return Result.failure()
        } finally {
            cacheInput?.let { fileManager.cleanupCache(it) }
            fileManager.cleanupDir(pythonOutputDir)
        }
    }

    /**
     * Lanza una corutina que mira cada 2 segundos el tamaño total de los .nsp
     * (parcial o final) en el directorio de output y reporta el progreso real.
     */
    private fun launchProgressWatcher(jobId: String, outputDir: File, estimatedTotal: Long): Job {
        val scope = CoroutineScope(Dispatchers.IO)
        return scope.launch {
            val total = estimatedTotal.coerceAtLeast(1L).toDouble()
            var lastBytes = 0L
            var lastSampleMs = System.currentTimeMillis()
            while (isActive) {
                delay(2000)
                val currentBytes = outputDir.walkTopDown()
                    .filter { it.isFile }
                    .sumOf { it.length() }
                val now = System.currentTimeMillis()
                val dtSec = (now - lastSampleMs) / 1000f
                val speedMBs = if (dtSec > 0) (currentBytes - lastBytes).toFloat() / (1024 * 1024) / dtSec else 0f
                lastBytes = currentBytes
                lastSampleMs = now

                val frac = (currentBytes.toDouble() / total).coerceIn(0.0, 0.9)
                val progress = (0.10f + frac.toFloat() * 0.80f).coerceIn(0f, 0.92f)
                val remaining = (estimatedTotal - currentBytes).coerceAtLeast(0L)
                val etaSec = if (speedMBs > 0.1f) (remaining.toDouble() / (1024 * 1024) / speedMBs).toInt() else -1

                runCatching {
                    dao.updateProgress(jobId, JobStatus.RUNNING.name, progress, currentBytes, speedMBs, etaSec)
                }
                runCatching { setForeground(makeForegroundInfo(null, (progress * 100).toInt())) }
            }
        }
    }

    private suspend fun markRunning(id: String) {
        dao.updateProgress(id, JobStatus.RUNNING.name, 0f, 0L, 0f, -1)
    }

    private suspend fun updateProgress(id: String, progress: Float, bytes: Long) {
        dao.updateProgress(id, JobStatus.RUNNING.name, progress, bytes, 0f, -1)
        runCatching { setForeground(makeForegroundInfo(null, (progress * 100).toInt())) }
    }

    private suspend fun success(id: String, outputPath: String, duration: Float) {
        dao.markSuccess(id, JobStatus.SUCCESS.name, outputPath, duration, System.currentTimeMillis())
    }

    private suspend fun fail(id: String, reason: FailureReason, detail: String) {
        dao.markFailure(id, JobStatus.FAILED.name, reason.name, detail, System.currentTimeMillis())
    }

    private fun makeForegroundInfo(displayName: String?, percent: Int): ForegroundInfo {
        val tapIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, NSZConverterApp.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.notif_title_running, 1, 1))
            .setContentText((displayName ?: "").take(60))
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
