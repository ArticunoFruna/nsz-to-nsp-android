package com.nszconverter.ui.onboarding

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nszconverter.data.prefs.PreferencesRepository
import com.nszconverter.domain.model.KeysStatus
import com.nszconverter.domain.usecase.ImportKeysUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val importKeys: ImportKeysUseCase,
    private val prefs: PreferencesRepository,
) : ViewModel() {

    private val _keys = MutableStateFlow<KeysStatus>(KeysStatus.NotConfigured)
    val keys = _keys.asStateFlow()

    private val _importing = MutableStateFlow(false)
    val importing = _importing.asStateFlow()

    fun onKeysSelected(uri: Uri) {
        viewModelScope.launch {
            _importing.value = true
            try {
                _keys.value = importKeys(uri)
            } finally {
                _importing.value = false
            }
        }
    }

    fun finishOnboarding(onDone: () -> Unit) {
        viewModelScope.launch {
            prefs.setOnboardingComplete(true)
            onDone()
        }
    }
}
