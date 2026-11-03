package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sin

/**
 * Compact played/unplayed waveform scrubber for the music bar.
 * The envelope is deterministic per track, so it renders immediately for
 * local and remote audio without decoding the entire file on the UI thread.
 */
@Composable
fun WaveformSeekBar(
    amplitudes: FloatArray,
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onSeekFinished: (Float) -> Unit = {},
    playedColor: Color = MaterialTheme.colorScheme.primary,
    unplayedColor: Color = playedColor.copy(alpha = 0.28f),
    barWidth: Dp = 3.dp,
    barGap: Dp = 2.dp,
    minBarHeight: Dp = 3.dp,
) {
    var dragProgress by remember { mutableStateOf<Float?>(null) }
    val shown = (dragProgress ?: progress).coerceIn(0f, 1f)
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnFinished by rememberUpdatedState(onSeekFinished)

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val p = (offset.x / size.width).coerceIn(0f, 1f)
                    currentOnSeek(p)
                    currentOnFinished(p)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val p = (offset.x / size.width).coerceIn(0f, 1f)
                        dragProgress = p
                        currentOnSeek(p)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val p = (change.position.x / size.width).coerceIn(0f, 1f)
                        dragProgress = p
                        currentOnSeek(p)
                    },
                    onDragEnd = {
                        currentOnFinished(dragProgress ?: progress.coerceIn(0f, 1f))
                        dragProgress = null
                    },
                    onDragCancel = { dragProgress = null },
                )
            }
    ) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val source = amplitudes.takeIf { it.isNotEmpty() } ?: FloatArray(96) { 0.25f }
        val width = barWidth.toPx().coerceAtLeast(1f)
        val gap = barGap.toPx().coerceAtLeast(0f)
        val count = ((size.width + gap) / (width + gap)).toInt().coerceAtLeast(1)
        val step = (size.width - width).coerceAtLeast(0f) / (count - 1).coerceAtLeast(1)
        val minHeight = minBarHeight.toPx().coerceAtLeast(1f)
        val center = size.height / 2f

        repeat(count) { index ->
            val sourceIndex = (index * source.size / count).coerceIn(0, source.lastIndex)
            val height = (source[sourceIndex].coerceIn(0f, 1f) * size.height)
                .coerceIn(minHeight, size.height)
            val x = width / 2f + index * step
            drawLine(
                color = if ((index + 0.5f) / count <= shown) playedColor else unplayedColor,
                start = Offset(x, center - height / 2f),
                end = Offset(x, center + height / 2f),
                strokeWidth = width,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** Stable envelope from a URI, avoiding a heavyweight file decode for the UI. */
fun waveformAmplitudesFor(key: String, count: Int = 96): FloatArray {
    val seed = abs(key.hashCode()).toFloat()
    return FloatArray(count) { index ->
        val primary = abs(sin((index + 1) * (0.17f + seed % 13f / 100f)))
        val detail = abs(sin((index + 3) * 0.61f + seed % 29f))
        (0.16f + primary * 0.48f + detail * 0.28f).coerceIn(0.12f, 1f)
    }
}

@Composable
fun rememberWaveformAmplitudes(key: String): FloatArray =
    remember(key) { waveformAmplitudesFor(key) }
