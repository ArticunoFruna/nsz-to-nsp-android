package com.nszconverter.data.repository

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.nszconverter.data.local.JobDao
import com.nszconverter.data.local.JobEntity
import com.nszconverter.data.prefs.PreferencesRepository
import com.nszconverter.domain.model.ConversionJob
import com.nszconverter.domain.model.JobStatus
import com.nszconverter.domain.model.KeysStatus
import com.nszconverter.util.FileManager
import com.nszconverter.worker.ConversionWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: JobDao,
    private val python: PythonBridge,
    private val fileManager: FileManager,
    private val prefs: PreferencesRepository,
) {

    fun observeJobs(): Flow<List<ConversionJob>> =
        dao.observeActive().map { list -> list.map { it.toDomain() } }

    fun observeHistory(): Flow<List<ConversionJob>> =
        dao.observeHistory().map { list -> list.map { it.toDomain() } }

    fun observeJob(id: String): Flow<ConversionJob?> =
        dao.observe(id).map { it?.toDomain() }

    suspend fun importKeysFromUri(uri: Uri): KeysStatus {
        val target = fileManager.keysFile()
        target.parentFile?.mkdirs()
        val ok = fileManager.copyUriToFile(uri, target)
        if (!ok) return KeysStatus.Invalid("copy_failed")
        val status = python.validateKeys(target.absolutePath)
        if (status is KeysStatus.Valid) {
            python.setupKeys(target.absolutePath)
            prefs.setKeysSourceUri(uri.toString())
        }
        return status
    }

    suspend fun validateKeys(localPath: String): KeysStatus = python.validateKeys(localPath)

    suspend fun currentKeysStatus(): KeysStatus {
        val f = fileManager.keysFile()
        if (!f.exists() || f.length() == 0L) return KeysStatus.NotConfigured
        return python.validateKeys(f.absolutePath)
    }

    suspend fun enqueueConversions(jobs: List<ConversionJob>) {
        if (jobs.isEmpty()) return
        dao.upsertAll(jobs.map { JobEntity.fromDomain(it.copy(status = JobStatus.QUEUED)) })

        val wm = WorkManager.getInstance(context)
        // Encadenamos los Workers en secuencia para evitar saturar CPU/RAM.
        val requests = jobs.map { job ->
            OneTimeWorkRequestBuilder<ConversionWorker>()
                .setInputData(Data.Builder().putString(ConversionWorker.KEY_JOB_ID, job.id).build())
                .setConstraints(Constraints.Builder().build())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag(WORK_TAG)
                .addTag(jobTag(job.id))
                .build()
        }

        // Persistimos el work request id para poder cancelar
        requests.zip(jobs).forEach { (req, job) ->
            dao.update(JobEntity.fromDomain(job.copy(workRequestId = req.id.toString())))
        }

        var continuation = wm.beginUniqueWork(WORK_UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, requests.first())
        requests.drop(1).forEach { continuation = continuation.then(it) }
        continuation.enqueue()
    }

    suspend fun cancelJob(jobId: String) {
        val entity = dao.get(jobId) ?: return
        entity.workRequestId?.let { wid ->
            runCatching { WorkManager.getInstance(context).cancelWorkById(java.util.UUID.fromString(wid)) }
        }
        dao.markFailure(
            id = jobId,
            status = JobStatus.CANCELLED.name,
            reason = com.nszconverter.domain.model.FailureReason.CANCELLED.name,
            detail = "cancelled_by_user",
            finishedAt = System.currentTimeMillis(),
        )
    }

    suspend fun cancelAll() {
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
    }

    suspend fun deleteJob(jobId: String) = dao.delete(jobId)
    suspend fun clearHistory() = dao.clearHistory()

    companion object {
        const val WORK_TAG = "nsz_conversion"
        const val WORK_UNIQUE_NAME = "nsz_conversion_queue"
        fun jobTag(id: String) = "job_$id"
    }
}
