package com.nszconverter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nszconverter.ui.theme.TerminalBackground
import com.nszconverter.ui.theme.TerminalBlue
import com.nszconverter.ui.theme.TerminalGreen
import com.nszconverter.ui.theme.TerminalRed
import com.nszconverter.ui.theme.TerminalText
import com.nszconverter.ui.theme.TerminalYellow

@Composable
fun TerminalLogView(
    lines: List<String>,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyListState()

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) state.animateScrollToItem(lines.lastIndex)
    }

    LazyColumn(
        state = state,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(TerminalBackground)
            .padding(12.dp)
            .fillMaxSize(),
    ) {
        items(lines) { line ->
            val (prefix, color) = parseLine(line)
            Text(
                text = prefix + line.removePrefix("✓ ").removePrefix("✗ ").removePrefix("▶ ").removePrefix("⚠ "),
                color = color,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
        }
    }
}

private fun parseLine(line: String): Pair<String, Color> = when {
    line.startsWith("✓") -> "✓ " to TerminalGreen
    line.startsWith("✗") -> "✗ " to TerminalRed
    line.startsWith("▶") -> "▶ " to TerminalBlue
    line.startsWith("⚠") -> "⚠ " to TerminalYellow
    else -> "" to TerminalText
}
