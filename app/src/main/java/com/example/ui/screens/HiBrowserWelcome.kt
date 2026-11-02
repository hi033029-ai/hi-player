package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.example.ui.components.HiPlayerLogoBadge
import com.example.ui.theme.LocalHiPalette

/** Decorative entrance on the browser home; browsing stays usable from the first frame. */
@Composable
internal fun HiBrowserWelcome(isPrivate: Boolean, modifier: Modifier = Modifier) {
    val palette = LocalHiPalette.current
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(1350, easing = FastOutSlowInEasing)) }
    val p = progress.value
    Column(modifier.testTag("browser_welcome_animation"), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
            HiPlayerLogoBadge(size = 92.dp, modifier = Modifier.scale(0.86f + 0.14f * p)
                .graphicsLayer { alpha = 0.72f + 0.28f * p; translationY = (1f - p) * 8.dp.toPx() })
            Canvas(Modifier.size(104.dp)) {
                val points = listOf(0.1f to 0.22f, 0.83f to 0.15f, 0.92f to 0.62f,
                    0.15f to 0.78f, 0.80f to 0.88f, 0.04f to 0.51f)
                points.forEachIndexed { index, (x, y) ->
                    val dx = (x - 0.5f) * (1f - p) * 0.12f
                    val dy = (y - 0.5f) * (1f - p) * 0.12f
                    val side = size.width * if (index % 2 == 0) 0.045f else 0.03f
                    drawRect(palette.primary.copy(alpha = (1f - p) * 0.65f),
                        topLeft = Offset(size.width * (x + dx), size.height * (y + dy)),
                        size = Size(side, side))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        AnimatedContent(targetState = p < 0.72f,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
            label = "browser_welcome_title") { welcoming ->
            Text(
                if (welcoming) {
                    if (isPrivate) "Welcome to private browsing" else "Welcome to Hi Browser"
                } else if (isPrivate) "Private browsing" else "Hi Browser",
                color = palette.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}
