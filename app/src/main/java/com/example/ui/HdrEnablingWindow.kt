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
import androidx.compose.foundation.layout.Arrangement
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

private val Amber = Color(0xFFF5A623)
private val Ink = Color(0xFF0A0A0F)

private class HdrPixel(
    val dx: Float,      // offset from centre, dp
    val dy: Float,
    val ux: Float,      // unit direction away from centre
    val uy: Float,
    val frac: Float,    // 0..1 clockwise from top: when the sweep reaches it
    val hue: Float,     // 0..360
    val phase: Float,   // twinkle phase
    val size: Float,    // 0.55..1 of the base cell
)

/** Ragged ring of square "pixels" around the badge: dense near it, sparse outside. */
private fun buildPixels(): List<HdrPixel> {
    val rnd = Random(7)
    val step = 13f
    val inner = 84f
    val outer = 148f
    val n = 11
    val twoPi = (2 * PI).toFloat()
    val out = ArrayList<HdrPixel>()
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
        out += HdrPixel(
            dx = x, dy = y, ux = x / d, uy = y / d,
            frac = frac, hue = frac * 360f,
            phase = rnd.nextFloat() * twoPi,
            size = 0.55f + 0.45f * rnd.nextFloat(),
        )
    }
    return out
}

/**
 * Full-screen "HDR is turning on" window.
 *
 * Usage:
 * ```
 * var showHdrAnim by remember { mutableStateOf(false) }
 *
 * Switch(
 *     checked = hdrOn,
 *     onCheckedChange = { hdrOn = it; if (it) showHdrAnim = true }
 * )
 *
 * HdrEnablingWindow(visible = showHdrAnim) { showHdrAnim = false }
 * ```
 * [onFinished] fires once the animation has played out (about 2.6 s).
 */
@Composable
fun HdrEnablingWindow(
    visible: Boolean,
    presetName: String,
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
        HdrEnablingContent(presetName, onFinished)
    }
}

@Composable
private fun HdrEnablingContent(
    presetName: String,
    onFinished: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val currentOnFinished by rememberUpdatedState(onFinished)

    val progress = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }
    val pop = remember { Animatable(1f) }
    val done = remember { Animatable(0f) } // 0 -> 1 when finished

    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = 1600, easing = FastOutSlowInEasing))
        done.snapTo(1f)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        coroutineScope {
            launch { burst.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
            launch {
                pop.animateTo(1.18f, tween(120))
                pop.animateTo(
                    1f,
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow,
                    ),
                )
            }
        }
        delay(600)
        currentOnFinished()
    }

    val pixels = remember { buildPixels() }

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
            .background(Ink.copy(alpha = 0.88f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(modifier = Modifier.size(300.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val c = center
                    val maxR = size.minDimension / 2f

                    // Soft glow that brightens as HDR "turns on"
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Amber.copy(alpha = 0.10f + 0.38f * progress.value),
                                Color.Transparent,
                            ),
                            center = c,
                            radius = maxR * 0.95f,
                        ),
                        radius = maxR * 0.95f,
                        center = c,
                    )

                    // Colourful pixels: grey (SDR) until the sweep reaches them,
                    // then they light up in vivid colour (HDR) and twinkle.
                    val dens = density
                    val baseCell = 10f * dens
                    val push = sin(burst.value * PI.toFloat()) * 16f * dens
                    val twoPi = (2 * PI).toFloat()
                    // Once the sweep completes the circle, the pixels drift outward and fade away.
                    val fade = 1f - burst.value
                    for (p in pixels) {
                        if (fade <= 0f) break
                        val lit = ((progress.value * 1.2f - p.frac) / 0.2f).coerceIn(0f, 1f)
                        val twinkle = if (lit >= 1f) {
                            0.65f + 0.35f * sin(twoPi * twinklePhase + p.phase)
                        } else 1f
                        val vivid = Color.hsv(p.hue, 0.85f, 1f).copy(alpha = twinkle)
                        val color = lerp(Color.White.copy(alpha = 0.07f), vivid, lit)
                        val s = baseCell * p.size * (1f + 0.35f * lit) *
                            (1f + 0.5f * sin(burst.value * PI.toFloat()))
                        val px = c.x + p.dx * dens + p.ux * push - s / 2f
                        val py = c.y + p.dy * dens + p.uy * push - s / 2f
                        drawRect(
                            color = color.copy(alpha = color.alpha * fade),
                            topLeft = Offset(px, py),
                            size = Size(s, s),
                        )
                    }

                    // Completion flash
                    if (burst.value > 0f) {
                        drawCircle(
                            color = Color.White.copy(alpha = (1f - burst.value) * 0.35f),
                            radius = maxR * burst.value,
                            center = c,
                        )
                    }
                }

                // Centre text: dull grey -> glowing amber
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.scale((0.88f + 0.12f * progress.value) * pop.value),
                ) {
                    Text(
                        text = if (done.value >= 1f) "Enabled" else "Enabling",
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
                            color = lerp(Color.White.copy(alpha = 0.55f), Amber, progress.value),
                            shadow = Shadow(
                                color = Amber.copy(alpha = progress.value),
                                blurRadius = 8f + 40f * progress.value,
                            ),
                        ),
                    )
                    Text(presetName, color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}
