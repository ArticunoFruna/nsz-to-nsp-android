package com.nszconverter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.KeyOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.nszconverter.domain.model.KeysStatus

@Composable
fun KeysStatusBadge(
    status: KeysStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (icon, label, container, content) = when (status) {
        is KeysStatus.Valid -> Quad(
            Icons.Outlined.Key,
            "${status.keyCount} keys",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
        )
        is KeysStatus.Invalid -> Quad(
            Icons.Outlined.KeyOff,
            "Keys inválidas",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )
        KeysStatus.NotConfigured -> Quad(
            Icons.Outlined.KeyOff,
            "Sin keys",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Icon(icon, null, tint = content, modifier = Modifier.padding(end = 4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = content)
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
