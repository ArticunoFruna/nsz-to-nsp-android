package com.nszconverter.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nszconverter.R
import com.nszconverter.domain.model.JobStatus
import com.nszconverter.ui.components.JobCard
import com.nszconverter.ui.components.KeysStatusBadge
import com.nszconverter.ui.components.StorageWarningBanner
import com.nszconverter.util.humanBytes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    padding: PaddingValues,
    onJobClicked: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val pickNszLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.onFilesPicked(uris)
    }
    val pickOutputLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.onOutputPicked(uri)
    }

    Scaffold(
        modifier = Modifier.padding(padding),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    KeysStatusBadge(
                        status = state.keysStatus,
                        onClick = { /* navega a settings, manejado por bottom bar */ },
                        modifier = Modifier.padding(end = 12.dp),
                    )
                },
            )
        },
        floatingActionButton = {
            if (state.pendingPick.isNotEmpty() && state.outputDirUri != null) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.startConversions() },
                    icon = { Icon(Icons.Outlined.PlayArrow, null) },
                    text = { Text(stringResource(R.string.home_convert_all) + " (${state.pendingPick.size})") },
                )
            } else {
                FloatingActionButton(onClick = { pickNszLauncher.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.home_fab_add))
                }
            }
        },
    ) { inner ->
        Column(modifier = Modifier.padding(inner).fillMaxSize()) {

            if (state.pendingPick.isNotEmpty()) {
                PendingSection(
                    count = state.pendingPick.size,
                    requiredBytes = state.requiredBytes,
                    freeBytes = state.freeBytes,
                    outputUri = state.outputDirUri,
                    onPickOutput = { pickOutputLauncher.launch(null) },
                )
                if (state.outputDirUri != null && state.requiredBytes > state.freeBytes && state.freeBytes >= 0) {
                    StorageWarningBanner(
                        message = stringResource(R.string.output_low_space_warning),
                        onChangeFolder = { pickOutputLauncher.launch(null) },
                    )
                }
            }

            if (state.jobs.isEmpty() && state.pendingPick.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(modifier = Modifier.weight(1f, fill = true)) {
                    items(state.jobs, key = { it.id }) { job ->
                        JobCard(
                            job = job,
                            onClick = { onJobClicked(job.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingSection(
    count: Int,
    requiredBytes: Long,
    freeBytes: Long,
    outputUri: String?,
    onPickOutput: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text("$count archivo(s) seleccionado(s)", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "${stringResource(R.string.output_estimated_size, requiredBytes.humanBytes())}  ·  " +
                stringResource(R.string.output_free_space, freeBytes.coerceAtLeast(0).humanBytes()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        if (outputUri == null) {
            Button(onClick = onPickOutput) {
                Text(stringResource(R.string.output_pick_folder))
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.Inbox, null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.home_empty),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.home_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
