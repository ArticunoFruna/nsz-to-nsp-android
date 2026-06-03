package com.nszconverter.domain.model

data class ConversionJob(
    val id: String,
    val sourceUri: String,
    val outputDirUri: String,
    val displayName: String,
    val sizeBytes: Long,
    val estimatedOutputBytes: Long,
    val status: JobStatus,
    val progress: Float = 0f,
    val bytesProcessed: Long = 0L,
    val speedMBs: Float = 0f,
    val etaSeconds: Int = -1,
    val outputPath: String? = null,
    val failureReason: FailureReason? = null,
    val failureDetail: String? = null,
    val durationSeconds: Float = 0f,
    val createdAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
    val workRequestId: String? = null,
)
