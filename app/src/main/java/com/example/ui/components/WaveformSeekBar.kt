package com.example.ui.components

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Seekable waveform progress bar. [peaks] are real decoded amplitudes (0..1);
 * while they are unavailable a flat neutral bar is drawn, never invented data.
 * Dragging previews the position locally and seeks once on release.
 */
@Composable
fun WaveformSeekBar(
    progress: Float,
    peaks: FloatArray?,
    enabled: Boolean,
    playedColor: Color,
    remainingColor: Color,
    onSeekFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
    onScrub: (Float?) -> Unit = {},
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val shown = (dragFraction ?: progress).coerceIn(0f, 1f)
    // Bars rise from flat when the decoded waveform arrives.
    val reveal by animateFloatAsState(if (peaks != null) 1f else 0f, tween(450), label = "waveformReveal")
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .testTag("audio_waveform_seekbar")
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
        val count = peaks?.size?.takeIf { it > 0 } ?: com.example.audio.AudioWaveformExtractor.BUCKETS
        val slot = size.width / count
        val barWidth = (slot * 0.62f).coerceAtLeast(1.5f)
        val minBar = 3.dp.toPx()
        val maxBar = size.height * 0.92f
        val playedX = size.width * shown
        for (i in 0 until count) {
            val amplitude = peaks?.getOrNull(i)?.coerceIn(0f, 1f) ?: 0f
            val h = minBar + (maxBar - minBar) * amplitude * reveal
            val x = i * slot + (slot - barWidth) / 2f
            val center = x + barWidth / 2f
            val color = if (center <= playedX) playedColor else remainingColor
            drawRoundRect(color, Offset(x, (size.height - h) / 2f), Size(barWidth, h), CornerRadius(barWidth / 2f))
        }
        // Playhead
        val headWidth = 2.dp.toPx()
        drawRoundRect(playedColor, Offset((playedX - headWidth / 2f).coerceIn(0f, size.width - headWidth), 0f),
            Size(headWidth, size.height), CornerRadius(headWidth))
    }
}
