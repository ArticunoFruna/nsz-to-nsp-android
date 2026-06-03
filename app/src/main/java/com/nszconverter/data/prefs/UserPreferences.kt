package com.nszconverter.data.prefs

import com.nszconverter.domain.model.ThemeMode

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val onboardingComplete: Boolean = false,
    val keysSourceUri: String? = null,
    val defaultOutputUri: String? = null,
    val deleteSourceOnSuccess: Boolean = false,
    val verifyIntegrity: Boolean = false,
    val keepScreenOn: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val threadCount: Int = 1,
)
