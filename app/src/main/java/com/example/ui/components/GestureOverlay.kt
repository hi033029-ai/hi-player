package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.HiAccentAmber
import com.example.ui.theme.HiPrimaryCyan
import kotlinx.coroutines.launch

@Composable
fun GestureOverlay(
    isLocked: Boolean,
    onSingleTap: () -> Unit,
    onDoubleTapLeft: () -> Unit,
    onDoubleTapRight: () -> Unit,
    onBrightnessDelta: (Float) -> Unit,
    onVolumeDelta: (Float) -> Unit,
    onScrubStart: () -> Unit,
    onScrubMove: (Long) -> Unit,
    onScrubEnd: () -> Unit,
    brightnessLevel: Float?,
    volumeLevel: Float?,
    scrubTimeMs: Long?,
    scrubDeltaMs: Long,
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

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(isLocked) {
                val topExclusionPx = topExclusionDp.toPx()
                if (isLocked) {
                    detectTapGestures(onTap = { offset ->
                        if (offset.y >= topExclusionPx) onSingleTap()
                    })
                } else {
                    detectTapGestures(
                        onTap = { offset ->
                            if (offset.y >= topExclusionPx) onSingleTap()
                        },
                        onDoubleTap = { offset ->
                            if (offset.y < topExclusionPx) return@detectTapGestures
                            val isLeft = offset.x < size.width / 2
                            if (isLeft) {
                                onDoubleTapLeft()
                                doubleTapSide = "left"
                            } else {
                                onDoubleTapRight()
                                doubleTapSide = "right"
                            }
                            coroutineScope.launch {
                                doubleTapAnim.snapTo(0.8f)
                                doubleTapAnim.animateTo(1.3f, tween(350))
                                doubleTapSide = null
                            }
                        }
                    )
                }
            }
            .pointerInput(isLocked) {
                val topExclusionPx = topExclusionDp.toPx()
                if (!isLocked) {
                    var isHorizontal = false
                    var isVertical = false
                    var isLeft = false
                    var totalDragX = 0f
                    var totalDragY = 0f
                    var suppressed = false

                    detectDragGestures(
                        onDragStart = { offset ->
                            suppressed = offset.y < topExclusionPx
                            isLeft = offset.x < size.width / 2
                            isHorizontal = false
                            isVertical = false
                            totalDragX = 0f
                            totalDragY = 0f
                        },
                        onDrag = { change, dragAmount ->
                            if (suppressed) return@detectDragGestures
                            change.consume()
                            totalDragX += dragAmount.x
                            totalDragY += dragAmount.y

                            if (!isHorizontal && !isVertical) {
                                if (Math.abs(totalDragX) > 25f && Math.abs(totalDragX) > Math.abs(totalDragY)) {
                                    isHorizontal = true
                                    onScrubStart()
                                } else if (Math.abs(totalDragY) > 25f) {
                                    isVertical = true
                                }
                            }

                            if (isHorizontal) {
                                // 1 pixel = 150ms of scrub
                                val seekDeltaMs = (totalDragX * 120).toLong()
                                onScrubMove(seekDeltaMs)
                            } else if (isVertical) {
                                val delta = -dragAmount.y / size.height.toFloat()
                                if (isLeft) {
                                    onBrightnessDelta(delta)
                                } else {
                                    onVolumeDelta(delta)
                                }
                            }
                        },
                        onDragEnd = {
                            if (isHorizontal) {
                                onScrubEnd()
                            }
                            isHorizontal = false
                            isVertical = false
                        },
                        onDragCancel = {
                            if (isHorizontal) {
                                onScrubEnd()
                            }
                            isHorizontal = false
                            isVertical = false
                        }
                    )
                }
            }
    ) {
        // Double-tap visual feedback animation
        if (doubleTapSide != null) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.5f)
                    .align(if (doubleTapSide == "left") Alignment.CenterStart else Alignment.CenterEnd)
                    .background(
                        Brush.horizontalGradient(
                            if (doubleTapSide == "left")
                                listOf(Color(0x3300E5FF), Color.Transparent)
                            else
                                listOf(Color.Transparent, Color(0x3300E5FF))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.scale(doubleTapAnim.value)
                ) {
                    Icon(
                        imageVector = if (doubleTapSide == "left") Icons.Default.FastRewind else Icons.Default.FastForward,
                        contentDescription = null,
                        tint = HiPrimaryCyan,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = if (doubleTapSide == "left") "-10s" else "+10s",
                        color = HiPrimaryCyan,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
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
                        .clip(RoundedCornerShape(16.dp))
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
                            tint = HiAccentAmber,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .width(8.dp)
                                .height(100.dp)
                                .clip(RoundedCornerShape(4.dp))
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
                val percent = (level * 100).toInt()
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
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
                            tint = if (isBoosted) HiAccentAmber else HiPrimaryCyan,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .width(8.dp)
                                .height(100.dp)
                                .clip(RoundedCornerShape(4.dp))
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
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isBoosted) "$percent% Boost" else "$percent%",
                            color = if (isBoosted) HiAccentAmber else Color.White,
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
                        .clip(RoundedCornerShape(12.dp))
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
