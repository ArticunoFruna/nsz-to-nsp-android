package com.nszconverter.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import com.nszconverter.domain.model.ConversionJob
import com.nszconverter.domain.model.JobStatus
import com.nszconverter.ui.theme.StatusFailedDark
import com.nszconverter.ui.theme.StatusFailedLight
import com.nszconverter.ui.theme.StatusQueuedDark
import com.nszconverter.ui.theme.StatusQueuedLight
import com.nszconverter.ui.theme.StatusRunning
import com.nszconverter.ui.theme.StatusRunningOnDark
import com.nszconverter.ui.theme.StatusSuccess
import com.nszconverter.ui.theme.StatusSuccessOnDark
import com.nszconverter.util.humanBytes
import com.nszconverter.util.humanSeconds

@Composable
fun JobCard(
    job: ConversionJob,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val statusColor = when (job.status) {
        JobStatus.QUEUED -> if (dark) StatusQueuedDark else StatusQueuedLight
        JobStatus.RUNNING -> if (dark) StatusRunningOnDark else StatusRunning
        JobStatus.SUCCESS -> if (dark) StatusSuccessOnDark else StatusSuccess
        JobStatus.FAILED -> if (dark) StatusFailedDark else StatusFailedLight
        JobStatus.CANCELLED -> if (dark) StatusQueuedDark else StatusQueuedLight
    }

    val animProgress by animateFloatAsState(
        targetValue = job.progress,
        animationSpec = tween(durationMillis = 250),
        label = "progress",
    )

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.InsertDriveFile,
                        contentDescription = null,
                        tint = statusColor,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = job.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = job.statusLine(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                StatusTrailing(job, statusColor)
            }

            if (job.status == JobStatus.RUNNING) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { animProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = statusColor,
                )
            }
        }
    }
}

@Composable
private fun StatusTrailing(job: ConversionJob, color: Color) {
    when (job.status) {
        JobStatus.RUNNING -> {
            CircularProgressIndicator(
                progress = { job.progress.coerceIn(0f, 1f) },
                modifier = Modifier.size(24.dp),
                strokeWidth = 3.dp,
                color = color,
            )
        }
        JobStatus.SUCCESS -> Icon(Icons.Outlined.CheckCircle, null, tint = color)
        JobStatus.FAILED -> Icon(Icons.Outlined.ErrorOutline, null, tint = color)
        JobStatus.CANCELLED -> Icon(Icons.Outlined.Cancel, null, tint = color)
        JobStatus.QUEUED -> Icon(Icons.Outlined.HourglassEmpty, null, tint = color)
    }
}

private fun ConversionJob.statusLine(): String {
    val size = sizeBytes.humanBytes()
    return when (status) {
        JobStatus.QUEUED -> "En cola · $size"
        JobStatus.RUNNING -> {
            val pct = (progress * 100).toInt()
            val eta = if (etaSeconds >= 0) " · ${etaSeconds.humanSeconds()} restantes" else ""
            "$pct%  ·  $size$eta"
        }
        JobStatus.SUCCESS -> "Listo · ${durationSeconds.toInt().humanSeconds()}"
        JobStatus.FAILED -> failureReason?.name ?: "Error"
        JobStatus.CANCELLED -> "Cancelado"
    }
}
