package com.nszconverter.domain.model

enum class JobStatus {
    QUEUED,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED;

    val isTerminal: Boolean get() = this == SUCCESS || this == FAILED || this == CANCELLED
    val isActive: Boolean get() = this == RUNNING
    val isPending: Boolean get() = this == QUEUED
}
