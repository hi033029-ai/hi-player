package com.example.ui.components

import com.example.ui.theme.hiContainerBorder

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.player.GestureIndicatorColors
import com.example.ui.theme.HiPrimaryCyan
import kotlinx.coroutines.launch

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
    topExclusionDp: androidx.compose.ui.unit.Dp = 64.dp
) {
    val coroutineScope = rememberCoroutineScope()
    var doubleTapSide by remember { mutableStateOf<String?>(null) } // "left" or "right"
    val doubleTapAnim = remember { Animatable(0f) }
    val currentOnSingleTap by rememberUpdatedState(onSingleTap)
    val currentOnDoubleTapLeft by rememberUpdatedState(onDoubleTapLeft)
    val currentOnDoubleTapCenter by rememberUpdatedState(onDoubleTapCenter)
    val currentOnDoubleTapRight by rememberUpdatedState(onDoubleTapRight)
    val currentOnBrightnessDelta by rememberUpdatedState(onBrightnessDelta)
    val currentOnVolumeDelta by rememberUpdatedState(onVolumeDelta)
    val currentOnScrubStart by rememberUpdatedState(onScrubStart)
    val currentOnScrubMove by rememberUpdatedState(onScrubMove)
    val currentOnScrubEnd by rememberUpdatedState(onScrubEnd)
    val currentOnPinchZoom by rememberUpdatedState(onPinchZoom)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val currentOnLongPressEnd by rememberUpdatedState(onLongPressEnd)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(isLocked) {
                val topExclusionPx = topExclusionDp.toPx()
                var longPressStarted = false
                detectTapGestures(
                    onPress = {
                        try {
                            tryAwaitRelease()
                        } finally {
                            if (longPressStarted) {
                                longPressStarted = false
                                currentOnLongPressEnd()
                            }
                        }
                    },
                    onTap = { offset ->
                        if (offset.y >= topExclusionPx) currentOnSingleTap()
                    },
                    onDoubleTap = { offset ->
                        if (isLocked || offset.y < topExclusionPx) return@detectTapGestures
                        val isLeft = offset.x < size.width / 3
                        val isRight = offset.x > size.width * 2 / 3
                        if (isLeft) {
                            currentOnDoubleTapLeft()
                            doubleTapSide = "left"
                        } else if (isRight) {
                            currentOnDoubleTapRight()
                            doubleTapSide = "right"
                        } else {
                            currentOnDoubleTapCenter()
                            doubleTapSide = "center"
                        }
                        coroutineScope.launch {
                            doubleTapAnim.snapTo(0.8f)
                            doubleTapAnim.animateTo(1.3f, tween(350))
                            doubleTapSide = null
                        }
                    },
                    onLongPress = { offset ->
                        if (!isLocked && offset.y >= topExclusionPx) {
                            longPressStarted = true
                            currentOnLongPress(offset.x, size.width.toFloat())
                        }
                    },
                )
            }
            .pointerInput(isLocked, topExclusionDp) {
                val topExclusionPx = topExclusionDp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var horizontal = false
                    var vertical = false
                    var pinched = false
                    var previous = down.position
                    var totalX = 0f
                    var totalY = 0f
                    val left = down.position.x < size.width / 2f
                    val suppressed = isLocked || down.position.y < topExclusionPx
                    try {
                        var anyPressed: Boolean
                        do {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (!suppressed && pressed.size >= 2) {
                                if (horizontal) { currentOnScrubEnd(); horizontal = false }
                                vertical = false
                                pinched = true
                                val zoom = event.calculateZoom()
                                if (zoom.isFinite() && zoom != 1f) currentOnPinchZoom(zoom)
                                event.changes.forEach { it.consume() }
                            } else if (!suppressed && !pinched && pressed.size == 1) {
                                val change = pressed.first()
                                val delta = change.position - previous
                                previous = change.position
                                if (!change.isConsumed) {
                                    totalX += delta.x
                                    totalY += delta.y
                                    if (!horizontal && !vertical && maxOf(kotlin.math.abs(totalX), kotlin.math.abs(totalY)) > viewConfiguration.touchSlop) {
                                        if (kotlin.math.abs(totalX) > kotlin.math.abs(totalY)) {
                                            horizontal = true
                                            totalX = 0f
                                            currentOnScrubStart()
                                        } else {
                                            vertical = true
                                        }
                                    }
                                    if (horizontal) {
                                        currentOnScrubMove((totalX / size.width.coerceAtLeast(1) * 120_000f).toLong())
                                        change.consume()
                                    } else if (vertical) {
                                        val amount = -delta.y / (size.height.coerceAtLeast(1) * .7f)
                                        if (left) currentOnBrightnessDelta(amount) else currentOnVolumeDelta(amount)
                                        change.consume()
                                    }
                                }
                            }
                            anyPressed = event.changes.any { it.pressed }
                        } while (anyPressed)
                    } finally {
                        if (horizontal) currentOnScrubEnd()
                    }
                }
            }
    ) {
        // Double-tap visual feedback animation
        if (doubleTapSide != null) {
            Box(
                modifier = Modifier
                    .size(if (doubleTapSide == "center") 112.dp else 96.dp)
                    .align(
                        when (doubleTapSide) {
                            "left" -> Alignment.CenterStart
                            "right" -> Alignment.CenterEnd
                            else -> Alignment.Center
                        }
                    )
                    .background(Color(0xCC0A0E18), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.scale(doubleTapAnim.value),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (doubleTapSide) {
                            "left" -> Icons.Default.FastRewind
                            "right" -> Icons.Default.FastForward
                            else -> if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow
                        },
                        contentDescription = null,
                        tint = HiPrimaryCyan,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }

        // Brightness HUD (Center Left)
        AnimatedVisibility(
            visible = brightnessLevel != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 32.dp)
        ) {
            brightnessLevel?.let { level ->
                val percent = (level * 100).toInt()
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp)).hiContainerBorder(RoundedCornerShape(16.dp))
                        .background(Color(0xCC111624))
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.BrightnessHigh,
                            contentDescription = "Brightness",
                            tint = HiPrimaryCyan,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .width(8.dp)
                                .height(100.dp)
                                .clip(RoundedCornerShape(4.dp)).hiContainerBorder(RoundedCornerShape(4.dp))
                                .background(Color(0x44FFFFFF))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(level.coerceIn(0f, 1f))
                                    .align(Alignment.BottomCenter)
                                    .background(HiPrimaryCyan)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "$percent%",
                            color = Color.White,
                            fontSize = 12.sp,
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
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 32.dp)
        ) {
            volumeLevel?.let { level ->
                val isBoosted = level > 1.0f
                val indicatorColor = Color(GestureIndicatorColors.volume(level))
                val percent = (level * 100).toInt()
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp)).hiContainerBorder(RoundedCornerShape(16.dp))
                        .background(Color(0xCC111624))
                        .padding(horizontal = 16.dp, vertical = 20.dp),
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
                            tint = indicatorColor,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .width(8.dp)
                                .height(100.dp)
                                .clip(RoundedCornerShape(4.dp)).hiContainerBorder(RoundedCornerShape(4.dp))
                                .background(Color(0x44FFFFFF))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight((level / 2.0f).coerceIn(0f, 1f))
                                    .align(Alignment.BottomCenter)
                                    .background(indicatorColor)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isBoosted) "$percent% Boost" else "$percent%",
                            color = indicatorColor,
                            fontSize = 12.sp,
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
                            color = HiPrimaryCyan,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
