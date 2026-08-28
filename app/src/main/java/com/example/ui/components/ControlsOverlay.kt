package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarHalf
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AspectRatioMode
import com.example.model.VideoItem
import com.example.ui.theme.HiAccentAmber
import com.example.ui.theme.HiPrimaryCyan
import com.example.util.MediaRating
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ControlsOverlay(
    isVisible: Boolean,
    isLocked: Boolean,
    isPlaying: Boolean,
    isBuffering: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long,
    video: VideoItem?,
    aspectRatioMode: AspectRatioMode,
    playbackSpeed: Float,
    isBgPlayActive: Boolean,
    abRepeatA: Long?,
    abRepeatB: Long?,
    videoScale: Float = 1.0f,
    rating: MediaRating? = null,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onRewind10: () -> Unit,
    onFastForward10: () -> Unit,
    onBack: () -> Unit,
    onToggleLock: () -> Unit,
    onRotateScreen: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onEnterPip: () -> Unit,
    onToggleBgPlay: () -> Unit,
    onOpenFile: () -> Unit,
    isHdrEnhanceActive: Boolean = false,
    onToggleHdrEnhance: () -> Unit = {},
    onOpenPlaylist: () -> Unit,
    onToggleSubtitles: () -> Unit,
    onOpenAudioSettings: () -> Unit,
    onOpenVideoSettings: () -> Unit,
    onOpenSubtitleSettings: () -> Unit,
    onOpenMoreOptions: () -> Unit,
    onOpenTelemetry: () -> Unit,
    onCycleAspectRatio: () -> Unit,
    onSetAbRepeat: () -> Unit,
    onCycleSpeed: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (isLocked) {
            // Floating Unlock Button when locked
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 24.dp)
            ) {
                IconButton(
                    onClick = onToggleLock,
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color(0xCC0E1424), CircleShape)
                        .testTag("unlock_screen_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Unlock Controls",
                        tint = HiPrimaryCyan,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            return@Box
        }

        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xCC000000),
                                Color(0x22000000),
                                Color(0x22000000),
                                Color(0xEE000000)
                            )
                        )
                    )
            ) {
                // 1. TOP BAR: Back (left), Filename (center), Fullscreen Toggle (right)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("player_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = video?.title ?: "Hi Player",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (video?.isHdr == true || video?.resolutionBadge == com.example.model.VideoResolutionBadge.UHD_4K) {
                                Box(
                                    modifier = Modifier
                                        .background(HiAccentAmber, RoundedCornerShape(3.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "4K UHD REMUX",
                                        color = Color.Black,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                            Text(
                                text = video?.codec ?: "HEVC HW",
                                color = HiPrimaryCyan,
                                fontSize = 11.sp
                            )
                            if (rating != null) {
                                RatingStarsBadge(rating = rating)
                            }
                        }
                    }

                    // Background Play Toggle
                    IconButton(
                        onClick = onToggleBgPlay,
                        modifier = Modifier.testTag("bg_play_toggle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Headphones,
                            contentDescription = "Background Play",
                            tint = if (isBgPlayActive) HiPrimaryCyan else Color.White.copy(alpha = 0.6f)
                        )
                    }

                    // Picture-in-Picture Button
                    IconButton(
                        onClick = onEnterPip,
                        modifier = Modifier.testTag("pip_mode_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureInPictureAlt,
                            contentDescription = "Picture in Picture",
                            tint = Color.White
                        )
                    }

                    // Fullscreen / Cycle Aspect Ratio
                    IconButton(
                        onClick = onCycleAspectRatio,
                        modifier = Modifier.testTag("fullscreen_toggle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Fullscreen",
                            tint = Color.White
                        )
                    }
                }

                // 2. LEFT EDGE CONTROLS: Lock button & Rotate button
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(
                        onClick = onToggleLock,
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0x88000000), CircleShape)
                            .testTag("left_edge_lock_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = "Lock Controls",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(
                        onClick = onRotateScreen,
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0x88000000), CircleShape)
                            .testTag("left_edge_rotate_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ScreenRotation,
                            contentDescription = "Rotate Screen",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // 3. RIGHT EDGE CONTROLS: Zoom In, Live "1.0x" Label, Zoom Out
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(
                        onClick = onZoomIn,
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0x88000000), CircleShape)
                            .testTag("zoom_in_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Zoom In",
                            tint = HiPrimaryCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(Color(0xCC111827), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = String.format("%.1fx", videoScale),
                            color = HiPrimaryCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = onZoomOut,
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0x88000000), CircleShape)
                            .testTag("zoom_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Zoom Out",
                            tint = HiPrimaryCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // 4. CENTER CONTROLS (Rewind 10s, Play/Pause/Buffer, Forward 10s)
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(36.dp)
                ) {
                    IconButton(
                        onClick = onRewind10,
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color(0x66000000), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "Rewind 10s",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .background(
                                Brush.radialGradient(listOf(HiPrimaryCyan.copy(alpha = 0.3f), Color.Transparent)),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isBuffering) {
                            CircularProgressIndicator(
                                color = HiPrimaryCyan,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(60.dp)
                            )
                        } else {
                            IconButton(
                                onClick = onTogglePlayPause,
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(Color(0xEE111624), CircleShape)
                                    .testTag("play_pause_button")
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = HiPrimaryCyan,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onFastForward10,
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color(0x66000000), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "Fast Forward 10s",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // 6. BOTTOM CONTROLS & SEEK BAR
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Timebar and Timestamps
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatDuration(currentPositionMs),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        HiVideoSeekBar(
                            currentPositionMs = currentPositionMs,
                            durationMs = durationMs,
                            bufferedPositionMs = bufferedPositionMs,
                            onSeekTo = onSeekTo,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("player_seekbar")
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = formatDuration(durationMs),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Secondary Control Row: open-file, playlist, back-10s, play/pause, forward-10s, audio-track, subtitle (CC), "⋮" options
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Open File Icon
                        PlayerToolbarButton(
                            icon = Icons.Default.FolderOpen,
                            label = "Open",
                            onClick = onOpenFile,
                            testTag = "open_file_button"
                        )

                        // Playlist Icon
                        PlayerToolbarButton(
                            icon = Icons.Default.PlaylistPlay,
                            label = "Playlist",
                            onClick = onOpenPlaylist,
                            testTag = "playlist_button"
                        )

                        // Back 10s
                        PlayerToolbarButton(
                            icon = Icons.Default.Replay10,
                            label = "-10s",
                            onClick = onRewind10,
                            testTag = "bottom_rewind_button"
                        )

                        // Forward 10s
                        PlayerToolbarButton(
                            icon = Icons.Default.Forward10,
                            label = "+10s",
                            onClick = onFastForward10,
                            testTag = "bottom_ff_button"
                        )

                        // Audio Track Selector
                        PlayerToolbarButton(
                            icon = Icons.Default.Audiotrack,
                            label = "Audio",
                            onClick = onOpenAudioSettings,
                            testTag = "audio_tracks_button"
                        )

                        // Subtitle (CC) Icon: Single tap toggles, long press opens panel
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .combinedClickable(
                                    onClick = onToggleSubtitles,
                                    onLongClick = onOpenSubtitleSettings
                                )
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                .testTag("subtitles_cc_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ClosedCaption,
                                contentDescription = "Subtitles",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "CC",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // "⋮" More Options
                        PlayerToolbarButton(
                            icon = Icons.Default.MoreVert,
                            label = "More",
                            onClick = onOpenMoreOptions,
                            testTag = "more_options_button"
                        )
                    }
                }

                // HDR / Vivid picture boost - floating toggle, bottom-right
                // corner. Lifts contrast and saturation on the decoded video
                // itself (not just screen brightness) for a punchier, more
                // vibrant picture on request.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 96.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isHdrEnhanceActive) HiPrimaryCyan else Color(0xCC1F2937))
                        .clickable {
                            onToggleHdrEnhance()
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Vivid picture boost",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "HDR",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HiVideoSeekBar(
    currentPositionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    // While the user is actively dragging, track the scrub fraction locally
    // so the thumb tracks the finger smoothly instead of jumping on every
    // upstream position update, and show a live time preview above it.
    var dragFraction by remember { mutableFloatStateOf(-1f) }
    var trackWidthPx by remember { mutableFloatStateOf(0f) }

    val playedFraction = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    val bufferedFraction = if (durationMs > 0) (bufferedPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    val displayFraction = if (dragFraction >= 0f) dragFraction else playedFraction

    Column(modifier = modifier) {
        if (dragFraction >= 0f && durationMs > 0) {
            Text(
                text = formatDuration((dragFraction * durationMs).toLong()),
                color = HiPrimaryCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(durationMs) {
                    if (durationMs <= 0) return@pointerInput
                    detectTapGestures { offset ->
                        val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onSeekTo((fraction * durationMs).toLong())
                    }
                }
                .pointerInput(durationMs) {
                    if (durationMs <= 0) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            if (dragFraction >= 0f) {
                                onSeekTo((dragFraction * durationMs).toLong())
                            }
                            dragFraction = -1f
                        },
                        onDragCancel = { dragFraction = -1f },
                        onHorizontalDrag = { change, _ ->
                            val fraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                            dragFraction = fraction
                        }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .align(Alignment.Center)
            ) {
                trackWidthPx = size.width
                val trackY = size.height / 2f
                val strokeWidth = size.height

                // Base track (untouched portion)
                drawLine(
                    color = Color(0x33FFFFFF),
                    start = Offset(0f, trackY),
                    end = Offset(size.width, trackY),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )

                // Buffered range - previously bufferedPositionMs was passed in
                // but never actually drawn anywhere.
                if (bufferedFraction > 0f) {
                    drawLine(
                        color = Color(0x59FFFFFF),
                        start = Offset(0f, trackY),
                        end = Offset(size.width * bufferedFraction, trackY),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }

                // Played / scrub range
                if (displayFraction > 0f) {
                    drawLine(
                        color = HiPrimaryCyan,
                        start = Offset(0f, trackY),
                        end = Offset(size.width * displayFraction, trackY),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
            }

            // Thumb
            val thumbSize = if (dragFraction >= 0f) 16.dp else 12.dp
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset {
                        val trackWidthDp = with(density) { trackWidthPx.toDp() }
                        androidx.compose.ui.unit.IntOffset(
                            x = with(density) { ((trackWidthDp * displayFraction) - (thumbSize / 2)).toPx().roundToInt() },
                            y = 0
                        )
                    }
                    .size(thumbSize)
                    .background(HiPrimaryCyan, CircleShape)
            )
        }
    }
}

@Composable
private fun PlayerToolbarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = Color.White,
    testTag: String = ""
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

/** Compact 5-star rating badge shown next to the codec tag in the player's title row. */
@Composable
private fun RatingStarsBadge(rating: MediaRating) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(5) { index ->
            val starFill = (rating.starsOutOf5 - index).coerceIn(0f, 1f)
            Icon(
                imageVector = when {
                    starFill >= 0.75f -> Icons.Default.Star
                    starFill >= 0.25f -> Icons.Default.StarHalf
                    else -> Icons.Default.StarOutline
                },
                contentDescription = null,
                tint = HiAccentAmber,
                modifier = Modifier.size(11.dp)
            )
        }
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = String.format("%.1f", rating.voteAverageOutOf10),
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
