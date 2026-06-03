package com.nszconverter.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nszconverter.domain.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "nsz_prefs")

@Singleton
class PreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object K {
        val theme = stringPreferencesKey("theme_mode")
        val onboarding = booleanPreferencesKey("onboarding_complete")
        val keysUri = stringPreferencesKey("keys_source_uri")
        val outputUri = stringPreferencesKey("default_output_uri")
        val deleteSource = booleanPreferencesKey("delete_source_on_success")
        val verify = booleanPreferencesKey("verify_integrity")
        val keepScreen = booleanPreferencesKey("keep_screen_on")
        val notif = booleanPreferencesKey("notifications_enabled")
        val threads = intPreferencesKey("thread_count")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { p ->
        UserPreferences(
            themeMode = p[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            onboardingComplete = p[K.onboarding] ?: false,
            keysSourceUri = p[K.keysUri],
            defaultOutputUri = p[K.outputUri],
            deleteSourceOnSuccess = p[K.deleteSource] ?: false,
            verifyIntegrity = p[K.verify] ?: false,
            keepScreenOn = p[K.keepScreen] ?: true,
            notificationsEnabled = p[K.notif] ?: true,
            threadCount = p[K.threads] ?: 1,
        )
    }

    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[K.theme] = mode.name }
    suspend fun setOnboardingComplete(v: Boolean) = context.dataStore.edit { it[K.onboarding] = v }
    suspend fun setKeysSourceUri(uri: String?) = context.dataStore.edit {
        if (uri == null) it.remove(K.keysUri) else it[K.keysUri] = uri
    }
    suspend fun setDefaultOutputUri(uri: String?) = context.dataStore.edit {
        if (uri == null) it.remove(K.outputUri) else it[K.outputUri] = uri
    }
    suspend fun setDeleteSource(v: Boolean) = context.dataStore.edit { it[K.deleteSource] = v }
    suspend fun setVerifyIntegrity(v: Boolean) = context.dataStore.edit { it[K.verify] = v }
    suspend fun setKeepScreenOn(v: Boolean) = context.dataStore.edit { it[K.keepScreen] = v }
    suspend fun setNotificationsEnabled(v: Boolean) = context.dataStore.edit { it[K.notif] = v }
    suspend fun setThreadCount(v: Int) = context.dataStore.edit { it[K.threads] = v.coerceIn(1, 8) }
}
