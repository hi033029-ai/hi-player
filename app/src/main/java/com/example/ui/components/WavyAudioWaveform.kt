package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin

@Composable
fun WavyAudioWaveform(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 28.dp,
    waveColor: Color = Color(0xFF00E5FF)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave_anim")
    
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val amplitudeMultiplier by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "amplitude"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val width = size.width
        val centerY = size.height / 2f
        val maxAmplitude = (size.height / 2.5f) * (if (isPlaying) amplitudeMultiplier else 0.15f)

        val path = Path()
        val path2 = Path()

        val wavelength = width / 2.5f

        path.moveTo(0f, centerY)
        path2.moveTo(0f, centerY)

        var x = 0f
        val step = 4f

        while (x <= width) {
            val angle = (x / wavelength) * (2 * Math.PI).toFloat() + phase
            val y = centerY + sin(angle) * maxAmplitude
            path.lineTo(x, y.toFloat())

            val angle2 = (x / wavelength) * (2 * Math.PI).toFloat() - phase * 0.8f
            val y2 = centerY + sin(angle2) * (maxAmplitude * 0.6f)
            path2.lineTo(x, y2.toFloat())

            x += step
        }

        drawPath(
            path = path,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    waveColor.copy(alpha = 0.2f),
                    waveColor,
                    waveColor.copy(alpha = 0.8f),
                    waveColor.copy(alpha = 0.2f)
                )
            ),
            style = Stroke(width = 3.dp.toPx())
        )

        drawPath(
            path = path2,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    waveColor.copy(alpha = 0.1f),
                    waveColor.copy(alpha = 0.5f),
                    waveColor.copy(alpha = 0.1f)
                )
            ),
            style = Stroke(width = 1.5.dp.toPx())
        )
    }
}
