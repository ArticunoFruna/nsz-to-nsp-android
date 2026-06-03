package com.nszconverter.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.nszconverter.domain.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = Purple40,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    error = ErrorRed40,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
)

private val DarkColors = darkColorScheme(
    primary = Purple80,
    onPrimary = androidx.compose.ui.graphics.Color(0xFF381E72),
    secondary = PurpleGrey80,
    tertiary = Pink80,
    error = ErrorRed80,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
)

@Composable
fun NSZConverterTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val allowDynamic = themeMode == ThemeMode.SYSTEM && supportsDynamic

    val scheme = when {
        allowDynamic && useDark -> dynamicDarkColorScheme(context)
        allowDynamic && !useDark -> dynamicLightColorScheme(context)
        useDark -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = AppTypography,
        content = content,
    )
}
