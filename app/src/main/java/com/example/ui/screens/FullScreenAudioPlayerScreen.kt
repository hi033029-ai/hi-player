package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.model.AudioItem
import com.example.ui.components.AudioTrackThumbnail
import com.example.ui.components.HiPlayerLogoBadge
import com.example.ui.components.WavyAudioWaveform
import com.example.ui.theme.LocalHiPalette

@Composable
fun FullScreenAudioPlayerScreen(
    track: AudioItem,
    isPlaying: Boolean,
    currentPos: Long,
    duration: Long,
    eqPreset: String,
    activeSubtitle: String?,
    lyricsEnabled: Boolean = false,
    lyricsText: String? = null,
    activeLyricLineIndex: Int = -1,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onOpenEq: () -> Unit,
    onOpenSubtitleSearch: () -> Unit,
    onOpenVideoSearch: () -> Unit,
    onLyricsTap: () -> Unit = {},
    onBack: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalHiPalette.current
    val uiMetrics = com.example.ui.theme.LocalHiUiMetrics.current
    val context = LocalContext.current
    val progress = if (duration > 0) (currentPos.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    // Intercept hardware/gesture Back button -> collapse to sheet mode
    BackHandler(onBack = onBack)

    val formatTime: (Long) -> String = { ms ->
        val totalSec = (ms / 1000).toInt()
        val min = totalSec / 60
        val sec = totalSec % 60
        String.format("%02d:%02d", min, sec)
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("full_screen_audio_player"),
        color = palette.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 0.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            // The global Hi Player header is owned by the root shell. Keep the
            // expanded player content directly below it; do not render a second header.
            Spacer(modifier = Modifier.height(8.dp))

            // Large Hero Album Art / PNG Artwork Display Card
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .shadow(12.dp, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .background(palette.surfaceElevated)
                    .border(1.5.dp, palette.primary.copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (lyricsEnabled && !lyricsText.isNullOrBlank()) {
                    val lrcRegex = remember { Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})](.*)""") }
                    val lyricLines = remember(lyricsText) {
                        lyricsText.lines().mapNotNull { line ->
                            lrcRegex.find(line)?.groupValues?.getOrNull(4)?.trim()
                        }
                    }
                    val start = if (activeLyricLineIndex >= 0) {
                        (activeLyricLineIndex - 3).coerceAtLeast(0)
                    } else 0
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        lyricLines.drop(start).take(10).forEachIndexed { offset, line ->
                            val actualIndex = start + offset
                            Text(
                                text = line.ifBlank { "♪" },
                                color = if (actualIndex == activeLyricLineIndex) palette.primary else palette.textSecondary,
                                fontSize = if (actualIndex == activeLyricLineIndex) 18.sp else 14.sp,
                                fontWeight = if (actualIndex == activeLyricLineIndex) FontWeight.Bold else FontWeight.Normal,
                                style = LocalTextStyle.current.copy(textDirection = TextDirection.ContentOrRtl),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            )
                        }
                    }
                } else {
                    AudioTrackThumbnail(
                        track = track,
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(24.dp),
                        contentScale = ContentScale.Crop
                    )
                }

                // Overlay Gradient Badge at top
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f))
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Track Title & Artist Info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = track.title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = track.artist,
                        fontSize = 13.5.sp,
                        color = palette.textSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(palette.primary.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "AUDIO PRO",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Options Row (Subtitles Search, Find Video, EQ Preset)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Subtitles Search Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.primary.copy(alpha = 0.15f))
                        .clickable(onClick = onOpenSubtitleSearch)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Subtitles,
                            contentDescription = "Subtitles",
                            tint = palette.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (lyricsEnabled) "Lyrics Off" else "Lyrics",
                            fontSize = 12.sp,
                            color = palette.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Internet Video Search Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.secondary.copy(alpha = 0.15f))
                        .clickable(onClick = onOpenVideoSearch)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.OndemandVideo,
                            contentDescription = "Find Video",
                            tint = palette.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Find Video",
                            fontSize = 12.sp,
                            color = palette.secondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Equalizer Chip Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.surfaceElevated)
                        .border(1.dp, palette.surfaceBorder, RoundedCornerShape(12.dp))
                        .clickable(onClick = onOpenEq)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Equalizer,
                            contentDescription = "Equalizer",
                            tint = palette.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = eqPreset,
                            fontSize = 12.sp,
                            color = palette.textPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Animated Wavy Audio Soundwave
            WavyAudioWaveform(
                isPlaying = isPlaying,
                waveColor = palette.primary,
                height = 32.dp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Progress Slider Scrub Bar
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = progress,
                    onValueChange = { frac ->
                        onSeek((frac * duration).toLong())
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = palette.primary,
                        activeTrackColor = palette.primary,
                        inactiveTrackColor = palette.surfaceBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(currentPos),
                        fontSize = 12.sp,
                        color = palette.textSecondary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = formatTime(duration),
                        fontSize = 12.sp,
                        color = palette.textSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Playback Controls Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Seek -10s
                IconButton(
                    onClick = { onSeek((currentPos - 10000).coerceAtLeast(0L)) },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay10,
                        contentDescription = "Seek Back 10s",
                        tint = palette.textPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Previous Track
                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous Track",
                        tint = palette.textPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Play / Pause Primary Fab Button
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .shadow(8.dp, CircleShape)
                        .clip(CircleShape)
                        .background(palette.primary)
                        .clickable(onClick = onTogglePlay),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = if (palette.isDark) Color.Black else Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Next Track
                IconButton(
                    onClick = onNext,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next Track",
                        tint = palette.textPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Seek +10s
                IconButton(
                    onClick = { onSeek((currentPos + 10000).coerceAtMost(duration)) },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Forward10,
                        contentDescription = "Seek Forward 10s",
                        tint = palette.textPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
