package com.example.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp

/**
 * Slim neon seek bar: a thin track with a gradient fill and a glowing thumb.
 * The track thickens and the thumb grows while the user scrubs. Dragging
 * previews locally (via [onScrub]) and seeks once on release.
 */
@Composable
fun NeonSeekBar(
    progress: Float,
    enabled: Boolean,
    startColor: Color,
    endColor: Color,
    trackColor: Color,
    onSeekFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onScrub: (Float?) -> Unit = {},
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val scrubbing = dragFraction != null
    val shown = (dragFraction ?: progress).coerceIn(0f, 1f)
    val trackHeight by animateDpAsState(if (scrubbing) 8.dp else 4.dp, tween(160), label = "seekTrack")
    val thumbRadius by animateDpAsState(if (scrubbing) 10.dp else 7.dp, tween(160), label = "seekThumb")
    val glow by animateFloatAsState(if (scrubbing) 0.45f else 0.25f, tween(160), label = "seekGlow")
    Canvas(
        modifier
            .fillMaxWidth()
            .height(32.dp)
            .testTag("audio_neon_seekbar")
            .semantics {
                contentDescription = "Music playback position"
                progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..1f)
                if (enabled) setProgress { target -> onSeekFraction(target.coerceIn(0f, 1f)); true }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset -> onSeekFraction((offset.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragFraction = (offset.x / size.width).coerceIn(0f, 1f); onScrub(dragFraction)
                    },
                    onDragEnd = {
                        dragFraction?.let(onSeekFraction); dragFraction = null; onScrub(null)
                    },
                    onDragCancel = { dragFraction = null; onScrub(null) },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f); onScrub(dragFraction)
                    },
                )
            }
    ) {
        val thumbR = thumbRadius.toPx()
        val left = thumbR
        val width = (size.width - thumbR * 2f).coerceAtLeast(1f)
        val h = trackHeight.toPx()
        val top = (size.height - h) / 2f
        val radius = CornerRadius(h / 2f)
        val playedX = left + width * shown
        drawRoundRect(trackColor, Offset(left, top), Size(width, h), radius)
        if (playedX > left) {
            val fill = Brush.horizontalGradient(listOf(startColor, endColor), startX = left, endX = left + width)
            // Soft glow under the played part.
            drawRoundRect(fill, Offset(left, top - h), Size(playedX - left, h * 3f), CornerRadius(h * 1.5f), alpha = glow * 0.6f)
            drawRoundRect(fill, Offset(left, top), Size(playedX - left, h), radius)
        }
        if (enabled) {
            val thumbColor = androidx.compose.ui.graphics.lerp(startColor, endColor, shown)
            val center = Offset(playedX, size.height / 2f)
            drawCircle(thumbColor, thumbR * 1.9f, center, alpha = glow)
            drawCircle(thumbColor, thumbR, center)
            drawCircle(Color.White, thumbR * 0.42f, center, alpha = 0.9f)
        }
    }
}
