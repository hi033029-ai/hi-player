package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PendingDownloadFormat
import com.example.model.AspectRatioMode
import com.example.model.VideoItem
import com.example.ui.theme.HiPrimaryCyan
import com.example.ui.theme.LocalHiUiMetrics
import com.example.ui.components.HdrIcon
import com.example.ui.components.PlayPauseButton
import com.example.ui.components.SkipButton
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ControlsOverlay(
    isVisible: Boolean,
    isLocked: Boolean,
    showLockedUnlockControl: Boolean,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long,
    video: VideoItem?,
    aspectRatioMode: AspectRatioMode,
    playbackSpeed: Float,
    isBgPlayActive: Boolean,
    videoScale: Float = 1.0f,
    isHdrSwitching: Boolean = false,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onRewind10: () -> Unit,
    onFastForward10: () -> Unit,
    onPrevious: () -> Unit = {},
    onNext: () -> Unit = {},
    onBack: () -> Unit,
    onToggleLock: () -> Unit,
    onRotateScreen: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onEnterPip: () -> Unit,
    onToggleBgPlay: () -> Unit,
    isMuted: Boolean = false,
    onToggleMute: () -> Unit = {},
    onOpenFile: () -> Unit,
    isHdrEnhanceActive: Boolean = false,
    pictureModeLabel: String = "Original",
    onToggleHdrEnhance: () -> Unit = {},
    onLongPressHdrEnhance: () -> Unit = {},
    onHdrBounds: (androidx.compose.ui.geometry.Rect) -> Unit = {},
    onToggleSubtitles: () -> Unit,
    onOpenAudioSettings: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenVideoSettings: () -> Unit,
    onOpenTelemetry: () -> Unit,
    onCycleAspectRatio: () -> Unit,
    onLongPressAspectRatio: () -> Unit = {},
    onAspectRatioSelected: (AspectRatioMode) -> Unit = {},
    onCycleSpeed: () -> Unit,
    onBottomControlsHeight: (Int) -> Unit = {},
    onSeekBarTouch: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiMetrics = LocalHiUiMetrics.current
    val referenceAspectLabel = aspectRatioMode.displayName
    Box(modifier = modifier.fillMaxSize()) {
        if (isLocked) {
            // The locked player remains clean. A single tap on the video toggles
            // this compact unlock affordance; no regular controls are exposed.
            AnimatedVisibility(
                visible = showLockedUnlockControl,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 24.dp)
            ) {
                IconButton(
                    onClick = onToggleLock,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("unlock_screen_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Unlock Controls",
                        tint = HiPrimaryCyan,
                        modifier = Modifier.size(20.dp)
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
                                Color(0x99000000),
                                Color(0x14000000),
                                Color(0x0A000000),
                                Color(0xC0000000)
                            )
                        )
                    )
            ) {
                // 1. TOP BAR: Back (left), title + filename (center), Audio/CC/actions (right)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .heightIn(min = uiMetrics.headerHeight)
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
                        val displayTitle = video?.let {
                            PendingDownloadFormat.displayName(it.title, it.path)
                        } ?: "Hi Player"
                        Text(
                            text = displayTitle,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = video?.folderName?.takeIf { it.isNotBlank() } ?: "Video",
                            color = Color.White.copy(alpha = 0.68f),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                    PlayerDeviceStatus()

                }

                // Keep the primary playback actions in one stable, thumb-friendly
                // centre group. They no longer compete with utility controls in the
                // bottom row or shrink when labels and display sizes change.
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                ) {
                    // Five transport buttons fit comfortably in landscape, but on
                    // a portrait phone they form a wide invisible touch barrier
                    // across the exact left/right zones used for brightness and
                    // volume swipes. Keep the familiar three-button transport in
                    // narrow windows and reserve previous/next for wider layouts.
                    val narrowTransport = maxWidth < 600.dp
                    PlayerTransportControls(
                        isPlaying = isPlaying,
                        onTogglePlayPause = onTogglePlayPause,
                        onPrevious = onPrevious,
                        onNext = onNext,
                        onRewind10 = onRewind10,
                        onFastForward10 = onFastForward10,
                        compact = maxHeight < 340.dp || narrowTransport,
                        showAdjacent = !narrowTransport,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                // Bottom controls: timeline first, then one responsive utility row.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .onSizeChanged { onBottomControlsHeight(it.height) }
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Timebar and timestamps
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
                            mediaUri = video?.uri,
                            onTouchChanged = onSeekBarTouch,
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

                    Spacer(modifier = Modifier.height(2.dp))

                    // Five essential actions remain visible on narrow phones. The
                    // remaining utilities scroll horizontally instead of producing
                    // duplicate, clipped or overlapping rows.
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val labelScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
                        val utilityCellWidth = (maxWidth / 5).coerceIn(48.dp * labelScale, 64.dp * labelScale)
                        val narrowUtilities = maxWidth < 600.dp
                        val utilityCount = if (narrowUtilities) 12 else 10
                        val utilityWidth = maxOf(maxWidth, utilityCellWidth * utilityCount)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()).width(utilityWidth),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PlayerToolbarButton(Icons.Default.LockOpen, "Lock", onToggleLock,
                                width = utilityCellWidth, testTag = "bottom_lock_button")
                            PlayerToolbarButton(
                                Icons.Default.AspectRatio, referenceAspectLabel, onCycleAspectRatio,
                                width = utilityCellWidth, onLongClick = onLongPressAspectRatio,
                                testTag = "bottom_aspect_button",
                            )
                            PlayerToolbarButton(Icons.Default.ClosedCaption, "CC", onToggleSubtitles,
                                width = utilityCellWidth, testTag = "bottom_subtitles_cc_button")
                            PlayerToolbarButton(Icons.Default.Audiotrack, "Audio", onOpenAudioSettings,
                                width = utilityCellWidth, testTag = "bottom_audio_tracks_button")
                            Column(
                                modifier = Modifier.width(utilityCellWidth).heightIn(min = 60.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                HdrIcon(
                                    isActive = isHdrEnhanceActive,
                                    isSwitching = isHdrSwitching,
                                    onClick = onToggleHdrEnhance,
                                    onLongClick = onLongPressHdrEnhance,
                                    modifier = Modifier.size(48.dp)
                                        .semantics { stateDescription = if (isHdrSwitching) "Switching" else if (isHdrEnhanceActive) "Enhanced, $pictureModeLabel" else "Original output" }
                                        .onGloballyPositioned { onHdrBounds(it.boundsInWindow()) },
                                )
                                Text(
                                    text = when {
                                        isHdrSwitching -> "HDR Switching"
                                        isHdrEnhanceActive -> "HDR $pictureModeLabel"
                                        else -> "HDR Original"
                                    },
                                    color = if (isHdrEnhanceActive) HiPrimaryCyan else Color.White.copy(alpha = 0.86f),
                                    fontSize = 10.sp,
                                    lineHeight = 11.sp,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (narrowUtilities) {
                                // Previous/next move out of the centre gesture
                                // lanes on phones, but remain available in the
                                // scrollable toolbar instead of disappearing.
                                PlayerToolbarButton(Icons.Default.SkipPrevious, "Previous", onPrevious,
                                    width = utilityCellWidth, testTag = "bottom_previous_file_button")
                                PlayerToolbarButton(Icons.Default.SkipNext, "Next", onNext,
                                    width = utilityCellWidth, testTag = "bottom_next_file_button")
                            }
                            PlayerToolbarButton(Icons.Default.ScreenRotation, "Rotate", onRotateScreen, width = utilityCellWidth)
                            PlayerToolbarButton(Icons.Default.PictureInPictureAlt, "PiP", onEnterPip, width = utilityCellWidth)
                            PlayerToolbarButton(Icons.Default.Speed, String.format("%.1f×", playbackSpeed), onCycleSpeed, width = utilityCellWidth)
                            PlayerToolbarButton(Icons.Default.Headphones, "Audio mode", onToggleBgPlay,
                                if (isBgPlayActive) HiPrimaryCyan else Color.White, width = utilityCellWidth)
                            PlayerToolbarButton(Icons.Default.Tune, "EQ", onOpenEqualizer, width = utilityCellWidth)
                        }
                    }
                }

            }
        }
    }
}

@Composable
internal fun HiVideoSeekBar(
    currentPositionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    mediaUri: android.net.Uri? = null,
    onTouchChanged: (Boolean) -> Unit = {},
) {
    val density = LocalDensity.current
    val currentOnSeekTo by rememberUpdatedState(onSeekTo)
    val currentOnTouchChanged by rememberUpdatedState(onTouchChanged)
    val latestDuration by rememberUpdatedState(durationMs)
    var gestureDuration by remember { mutableLongStateOf(0L) }
    // While the user is actively dragging, track the scrub fraction locally
    // so the thumb tracks the finger smoothly instead of jumping on every
    // upstream position update, and show a live time preview above it.
    var dragFraction by remember { mutableFloatStateOf(-1f) }
    var trackWidthPx by remember { mutableFloatStateOf(0f) }

    val playedFraction = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    val bufferedFraction = if (durationMs > 0) (bufferedPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    val displayFraction = if (dragFraction >= 0f) dragFraction else playedFraction

    Box(modifier = modifier.semantics {
        if (durationMs > 0) {
            progressBarRangeInfo = ProgressBarRangeInfo(displayFraction, 0f..1f)
            setProgress { value ->
                if (!value.isFinite()) return@setProgress false
                currentOnSeekTo((value.coerceIn(0f, 1f) * durationMs).toLong())
                true
            }
        }
    }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .pointerInput(mediaUri) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val durationAtDown = latestDuration
                        if (durationAtDown <= 0 || size.width == 0) return@awaitEachGesture
                        gestureDuration = durationAtDown
                        currentOnTouchChanged(true)
                        val startX = down.position.x
                        var lastX = startX
                        var isHorizontalDrag = false
                        var movedPastSlop = false
                        var finishedNormally = false
                        down.consume()

                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: break
                                if (event.changes.any { it.id != down.id && it.pressed }) {
                                    movedPastSlop = true
                                }
                                lastX = change.position.x
                                if (!change.pressed) {
                                    // ACTION_CANCEL is delivered as a consumed up.
                                    finishedNormally = change.changedToUpIgnoreConsumed() && !change.isConsumed
                                    break
                                }
                                val dx = lastX - startX
                                val dy = change.position.y - down.position.y
                                if (!movedPastSlop && !isHorizontalDrag &&
                                    (kotlin.math.abs(dx) > viewConfiguration.touchSlop ||
                                        kotlin.math.abs(dy) > viewConfiguration.touchSlop)
                                ) {
                                    if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                                        isHorizontalDrag = true
                                    } else {
                                        movedPastSlop = true
                                    }
                                }
                                if (isHorizontalDrag) {
                                    dragFraction = (lastX / size.width.toFloat()).coerceIn(0f, 1f)
                                }
                                change.consume()
                            }
                            if (finishedNormally && !movedPastSlop && size.width > 0) {
                                val seekX = if (isHorizontalDrag) lastX else startX
                                val fraction = (seekX / size.width.toFloat()).coerceIn(0f, 1f)
                                currentOnSeekTo((fraction * durationAtDown).toLong())
                            }
                        } finally {
                            dragFraction = -1f
                            currentOnTouchChanged(false)
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            if (dragFraction >= 0f) {
                Text(
                    text = formatDuration((dragFraction * gestureDuration).toLong()),
                    color = HiPrimaryCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopStart),
                )
            }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayerToolbarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = Color.White,
    width: Dp = 58.dp,
    testTag: String = "",
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .width(width)
            .heightIn(min = 60.dp)
            .testTag(testTag)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(21.dp)
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.86f),
            fontSize = 10.sp,
            lineHeight = 11.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlayerTransportControls(
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRewind10: () -> Unit,
    onFastForward10: () -> Unit,
    compact: Boolean = false,
    showAdjacent: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val sideSize = if (compact) 48.dp else 52.dp
    val playSize = if (compact) 56.dp else 64.dp
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(36.dp))
            .background(Color.Black.copy(alpha = 0.46f))
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 0.dp else 2.dp),
    ) {
        if (showAdjacent) {
            SkipButton(Icons.Default.SkipPrevious, onPrevious, Modifier.testTag("previous_file_button"),
                contentDescription = "Previous video", size = sideSize, iconSize = 23.dp)
        }
        SkipButton(Icons.Default.Replay10, onRewind10, Modifier.testTag("rewind_10_button"),
            contentDescription = "Rewind 10 seconds", size = sideSize, iconSize = 24.dp)
        PlayPauseButton(
            isPlaying,
            onTogglePlayPause,
            tint = HiPrimaryCyan,
            modifier = Modifier.testTag("bottom_play_pause_button"),
            size = playSize,
            iconSize = if (compact) 30.dp else 34.dp,
            containerColor = Color.White.copy(alpha = 0.14f),
        )
        SkipButton(Icons.Default.Forward10, onFastForward10, Modifier.testTag("forward_10_button"),
            contentDescription = "Forward 10 seconds", size = sideSize, iconSize = 24.dp)
        if (showAdjacent) {
            SkipButton(Icons.Default.SkipNext, onNext, Modifier.testTag("next_file_button"),
                contentDescription = "Next video", size = sideSize, iconSize = 23.dp)
        }
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
