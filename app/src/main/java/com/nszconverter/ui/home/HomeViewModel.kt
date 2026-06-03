package com.nszconverter.ui.home

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nszconverter.data.prefs.PreferencesRepository
import com.nszconverter.data.repository.ConversionRepository
import com.nszconverter.domain.model.ConversionJob
import com.nszconverter.domain.model.JobStatus
import com.nszconverter.domain.model.KeysStatus
import com.nszconverter.domain.usecase.ObserveJobsUseCase
import com.nszconverter.domain.usecase.StartConversionUseCase
import com.nszconverter.util.FileManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class HomeUiState(
    val jobs: List<ConversionJob> = emptyList(),
    val keysStatus: KeysStatus = KeysStatus.NotConfigured,
    val outputDirUri: String? = null,
    val pendingPick: List<Uri> = emptyList(),
    val freeBytes: Long = -1,
    val requiredBytes: Long = 0,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val observeJobs: ObserveJobsUseCase,
    private val startConversion: StartConversionUseCase,
    private val repo: ConversionRepository,
    private val prefs: PreferencesRepository,
    private val fileManager: FileManager,
) : ViewModel() {

    private val _pending = MutableStateFlow<List<Uri>>(emptyList())
    private val _keys = MutableStateFlow<KeysStatus>(KeysStatus.NotConfigured)

    val state = combine(
        observeJobs(),
        prefs.preferences.map { it.defaultOutputUri },
        _pending,
        _keys,
    ) { jobs, outputUri, pending, keys ->
        val required = pending.sumOf { fileManager.querySize(it).coerceAtLeast(0L) }
        val free = outputUri?.let { fileManager.availableTreeBytes(Uri.parse(it)) } ?: fileManager.availableInternalBytes()
        HomeUiState(jobs, keys, outputUri, pending, free, (required * 1.35).toLong())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    init {
        refreshKeys()
    }

    fun refreshKeys() {
        viewModelScope.launch {
            _keys.value = repo.currentKeysStatus()
        }
    }

    fun onFilesPicked(uris: List<Uri>) {
        val resolver = context.contentResolver
        uris.forEach { uri ->
            runCatching {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        _pending.value = uris
    }

    fun onOutputPicked(uri: Uri) {
        val resolver: ContentResolver = context.contentResolver
        runCatching {
            resolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        viewModelScope.launch { prefs.setDefaultOutputUri(uri.toString()) }
    }

    fun startConversions() {
        val current = state.value
        val output = current.outputDirUri ?: return
        if (current.pendingPick.isEmpty()) return

        viewModelScope.launch {
            val jobs = current.pendingPick.map { uri ->
                val name = fileManager.queryDisplayName(uri) ?: "archivo.nsz"
                val size = fileManager.querySize(uri).coerceAtLeast(0L)
                ConversionJob(
                    id = UUID.randomUUID().toString(),
                    sourceUri = uri.toString(),
                    outputDirUri = output,
                    displayName = name,
                    sizeBytes = size,
                    estimatedOutputBytes = (size * 1.35).toLong(),
                    status = JobStatus.QUEUED,
                )
            }
            startConversion(jobs)
            _pending.value = emptyList()
        }
    }

    fun cancelJob(id: String) = viewModelScope.launch { repo.cancelJob(id) }
    fun deleteJob(id: String) = viewModelScope.launch { repo.deleteJob(id) }
    fun cancelAll() = viewModelScope.launch { repo.cancelAll() }
}
