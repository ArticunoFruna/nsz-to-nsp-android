package com.nszconverter.domain.model

data class FileInfo(
    val name: String,
    val sizeBytes: Long,
    val estimatedOutputBytes: Long,
    val exists: Boolean,
)
