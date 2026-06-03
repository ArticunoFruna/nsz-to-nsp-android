package com.nszconverter.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nszconverter.data.repository.ConversionRepository
import com.nszconverter.domain.model.ConversionJob
import com.nszconverter.domain.model.JobStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class JobDetailViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repo: ConversionRepository,
) : ViewModel() {

    val jobId: String = handle["id"] ?: ""
    val job = repo.observeJob(jobId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun cancel() = viewModelScope.launch { repo.cancelJob(jobId) }
    fun delete() = viewModelScope.launch { repo.deleteJob(jobId) }

    fun buildLogLines(j: ConversionJob?): List<String> {
        if (j == null) return emptyList()
        val lines = mutableListOf<String>()
        lines += "▶ Job ${j.id.take(8)}"
        lines += "  archivo: ${j.displayName}"
        lines += "  tamaño:  ${j.sizeBytes} bytes"
        when (j.status) {
            JobStatus.QUEUED -> lines += "  estado: en cola"
            JobStatus.RUNNING -> lines += "  progreso: ${(j.progress * 100).toInt()}%"
            JobStatus.SUCCESS -> {
                lines += "✓ completado en ${j.durationSeconds}s"
                j.outputPath?.let { lines += "  destino: $it" }
            }
            JobStatus.FAILED -> {
                lines += "✗ falló: ${j.failureReason?.name}"
                j.failureDetail?.split("\n")?.forEach { line ->
                    if (line.isNotBlank()) lines += "  $line"
                }
            }
            JobStatus.CANCELLED -> lines += "⚠ cancelado"
        }
        return lines
    }
}
