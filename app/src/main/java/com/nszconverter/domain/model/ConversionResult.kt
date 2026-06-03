package com.nszconverter.domain.model

sealed class ConversionResult {
    data class Success(
        val outputPath: String,
        val durationSeconds: Float,
        val stdoutTail: String = "",
    ) : ConversionResult()

    data class Failure(
        val reason: FailureReason,
        val detail: String,
        val stderrTail: String = "",
    ) : ConversionResult()

    data object Cancelled : ConversionResult()
}
