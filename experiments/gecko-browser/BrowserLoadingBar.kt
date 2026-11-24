package com.example.browser.gecko

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** How long the progress can stay unchanged before the "stuck" wave starts. */
private const val STUCK_AFTER_MS = 1200L

/** Default bar colour: electric cyan. Pass any other colour via the [color] parameter. */
private val DefaultBarColor = Color(0xFF22D3EE)

/**
 * Smooth glowing page-loading bar for the browser, matching the HDR windows.
 *
 * A slim bar in the chosen [color] that goes from a deep shade to a bright, pale
 * head with an HDR-style bloom, and a soft sheen of light that slides along it. If the page
 * gets stuck (progress unchanged for about 1.2 s), the bar turns into a flowing
 * ribbon: a wave travels through it, swelling and thinning, until the page
 * moves again.
 *
 * - [progress] 0f..1f (page load progress)
 * - [loading] true while a page is loading; when it turns false the bar
 *   runs to 100 %, then fades away
 * - [color] main colour; the deep and bright shades are derived from it
 *   (e.g. Color(0xFF22D3EE) cyan, 0xFFA78BFA violet, 0xFF4ADE80 green,
 *   0xFFF472B6 pink, 0xFFF5A623 amber)
 *
 * Put it at the top of the web view:
 * ```
 * Box(Modifier.fillMaxSize()) {
 *     AndroidView(factory = { geckoView }, modifier = Modifier.fillMaxSize())
 *     BrowserLoadingBar(
 *         progress = progress,
 *         loading = loading,
 *         modifier = Modifier.align(Alignment.TopCenter),
 *     )
 * }
 * ```
 *
 * GeckoView wiring:
 * ```
 * var progress by remember { mutableFloatStateOf(0f) }
 * var loading by remember { mutableStateOf(false) }
 *
 * DisposableEffect(session) {
 *     session.progressDelegate = object : GeckoSession.ProgressDelegate {
 *         override fun onPageStart(session: GeckoSession, url: String) {
 *             progress = 0f
 *             loading = true
 *         }
 *         override fun onProgressChange(session: GeckoSession, p: Int) {
 *             progress = p / 100f
 *         }
 *         override fun onPageStop(session: GeckoSession, success: Boolean) {
 *             progress = 1f
 *             loading = false
 *         }
 *     }
 *     onDispose { session.progressDelegate = null }
 * }
 * ```
 */
@Composable
fun BrowserLoadingBar(
    progress: Float,
    loading: Boolean,
    modifier: Modifier = Modifier,
    color: Color = DefaultBarColor,
) {
    val deep = remember(color) { lerp(color, Color.Black, 0.45f) }
    val hot = remember(color) { lerp(color, Color.White, 0.65f) }
    val shown = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }
    var stuck by remember { mutableStateOf(false) }

    // Start of a load: reset and show. End of a load: run to 100 %, then fade out.
    LaunchedEffect(loading) {
        if (loading) {
            shown.snapTo(0f)
            alpha.snapTo(1f)
        } else {
            shown.animateTo(1f, tween(250, easing = FastOutSlowInEasing))
            delay(200)
            alpha.animateTo(0f, tween(300))
        }
    }

    // While loading: follow the reported progress, with a small head start
    // so the bar is visible right away and never reaches the end early.
    LaunchedEffect(loading, progress) {
        if (loading) {
            shown.animateTo(
                progress.coerceIn(0.08f, 0.95f),
                tween(250, easing = FastOutSlowInEasing),
            )
        }
    }

    // Stuck detection: restarts every time the progress changes.
    LaunchedEffect(loading, progress) {
        stuck = false
        if (loading) {
            delay(STUCK_AFTER_MS)
            stuck = true
        }
    }

    // 0 = calm bar, 1 = full flowing wave (eases in and out)
    val waveAmount by animateFloatAsState(
        targetValue = if (loading && stuck) 1f else 0f,
        animationSpec = tween(500),
        label = "waveAmount",
    )

    if (!loading && alpha.value == 0f) return

    val transition = rememberInfiniteTransition(label = "bar")
    val wavePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "wavePhase",
    )
    val sheenPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "sheenPhase",
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(16.dp)
            .graphicsLayer { this.alpha = alpha.value },
    ) {
        val w = size.width
        val cy = size.height / 2f
        val twoPi = (2 * PI).toFloat()
        val fillW = w * shown.value

        // Faint track
        val trackH = 3.dp.toPx()
        drawRoundRect(
            color = Color.White.copy(alpha = 0.06f),
            topLeft = Offset(0f, cy - trackH / 2f),
            size = Size(w, trackH),
            cornerRadius = CornerRadius(trackH / 2f),
        )

        if (fillW <= 0f) return@Canvas

        // Ribbon outline: thickness = base + travelling sine wave.
        val baseThick = 5.dp.toPx()
        val amp = 3.dp.toPx() * waveAmount
        val waveLen = 64.dp.toPx()
        val taper = 24.dp.toPx()
        fun thick(x: Float): Float {
            val env = min(1f, min(x / taper, (fillW - x) / taper + 0.35f)).coerceAtLeast(0f)
            val t = baseThick + amp * sin(twoPi * (x / waveLen - wavePhase)) * env
            return max(2.dp.toPx(), t)
        }

        val stepPx = 2.dp.toPx()
        val ribbon = Path().apply {
            moveTo(0f, cy - thick(0f) / 2f)
            var x = 0f
            while (x < fillW) {
                lineTo(x, cy - thick(x) / 2f)
                x += stepPx
            }
            lineTo(fillW, cy - thick(fillW) / 2f)
            lineTo(fillW, cy + thick(fillW) / 2f)
            x = fillW
            while (x > 0f) {
                lineTo(x, cy + thick(x) / 2f)
                x -= stepPx
            }
            lineTo(0f, cy + thick(0f) / 2f)
            close()
        }

        // Fill: deep amber at the start, hot at the head
        drawPath(
            path = ribbon,
            brush = Brush.horizontalGradient(
                colors = listOf(deep, color, hot),
                startX = 0f,
                endX = fillW,
            ),
        )

        // Sheen: a soft band of light sliding along the bar (stronger while stuck)
        val sheenW = 48.dp.toPx()
        val sx = sheenPhase * (w + sheenW * 2f) - sheenW
        clipPath(ribbon) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.25f + 0.35f * waveAmount),
                        Color.Transparent,
                    ),
                    startX = sx - sheenW,
                    endX = sx + sheenW,
                ),
                size = Size(w, size.height),
            )
        }

        // HDR bloom at the head, pulsing while stuck
        val pulse = 1f + 0.25f * waveAmount * sin(twoPi * wavePhase)
        val bloomR = 12.dp.toPx() * pulse
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.9f),
                    color.copy(alpha = 0.35f),
                    Color.Transparent,
                ),
                center = Offset(fillW, cy),
                radius = bloomR,
            ),
            radius = bloomR,
            center = Offset(fillW, cy),
        )
    }
}
