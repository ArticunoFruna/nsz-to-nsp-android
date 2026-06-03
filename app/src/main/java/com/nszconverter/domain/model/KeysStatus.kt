package com.nszconverter.domain.model

sealed class KeysStatus {
    data object NotConfigured : KeysStatus()
    data class Valid(val keyCount: Int, val hasTitleKeys: Boolean) : KeysStatus()
    data class Invalid(val rawError: String) : KeysStatus()
}
