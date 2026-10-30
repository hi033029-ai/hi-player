package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lyrics.LyricsText
import com.example.ui.theme.LocalHiPalette

@Composable
fun AudioLyricsPanel(
    text: String?, activeIndex: Int, message: String?, loading: Boolean,
    retryable: Boolean, onRetry: () -> Unit, compact: Boolean = false,
) {
    val palette = LocalHiPalette.current
    val parsed = remember(text) { LyricsText.parse(text.orEmpty()) }
    val lines = remember(parsed) { parsed.displayLines }
    if (lines.isEmpty()) {
        Row(Modifier.fillMaxSize().padding(if (compact) 6.dp else 16.dp).testTag("audio_lyrics_status"),
            verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(18.dp), color = palette.primary, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(message ?: "No lyrics available", Modifier.weight(1f), color = palette.textPrimary,
                fontSize = if (compact) 10.sp else 14.sp, maxLines = if (compact) 2 else 5,
                style = TextStyle(textDirection = TextDirection.ContentOrRtl), overflow = TextOverflow.Ellipsis)
            if (!loading && retryable) IconButton(onClick = onRetry, modifier = Modifier.size(if (compact) 32.dp else 48.dp)) {
                Icon(Icons.Default.Refresh, "Retry lyrics", tint = palette.primary)
            }
        }
    } else if (compact) {
        val start = activeIndex.coerceAtLeast(0).coerceAtMost(lines.lastIndex)
        Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp).testTag("audio_lyrics_text")) {
            lines.drop(start).take(2).forEachIndexed { i, line ->
                Text(line.ifBlank { "♪" }, color = if (i == 0) palette.textPrimary else palette.textSecondary,
                    fontSize = if (i == 0) 11.sp else 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(textDirection = TextDirection.ContentOrRtl))
            }
        }
    } else {
        val list = rememberLazyListState()
        LaunchedEffect(activeIndex, text) {
            if (parsed.timed.isNotEmpty() && activeIndex >= 0) list.animateScrollToItem((activeIndex - 2).coerceIn(0, lines.lastIndex))
        }
        LazyColumn(Modifier.fillMaxSize().padding(16.dp).testTag("audio_lyrics_text"), state = list,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(lines) { index, line ->
                val active = parsed.timed.isNotEmpty() && index == activeIndex
                Text(line.ifBlank { "♪" }, color = if (active) palette.primary else palette.textPrimary,
                    fontSize = if (active) 18.sp else 14.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    style = TextStyle(textDirection = TextDirection.ContentOrRtl))
            }
        }
    }
}
