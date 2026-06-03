package com.nszconverter.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nszconverter.data.prefs.PreferencesRepository
import com.nszconverter.data.prefs.UserPreferences
import com.nszconverter.data.repository.ConversionRepository
import com.nszconverter.domain.model.KeysStatus
import com.nszconverter.domain.model.ThemeMode
import com.nszconverter.domain.usecase.ImportKeysUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val prefs: UserPreferences = UserPreferences(),
    val keysStatus: KeysStatus = KeysStatus.NotConfigured,
    val nszVersion: String = "—",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PreferencesRepository,
    private val repo: ConversionRepository,
    private val importKeys: ImportKeysUseCase,
) : ViewModel() {

    private val _keys = MutableStateFlow<KeysStatus>(KeysStatus.NotConfigured)
    private val _nszVersion = MutableStateFlow("—")

    val state = combine(prefs.preferences, _keys, _nszVersion) { p, k, v ->
        SettingsUiState(p, k, v)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    init {
        viewModelScope.launch {
            _keys.value = repo.currentKeysStatus()
            _nszVersion.value = runCatching {
                val py = com.chaquo.python.Python.getInstance()
                py.getModule("converter").callAttr("nsz_version").toString()
            }.getOrDefault("unknown")
        }
    }

    fun onKeysSelected(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        viewModelScope.launch {
            _keys.value = importKeys(uri)
        }
    }

    fun onOutputSelected(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        viewModelScope.launch { prefs.setDefaultOutputUri(uri.toString()) }
    }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { prefs.setTheme(mode) }
    fun setDeleteSource(v: Boolean) = viewModelScope.launch { prefs.setDeleteSource(v) }
    fun setVerify(v: Boolean) = viewModelScope.launch { prefs.setVerifyIntegrity(v) }
    fun setKeepScreenOn(v: Boolean) = viewModelScope.launch { prefs.setKeepScreenOn(v) }
    fun setNotifications(v: Boolean) = viewModelScope.launch { prefs.setNotificationsEnabled(v) }
    fun setThreadCount(v: Int) = viewModelScope.launch { prefs.setThreadCount(v) }
}
