package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AudioItem
import com.example.ui.components.AudioLyricsPanel
import com.example.ui.components.AudioTrackThumbnail
import com.example.ui.theme.LocalHiPalette
import com.example.ui.theme.contrastContentColor

/** Metadata stays above the flexible lyrics pane; transport controls never scroll away. */
@Composable
fun FullScreenAudioPlayerScreen(
    track: AudioItem, isPlaying: Boolean, currentPos: Long, duration: Long, eqPreset: String,
    activeSubtitle: String?, lyricsEnabled: Boolean = false, lyricsText: String? = null,
    activeLyricLineIndex: Int = -1, onTogglePlay: () -> Unit, onNext: () -> Unit, onPrevious: () -> Unit,
    onSeek: (Long) -> Unit, onOpenEq: () -> Unit, onOpenSubtitleSearch: () -> Unit,
    onLyricsTap: () -> Unit = {}, onBack: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier,
    lyricsMessage: String? = null, lyricsLoading: Boolean = false, lyricsRetryable: Boolean = false,
    onRetryLyrics: () -> Unit = {},
) {
    val palette = LocalHiPalette.current
    val total = duration.takeIf { it > 0 } ?: track.durationMs.coerceAtLeast(0)
    val position = currentPos.coerceIn(0, total.coerceAtLeast(0))
    val progress = if (total > 0) (position.toFloat() / total).coerceIn(0f, 1f) else 0f
    fun clock(ms: Long): String = "%02d:%02d".format(java.util.Locale.US, ms / 60000, ms / 1000 % 60)
    BackHandler(onBack = onBack)
    Surface(modifier.fillMaxSize().testTag("full_screen_audio_player"), color = palette.background) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Row(Modifier.fillMaxWidth().testTag("audio_song_header"), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.KeyboardArrowDown, "Minimize player", tint = palette.textPrimary)
                }
                Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                    Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis, color = palette.textPrimary,
                        fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = palette.textSecondary, fontSize = 12.sp)
                }
                IconButton(onClick = onCancel, modifier = Modifier.testTag("cancel_audio_player")) {
                    Icon(Icons.Default.Close, "Stop music", tint = palette.textSecondary)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = lyricsEnabled, onClick = onOpenSubtitleSearch,
                    label = { Text(if (lyricsEnabled) "Lyrics on" else "Lyrics") },
                    leadingIcon = { Icon(Icons.Default.Subtitles, null, Modifier.size(16.dp)) },
                    colors = FilterChipDefaults.filterChipColors(labelColor = palette.textPrimary,
                        selectedContainerColor = palette.primary,
                        selectedLabelColor = contrastContentColor(palette.primary, palette.background),
                        selectedLeadingIconColor = contrastContentColor(palette.primary, palette.background)))
                AssistChip(onClick = onOpenEq, label = { Text("Equalizer · $eqPreset", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.Default.Equalizer, null, Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1f), colors = AssistChipDefaults.assistChipColors(labelColor = palette.textPrimary,
                        leadingIconContentColor = palette.textPrimary))
            }
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).padding(vertical = 6.dp).testTag("audio_lyrics_area"),
                contentAlignment = Alignment.Center) {
                if (lyricsEnabled) {
                    AudioLyricsPanel(lyricsText, activeLyricLineIndex, lyricsMessage, lyricsLoading, lyricsRetryable, onRetryLyrics)
                } else {
                    AudioTrackThumbnail(track, modifier = Modifier.size(minOf(maxWidth, maxHeight, 260.dp)),
                        shape = RoundedCornerShape(20.dp))
                }
            }
            Column(Modifier.fillMaxWidth()) {
                Slider(value = progress, onValueChange = { onSeek((it * total).toLong()) }, enabled = total > 0,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Music playback position" },
                    colors = SliderDefaults.colors(thumbColor = palette.primary, activeTrackColor = palette.primary,
                        inactiveTrackColor = palette.surfaceBorder))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(clock(position), color = palette.textSecondary, fontSize = 11.sp)
                    Text(clock(total), color = palette.textSecondary, fontSize = 11.sp)
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onSeek((position - 10000).coerceAtLeast(0)) }) {
                    Icon(Icons.Default.Replay10, "Seek Back 10s", tint = palette.textPrimary)
                }
                IconButton(onClick = onPrevious) { Icon(Icons.Default.SkipPrevious, "Previous Track", tint = palette.textPrimary) }
                Box(Modifier.size(56.dp).clip(CircleShape).background(palette.primary).clickable(onClick = onTogglePlay),
                    contentAlignment = Alignment.Center) {
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (isPlaying) "Pause" else "Play", Modifier.size(32.dp),
                        tint = contrastContentColor(palette.primary, palette.background))
                }
                IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, "Next Track", tint = palette.textPrimary) }
                IconButton(onClick = { onSeek((position + 10000).coerceAtMost(total)) }) {
                    Icon(Icons.Default.Forward10, "Seek Forward 10s", tint = palette.textPrimary)
                }
            }
        }
    }
}
