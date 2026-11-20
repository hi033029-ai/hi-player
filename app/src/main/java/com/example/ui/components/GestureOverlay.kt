package com.example.ui.components

import com.example.ui.theme.hiContainerBorder

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.HiAccentAmber
import com.example.ui.theme.HiPrimaryCyan
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private enum class TapFeedback { REWIND, FORWARD, PLAY, PAUSE }

private enum class SwipeMode { PENDING, SEEK, BRIGHTNESS, VOLUME, PINCH, HOLD, CANCELLED }

@Composable
fun GestureOverlay(
    isLocked: Boolean,
    onSingleTap: () -> Unit,
    onDoubleTapLeft: () -> Unit,
    onDoubleTapCenter: () -> Unit,
    onDoubleTapRight: () -> Unit,
    isPlaying: Boolean = true,
    onBrightnessDelta: (Float) -> Unit,
    onVolumeDelta: (Float) -> Unit,
    onScrubStart: () -> Unit,
    onScrubMove: (Long) -> Unit,
    onScrubEnd: () -> Unit,
    onScrubCancel: () -> Unit = {},
    onBrightnessGestureStart: () -> Unit = {},
    onBrightnessGestureEnd: () -> Unit = {},
    onVolumeGestureStart: () -> Unit = {},
    onVolumeGestureEnd: () -> Unit = {},
    brightnessLevel: Float?,
    volumeLevel: Float?,
    scrubTimeMs: Long?,
    scrubDeltaMs: Long,
    onPinchZoom: (Float) -> Unit = {},
    onLongPress: (x: Float, width: Float) -> Unit = { _, _ -> },
    onLongPressEnd: () -> Unit = {},
    modifier: Modifier = Modifier,
    // The top control bar (back, bg-play, PiP, aspect-ratio buttons) sits
    // visually above this overlay, but this overlay still spans the full
    // screen for swipe/double-tap purposes. A tap landing in that top strip
    // was being picked up as a single-tap-to-toggle-controls gesture here
    // before it could register as a button click, which made the aspect
    // ratio (and other top-bar) buttons look like they "did nothing" when
    // tapped. Taps/drags starting in this band are now ignored so they fall
    // through to the buttons instead.
    topExclusionDp: androidx.compose.ui.unit.Dp = 64.dp,
    doubleTapOffsetMs: Long? = null,
) {
    val coroutineScope = rememberCoroutineScope()
    var feedback by remember { mutableStateOf<TapFeedback?>(null) }
    var feedbackRevision by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val playingNow by rememberUpdatedState(isPlaying)
    LaunchedEffect(feedbackRevision) {
        if (feedback != null) {
            delay(640)
            feedback = null
        }
    }
    val currentOnSingleTap by rememberUpdatedState(onSingleTap)
    val currentOnDoubleTapLeft by rememberUpdatedState(onDoubleTapLeft)
    val currentOnDoubleTapCenter by rememberUpdatedState(onDoubleTapCenter)
    val currentOnDoubleTapRight by rememberUpdatedState(onDoubleTapRight)
    val currentOnBrightnessDelta by rememberUpdatedState(onBrightnessDelta)
    val currentOnVolumeDelta by rememberUpdatedState(onVolumeDelta)
    val currentOnScrubStart by rememberUpdatedState(onScrubStart)
    val currentOnScrubMove by rememberUpdatedState(onScrubMove)
    val currentOnScrubEnd by rememberUpdatedState(onScrubEnd)
    val currentOnScrubCancel by rememberUpdatedState(onScrubCancel)
    val currentOnBrightnessStart by rememberUpdatedState(onBrightnessGestureStart)
    val currentOnBrightnessEnd by rememberUpdatedState(onBrightnessGestureEnd)
    val currentOnVolumeStart by rememberUpdatedState(onVolumeGestureStart)
    val currentOnVolumeEnd by rememberUpdatedState(onVolumeGestureEnd)
    val currentOnPinchZoom by rememberUpdatedState(onPinchZoom)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val currentOnLongPressEnd by rememberUpdatedState(onLongPressEnd)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(isLocked, topExclusionDp) {
                // One recognizer arbitrates tap, axis-locked swipe, hold and pinch.
                // detectTransformGestures also consumes ONE-finger pan, even when
                // its caller ignores pan. Running it beside drag detection starves
                // seeking/brightness/volume depending on event ordering.
                val topExclusionPx = topExclusionDp.toPx()
                var singleTap: Job? = null
                var lastTapUp = Long.MIN_VALUE
                var lastTapPosition = Offset.Zero
                try {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        if (down.position.y < topExclusionPx) return@awaitEachGesture
                        down.consume()
                        val doubleCandidate = lastTapUp != Long.MIN_VALUE &&
                            down.uptimeMillis - lastTapUp <= viewConfiguration.doubleTapTimeoutMillis &&
                            (down.position - lastTapPosition).getDistance() < 48.dp.toPx()
                        if (doubleCandidate) singleTap?.cancel()
                        var mode = SwipeMode.PENDING
                        var seekStarted = false
                        var brightnessStarted = false
                        var volumeStarted = false
                        var holdStarted = false
                        var previous = down.position
                        var eventTime = down.uptimeMillis
                        try {
                            while (true) {
                                val remainingHold = viewConfiguration.longPressTimeoutMillis - (eventTime - down.uptimeMillis)
                                val event = if (!isLocked && mode == SwipeMode.PENDING) {
                                    withTimeoutOrNull(remainingHold.coerceAtLeast(1L)) { awaitPointerEvent() }
                                } else awaitPointerEvent()
                                if (event == null) {
                                    singleTap?.cancel(); lastTapUp = Long.MIN_VALUE
                                    mode = SwipeMode.HOLD; holdStarted = true
                                    currentOnLongPress(down.position.x, size.width.toFloat())
                                    continue
                                }
                                eventTime = event.changes.maxOf { it.uptimeMillis }
                                val pressed = event.changes.count { it.pressed }
                                if (!isLocked && pressed >= 2) {
                                    if (seekStarted) { currentOnScrubCancel(); seekStarted = false }
                                    if (holdStarted) { currentOnLongPressEnd(); holdStarted = false }
                                    singleTap?.cancel(); lastTapUp = Long.MIN_VALUE
                                    if (brightnessStarted) { currentOnBrightnessEnd(); brightnessStarted = false }
                                    if (volumeStarted) { currentOnVolumeEnd(); volumeStarted = false }
                                    mode = SwipeMode.PINCH
                                    val zoom = event.calculateZoom()
                                    if (zoom.isFinite() && zoom != 1f) currentOnPinchZoom(zoom)
                                    event.changes.forEach { it.consume() }
                                }
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (pressed == 0) {
                                    // Compose synthesizes consumed up events for
                                    // ACTION_CANCEL. They are not a successful
                                    // finger release and must discard the preview.
                                    if (event.changes.any { it.isConsumed }) break
                                    if (mode == SwipeMode.SEEK) {
                                        currentOnScrubEnd(); seekStarted = false
                                    } else if (mode == SwipeMode.PENDING) {
                                        if (doubleCandidate && !isLocked) {
                                            lastTapUp = Long.MIN_VALUE
                                            val action = when {
                                                down.position.x < size.width / 3f -> TapFeedback.REWIND
                                                down.position.x > size.width * 2f / 3f -> TapFeedback.FORWARD
                                                playingNow -> TapFeedback.PAUSE
                                                else -> TapFeedback.PLAY
                                            }
                                            when (action) {
                                                TapFeedback.REWIND -> currentOnDoubleTapLeft()
                                                TapFeedback.FORWARD -> currentOnDoubleTapRight()
                                                else -> currentOnDoubleTapCenter()
                                            }
                                            feedback = action
                                            feedbackRevision++
                                        } else {
                                            val tapTime = eventTime
                                            lastTapUp = tapTime; lastTapPosition = down.position
                                            singleTap?.cancel()
                                            singleTap = coroutineScope.launch {
                                                delay(viewConfiguration.doubleTapTimeoutMillis)
                                                if (lastTapUp == tapTime) currentOnSingleTap()
                                            }
                                        }
                                    }
                                    break
                                }
                                if (mode == SwipeMode.PINCH || mode == SwipeMode.HOLD) {
                                    event.changes.forEach { it.consume() }
                                    continue
                                }
                                if (change == null || !change.pressed || change.isConsumed) {
                                    mode = SwipeMode.CANCELLED
                                    break
                                }
                                val total = change.position - down.position
                                var delta = change.position - previous
                                previous = change.position
                                if (mode == SwipeMode.PENDING && total.getDistance() > viewConfiguration.touchSlop) {
                                    singleTap?.cancel(); lastTapUp = Long.MIN_VALUE
                                    mode = when {
                                        isLocked -> SwipeMode.CANCELLED
                                        abs(total.x) > abs(total.y) -> SwipeMode.SEEK
                                        down.position.x < size.width / 2f -> SwipeMode.BRIGHTNESS
                                        else -> SwipeMode.VOLUME
                                    }
                                    if (mode == SwipeMode.SEEK) { currentOnScrubStart(); seekStarted = true }
                                    if (mode == SwipeMode.BRIGHTNESS) { currentOnBrightnessStart(); brightnessStarted = true }
                                    if (mode == SwipeMode.VOLUME) { currentOnVolumeStart(); volumeStarted = true }
                                    delta = total
                                }
                                when (mode) {
                                    SwipeMode.SEEK -> currentOnScrubMove((total.x / density * 120f).toLong())
                                    SwipeMode.BRIGHTNESS -> currentOnBrightnessDelta(-delta.y / size.height)
                                    SwipeMode.VOLUME -> currentOnVolumeDelta(-delta.y / size.height)
                                    else -> Unit
                                }
                                if (mode != SwipeMode.PENDING) change.consume()
                            }
                        } finally {
                            // Pointer cancellation, rotation and a second finger must
                            // clear a preview, never commit an accidental seek.
                            if (seekStarted) currentOnScrubCancel()
                            if (brightnessStarted) currentOnBrightnessEnd()
                            if (volumeStarted) currentOnVolumeEnd()
                            if (holdStarted) currentOnLongPressEnd()
                        }
                    }
                } finally { singleTap?.cancel() }
            }
    ) {
        feedback?.let { action ->
            val alignment = when (action) {
                TapFeedback.REWIND -> Alignment.CenterStart
                TapFeedback.FORWARD -> Alignment.CenterEnd
                else -> Alignment.Center
            }
            val iconSize = with(LocalDensity.current) { 16.sp.toDp() }.coerceIn(12.dp, 24.dp)
            val label = when (action) {
                TapFeedback.PLAY -> "Play"
                TapFeedback.PAUSE -> "Pause"
                else -> {
                    val delta = doubleTapOffsetMs ?: if (action == TapFeedback.REWIND) -10_000L else 10_000L
                    val sign = if (delta < 0) "−" else if (delta > 0) "+" else ""
                    "${sign}${abs(delta) / 1000}s"
                }
            }
            androidx.compose.animation.AnimatedContent(targetState = action, modifier = Modifier
                .align(alignment).padding(horizontal = 18.dp), label = "doubleTapAction") { shown ->
                Row(
                    Modifier.testTag("double_tap_hud").background(Color(0xDD141922), RoundedCornerShape(50))
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(when (shown) {
                        TapFeedback.REWIND -> Icons.Default.FastRewind
                        TapFeedback.FORWARD -> Icons.Default.FastForward
                        TapFeedback.PLAY -> Icons.Default.PlayArrow
                        TapFeedback.PAUSE -> Icons.Default.Pause
                    }, contentDescription = null, tint = HiPrimaryCyan, modifier = Modifier.size(iconSize))
                    Spacer(Modifier.width(4.dp))
                    Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Brightness HUD (Center Left)
        AnimatedVisibility(
            visible = brightnessLevel != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 18.dp)
        ) {
            brightnessLevel?.let { level ->
                val percent = (level * 100).toInt()
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp)).hiContainerBorder(RoundedCornerShape(18.dp))
                        .background(Color(0xD9111624))
                        .padding(horizontal = 10.dp, vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.BrightnessHigh,
                            contentDescription = "Brightness",
                            tint = HiAccentAmber,
                            modifier = Modifier.size(23.dp)
                        )
                        Spacer(modifier = Modifier.height(7.dp))
                        Box(
                            modifier = Modifier
                                .width(6.dp)
                                .height(68.dp)
                                .clip(RoundedCornerShape(3.dp)).hiContainerBorder(RoundedCornerShape(3.dp))
                                .background(Color(0x44FFFFFF))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(level.coerceIn(0f, 1f))
                                    .align(Alignment.BottomCenter)
                                    .background(HiAccentAmber)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "$percent%",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Volume HUD (Center Right)
        AnimatedVisibility(
            visible = volumeLevel != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 18.dp)
        ) {
            volumeLevel?.let { level ->
                val isBoosted = level > 1.0f
                val percent = (level * 100).toInt()
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp)).hiContainerBorder(RoundedCornerShape(18.dp))
                        .background(Color(0xD9111624))
                        .padding(horizontal = 10.dp, vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = when {
                                level <= 0f -> Icons.Default.VolumeMute
                                level <= 0.5f -> Icons.Default.VolumeDown
                                else -> Icons.Default.VolumeUp
                            },
                            contentDescription = "Volume",
                            tint = if (isBoosted) HiAccentAmber else HiPrimaryCyan,
                            modifier = Modifier.size(23.dp)
                        )
                        Spacer(modifier = Modifier.height(7.dp))
                        Box(
                            modifier = Modifier
                                .width(6.dp)
                                .height(68.dp)
                                .clip(RoundedCornerShape(3.dp)).hiContainerBorder(RoundedCornerShape(3.dp))
                                .background(Color(0x44FFFFFF))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight((level / 2.0f).coerceIn(0f, 1f))
                                    .align(Alignment.BottomCenter)
                                    .background(if (isBoosted) HiAccentAmber else HiPrimaryCyan)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "$percent%",
                            color = if (isBoosted) HiAccentAmber else Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Scrubbing Preview HUD (Center Top)
        AnimatedVisibility(
            visible = scrubTimeMs != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 70.dp)
        ) {
            scrubTimeMs?.let { targetMs ->
                val totalSeconds = targetMs / 1000
                val hours = totalSeconds / 3600
                val minutes = (totalSeconds % 3600) / 60
                val seconds = totalSeconds % 60
                val timeStr = if (hours > 0) {
                    String.format("%d:%02d:%02d", hours, minutes, seconds)
                } else {
                    String.format("%02d:%02d", minutes, seconds)
                }

                val deltaSeconds = scrubDeltaMs / 1000
                val deltaSign = if (deltaSeconds >= 0) "+${deltaSeconds}s" else "${deltaSeconds}s"

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp)).hiContainerBorder(RoundedCornerShape(12.dp))
                        .background(Color(0xE60A0E18))
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = timeStr,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "[$deltaSign]",
                            color = if (deltaSeconds >= 0) HiPrimaryCyan else HiAccentAmber,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
