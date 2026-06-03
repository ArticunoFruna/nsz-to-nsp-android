package com.nszconverter.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nszconverter.domain.model.ConversionJob
import com.nszconverter.domain.model.FailureReason
import com.nszconverter.domain.model.JobStatus

@Entity(tableName = "jobs")
data class JobEntity(
    @PrimaryKey val id: String,
    val sourceUri: String,
    val outputDirUri: String,
    val displayName: String,
    val sizeBytes: Long,
    val estimatedOutputBytes: Long,
    val status: String,
    val progress: Float,
    val bytesProcessed: Long,
    val speedMBs: Float,
    val etaSeconds: Int,
    val outputPath: String?,
    val failureReason: String?,
    val failureDetail: String?,
    val durationSeconds: Float,
    val createdAt: Long,
    val finishedAt: Long?,
    val workRequestId: String?,
) {
    fun toDomain(): ConversionJob = ConversionJob(
        id = id,
        sourceUri = sourceUri,
        outputDirUri = outputDirUri,
        displayName = displayName,
        sizeBytes = sizeBytes,
        estimatedOutputBytes = estimatedOutputBytes,
        status = runCatching { JobStatus.valueOf(status) }.getOrDefault(JobStatus.QUEUED),
        progress = progress,
        bytesProcessed = bytesProcessed,
        speedMBs = speedMBs,
        etaSeconds = etaSeconds,
        outputPath = outputPath,
        failureReason = failureReason?.let { runCatching { FailureReason.valueOf(it) }.getOrNull() },
        failureDetail = failureDetail,
        durationSeconds = durationSeconds,
        createdAt = createdAt,
        finishedAt = finishedAt,
        workRequestId = workRequestId,
    )

    companion object {
        fun fromDomain(j: ConversionJob) = JobEntity(
            id = j.id,
            sourceUri = j.sourceUri,
            outputDirUri = j.outputDirUri,
            displayName = j.displayName,
            sizeBytes = j.sizeBytes,
            estimatedOutputBytes = j.estimatedOutputBytes,
            status = j.status.name,
            progress = j.progress,
            bytesProcessed = j.bytesProcessed,
            speedMBs = j.speedMBs,
            etaSeconds = j.etaSeconds,
            outputPath = j.outputPath,
            failureReason = j.failureReason?.name,
            failureDetail = j.failureDetail,
            durationSeconds = j.durationSeconds,
            createdAt = j.createdAt,
            finishedAt = j.finishedAt,
            workRequestId = j.workRequestId,
        )
    }
}
