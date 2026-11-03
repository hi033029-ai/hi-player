package com.example.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lyrics.LyricsText
import com.example.model.AudioItem

/**
 * Shows the current timed line immediately over the track artwork.
 */
@Composable
fun FocusedLyricsView(
    track: AudioItem, text: String?, activeIndex: Int, message: String?, loading: Boolean,
    retryable: Boolean, onRetry: () -> Unit, modifier: Modifier = Modifier,
) {
    val parsed = remember(text) { LyricsText.parse(text.orEmpty()) }
    val timed = parsed.timed
    Box(modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).testTag("audio_focused_lyrics")) {
        AudioTrackThumbnail(track, Modifier.fillMaxSize().then(
            if (Build.VERSION.SDK_INT >= 31) Modifier.blur(28.dp) else Modifier),
            shape = RoundedCornerShape(24.dp), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            listOf(Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.62f), Color.Black.copy(alpha = 0.35f)))))
        val textStyle = TextStyle(textDirection = TextDirection.ContentOrRtl, textAlign = TextAlign.Center)
        when {
            timed.isNotEmpty() -> {
                val index = activeIndex.coerceIn(-1, timed.lastIndex)
                val current = timed.getOrNull(index)?.second.orEmpty().ifBlank { "♪" }
                val next = timed.getOrNull(index + 1)?.second?.takeIf { it.isNotBlank() }
                Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(current, color = Color.White, fontSize = 26.sp, lineHeight = 32.sp,
                        fontWeight = FontWeight.Bold, style = textStyle, modifier = Modifier.testTag("audio_lyric_current"))
                    if (next != null) {
                        Spacer(Modifier.height(14.dp))
                        Text(next, color = Color.White.copy(alpha = 0.5f), fontSize = 16.sp, style = textStyle)
                    }
                }
            }
            parsed.plain.isNotEmpty() -> {
                // Without timestamps the current verse is unknown; say so rather than guess.
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("These lyrics aren't time-synced", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    parsed.plain.forEach { Text(it, color = Color.White, fontSize = 16.sp, lineHeight = 24.sp, style = textStyle) }
                }
            }
            else -> Row(Modifier.align(Alignment.Center).padding(20.dp).testTag("audio_lyrics_status"),
                verticalAlignment = Alignment.CenterVertically) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(message ?: "No lyrics available", color = Color.White, fontSize = 14.sp, style = textStyle)
                if (!loading && retryable) IconButton(onClick = onRetry) { Icon(Icons.Default.Refresh, "Retry lyrics", tint = Color.White) }
            }
        }
    }
}
