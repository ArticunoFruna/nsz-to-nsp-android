package com.nszconverter.domain.model

enum class FailureReason {
    INVALID_KEYS,
    KEYS_OUTDATED,
    FILE_NOT_FOUND,
    FILE_CORRUPT,
    FILE_EMPTY,
    INSUFFICIENT_SPACE_CACHE,
    INSUFFICIENT_SPACE_OUTPUT,
    PERMISSION_DENIED,
    PYTHON_ERROR,
    CANCELLED,
    UNKNOWN;

    companion object {
        fun fromRawError(raw: String): FailureReason = when {
            raw.startsWith("input_not_found") -> FILE_NOT_FOUND
            raw.startsWith("input_empty") -> FILE_EMPTY
            raw.startsWith("keys_outdated") -> KEYS_OUTDATED
            raw.startsWith("missing:") -> INVALID_KEYS
            raw.startsWith("permission_denied") -> PERMISSION_DENIED
            raw.startsWith("nsz_import_failed") -> PYTHON_ERROR
            raw.startsWith("nsz_failed") -> PYTHON_ERROR
            raw.startsWith("output_not_found") -> PYTHON_ERROR
            else -> UNKNOWN
        }
    }
}
