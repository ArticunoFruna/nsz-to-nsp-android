package com.nszconverter.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nszconverter.R
import com.nszconverter.domain.model.JobStatus
import com.nszconverter.ui.components.TerminalLogView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobDetailScreen(
    jobId: String,
    onBack: () -> Unit,
    viewModel: JobDetailViewModel = hiltViewModel(),
) {
    val job by viewModel.job.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(job?.displayName ?: "Job") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { inner ->
        Column(modifier = Modifier.padding(inner).fillMaxSize().padding(16.dp)) {
            val logLines = remember(job) { viewModel.buildLogLines(job) }

            TerminalLogView(
                lines = logLines,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (job?.status) {
                    JobStatus.RUNNING, JobStatus.QUEUED -> {
                        Button(
                            onClick = { viewModel.cancel() },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Outlined.Cancel, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.detail_cancel))
                        }
                    }
                    JobStatus.SUCCESS -> {
                        OutlinedButton(
                            onClick = {
                                job?.outputPath?.let { share(context, Uri.parse(it)) }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Outlined.Share, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.detail_share))
                        }
                    }
                    else -> Unit
                }
            }
        }
    }
}

private fun share(context: android.content.Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/octet-stream"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, null)) }
}
