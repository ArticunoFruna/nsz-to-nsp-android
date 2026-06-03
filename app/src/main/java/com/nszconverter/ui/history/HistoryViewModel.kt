package com.nszconverter.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nszconverter.data.repository.ConversionRepository
import com.nszconverter.domain.model.ConversionJob
import com.nszconverter.domain.model.JobStatus
import com.nszconverter.domain.usecase.ObserveHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class HistoryFilter { ALL, SUCCESS, FAILED }

data class HistoryUiState(
    val items: List<ConversionJob> = emptyList(),
    val filter: HistoryFilter = HistoryFilter.ALL,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    observeHistory: ObserveHistoryUseCase,
    private val repo: ConversionRepository,
) : ViewModel() {

    private val _filter = MutableStateFlow(HistoryFilter.ALL)

    val state = combine(observeHistory(), _filter) { all, filter ->
        val filtered = when (filter) {
            HistoryFilter.ALL -> all
            HistoryFilter.SUCCESS -> all.filter { it.status == JobStatus.SUCCESS }
            HistoryFilter.FAILED -> all.filter { it.status == JobStatus.FAILED || it.status == JobStatus.CANCELLED }
        }
        HistoryUiState(filtered, filter)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryUiState())

    fun setFilter(f: HistoryFilter) { _filter.value = f }
    fun deleteEntry(id: String) = viewModelScope.launch { repo.deleteJob(id) }
    fun clearAll() = viewModelScope.launch { repo.clearHistory() }
}
