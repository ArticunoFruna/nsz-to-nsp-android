package com.nszconverter.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nszconverter.BuildConfig
import com.nszconverter.R
import com.nszconverter.domain.model.KeysStatus
import com.nszconverter.domain.model.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    padding: PaddingValues,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val pickKeys = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onKeysSelected(uri)
    }
    val pickOutput = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.onOutputSelected(uri)
    }

    Scaffold(
        modifier = Modifier.padding(padding),
        topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // -------- Keys
            Section(stringResource(R.string.settings_section_keys)) {
                val keysLabel = when (val k = state.keysStatus) {
                    is KeysStatus.Valid -> stringResource(R.string.settings_keys_status_ok, k.keyCount)
                    is KeysStatus.Invalid -> stringResource(R.string.settings_keys_status_invalid)
                    KeysStatus.NotConfigured -> stringResource(R.string.settings_keys_status_missing)
                }
                Text(keysLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { pickKeys.launch(arrayOf("*/*")) }) {
                    Text(stringResource(R.string.settings_keys_change))
                }
            }

            Divider()

            // -------- Output
            Section(stringResource(R.string.settings_section_output)) {
                val label = state.prefs.defaultOutputUri ?: stringResource(R.string.settings_output_folder_none)
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { pickOutput.launch(null) }) {
                    Text(stringResource(R.string.settings_output_folder))
                }
                ToggleRow(
                    label = stringResource(R.string.settings_delete_source),
                    checked = state.prefs.deleteSourceOnSuccess,
                    onCheckedChange = viewModel::setDeleteSource,
                )
                ToggleRow(
                    label = stringResource(R.string.settings_verify_integrity),
                    checked = state.prefs.verifyIntegrity,
                    onCheckedChange = viewModel::setVerify,
                )
            }

            Divider()

            // -------- Appearance
            Section(stringResource(R.string.settings_section_appearance)) {
                Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeChip(state.prefs.themeMode, ThemeMode.SYSTEM, stringResource(R.string.settings_theme_system), viewModel::setTheme)
                    ThemeChip(state.prefs.themeMode, ThemeMode.LIGHT, stringResource(R.string.settings_theme_light), viewModel::setTheme)
                    ThemeChip(state.prefs.themeMode, ThemeMode.DARK, stringResource(R.string.settings_theme_dark), viewModel::setTheme)
                }
            }

            Divider()

            // -------- Advanced
            Section(stringResource(R.string.settings_section_advanced)) {
                ToggleRow(
                    label = stringResource(R.string.settings_keep_screen_on),
                    checked = state.prefs.keepScreenOn,
                    onCheckedChange = viewModel::setKeepScreenOn,
                )
                ToggleRow(
                    label = stringResource(R.string.settings_notifications),
                    checked = state.prefs.notificationsEnabled,
                    onCheckedChange = viewModel::setNotifications,
                )
            }

            Divider()

            // -------- About
            Section(stringResource(R.string.settings_section_about)) {
                Text(stringResource(R.string.settings_app_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text("nsz: ${state.nszVersion}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.settings_author), style = MaterialTheme.typography.bodyMedium)
                LinkText(
                    text = stringResource(R.string.settings_github_author),
                    url = "https://github.com/ArticunoFruna",
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.settings_credits), style = MaterialTheme.typography.bodyMedium)
                LinkText(
                    text = stringResource(R.string.settings_github_nsz),
                    url = "https://github.com/nicoboss/nsz",
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.settings_legal_full), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ThemeChip(current: ThemeMode, mode: ThemeMode, label: String, onSelect: (ThemeMode) -> Unit) {
    FilterChip(
        selected = current == mode,
        onClick = { onSelect(mode) },
        label = { Text(label) },
    )
}

@Composable
private fun LinkText(text: String, url: String) {
    val uriHandler = LocalUriHandler.current
    Text(
        text = text,
        modifier = Modifier
            .padding(vertical = 2.dp)
            .clickable { runCatching { uriHandler.openUri(url) } },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}
