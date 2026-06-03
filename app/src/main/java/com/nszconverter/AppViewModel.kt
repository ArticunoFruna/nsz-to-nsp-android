package com.nszconverter

import androidx.lifecycle.ViewModel
import com.nszconverter.data.prefs.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    prefsRepo: PreferencesRepository,
) : ViewModel() {
    val preferences = prefsRepo.preferences
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
}
