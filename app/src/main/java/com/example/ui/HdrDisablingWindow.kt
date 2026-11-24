package com.example.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

private val OffAmber = Color(0xFFF5A623)
private val OffInk = Color(0xFF0A0A0F)

private class HdrOffPixel(
    val dx: Float,      // offset from centre, dp
    val dy: Float,
    val ux: Float,      // unit direction away from centre
    val uy: Float,
    val frac: Float,    // 0..1 clockwise from top: when the sweep reaches it
    val hue: Float,     // 0..360
    val phase: Float,   // twinkle phase
    val size: Float,    // 0.55..1 of the base cell
)

/** Same ragged ring of pixels as the enabling window. */
private fun buildOffPixels(): List<HdrOffPixel> {
    val rnd = Random(7)
    val step = 13f
    val inner = 84f
    val outer = 148f
    val n = 11
    val twoPi = (2 * PI).toFloat()
    val out = ArrayList<HdrOffPixel>()
    for (iy in -n..n) for (ix in -n..n) {
        val x = ix * step
        val y = iy * step
        val d = hypot(x, y)
        if (d < inner || d > outer) continue
        val keep = 1f - (d - inner) / (outer - inner)
        if (rnd.nextFloat() > keep * 1.1f + 0.15f) continue
        var a = atan2(x, -y)
        if (a < 0f) a += twoPi
        val frac = a / twoPi
        out += HdrOffPixel(
            dx = x, dy = y, ux = x / d, uy = y / d,
            frac = frac, hue = frac * 360f,
            phase = rnd.nextFloat() * twoPi,
            size = 0.55f + 0.45f * rnd.nextFloat(),
        )
    }
    return out
}

/**
 * Full-screen "HDR is turning off" window. The mirror of [HdrEnablingWindow]:
 * the colourful pixels start lit, drain to grey in a clockwise sweep, then
 * collapse inward and fade away while the "HDR" text dims from amber to grey.
 *
 * Usage:
 * ```
 * var showOn by remember { mutableStateOf(false) }
 * var showOff by remember { mutableStateOf(false) }
 *
 * Switch(
 *     checked = hdrOn,
 *     onCheckedChange = {
 *         hdrOn = it
 *         if (it) showOn = true else showOff = true
 *     }
 * )
 *
 * HdrEnablingWindow(visible = showOn) { showOn = false }
 * HdrDisablingWindow(visible = showOff) { showOff = false }
 * ```
 * [onFinished] fires once the animation has played out (about 2.5 s).
 */
@Composable
fun HdrDisablingWindow(
    visible: Boolean,
    onFinished: () -> Unit,
) {
    if (!visible) return

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        HdrDisablingContent(onFinished = onFinished)
    }
}

@Composable
private fun HdrDisablingContent(
    onFinished: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val currentOnFinished by rememberUpdatedState(onFinished)

    val progress = remember { Animatable(0f) }
    val collapse = remember { Animatable(0f) }
    val pop = remember { Animatable(1f) }
    val done = remember { Animatable(0f) } // 0 -> 1 when finished

    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = 1400, easing = FastOutSlowInEasing))
        done.snapTo(1f)
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        coroutineScope {
            launch { collapse.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
            launch {
                pop.animateTo(0.9f, tween(120))
                pop.animateTo(
                    1f,
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow,
                    ),
                )
            }
        }
        delay(500)
        currentOnFinished()
    }

    val pixels = remember { buildOffPixels() }

    val twinkleTransition = rememberInfiniteTransition(label = "twinkle")
    val twinklePhase by twinkleTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "twinklePhase",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OffInk.copy(alpha = 0.88f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.size(300.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val c = center
                val maxR = size.minDimension / 2f

                // Soft glow that dims as HDR "turns off"
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            OffAmber.copy(alpha = 0.10f + 0.38f * (1f - progress.value)),
                            Color.Transparent,
                        ),
                        center = c,
                        radius = maxR * 0.95f,
                    ),
                    radius = maxR * 0.95f,
                    center = c,
                )

                // Colourful pixels start vivid, then drain to grey (SDR) as the sweep
                // reaches them. When the circle completes they collapse inward and fade.
                val dens = density
                val baseCell = 10f * dens
                val pull = -collapse.value * 18f * dens
                val twoPi = (2 * PI).toFloat()
                val fade = 1f - collapse.value
                for (p in pixels) {
                    if (fade <= 0f) break
                    val drained = ((progress.value * 1.2f - p.frac) / 0.2f).coerceIn(0f, 1f)
                    val twinkle = 0.65f + 0.35f * sin(twoPi * twinklePhase + p.phase)
                    val vivid = Color.hsv(p.hue, 0.85f, 1f).copy(alpha = twinkle)
                    val color = lerp(vivid, Color.White.copy(alpha = 0.07f), drained)
                    val s = baseCell * p.size * (1f + 0.35f * (1f - drained)) *
                        (1f - 0.3f * collapse.value)
                    val px = c.x + p.dx * dens + p.ux * pull - s / 2f
                    val py = c.y + p.dy * dens + p.uy * pull - s / 2f
                    drawRect(
                        color = color.copy(alpha = color.alpha * fade),
                        topLeft = Offset(px, py),
                        size = Size(s, s),
                    )
                }
            }

            // Centre text: glowing amber -> dull grey
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.scale((1f - 0.08f * progress.value) * pop.value),
            ) {
                Text(
                    text = if (done.value >= 1f) "Disabled" else "Disabling",
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 2.sp,
                        color = Color.White.copy(alpha = 0.75f),
                    ),
                )
                Text(
                    text = "HDR",
                    style = TextStyle(
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 4.sp,
                        color = lerp(OffAmber, Color.White.copy(alpha = 0.55f), progress.value),
                        shadow = Shadow(
                            color = OffAmber.copy(alpha = 1f - progress.value),
                            blurRadius = 8f + 40f * (1f - progress.value),
                        ),
                    ),
                )
            }
        }
    }
}
