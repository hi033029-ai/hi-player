package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.ui.theme.LocalHiPalette

/**
 * Full-screen "Browser mode" intro shown each time the user enters the browser:
 * the globe spins and grows in, a ring pulses out, the title rises, then everything fades away.
 */
@Composable
internal fun HiBrowserModeIntro(onFinished: () -> Unit) {
    val palette = LocalHiPalette.current
    val appear = remember { Animatable(0f) }
    val ring = remember { Animatable(0f) }
    val leave = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { ring.animateTo(1f, tween(1100, easing = LinearEasing)) }
        appear.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
        delay(500)
        leave.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
        onFinished()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - leave.value }
            .background(palette.background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                val grow = 0.7f + 0.3f * appear.value + 0.12f * leave.value
                scaleX = grow
                scaleY = grow
                alpha = appear.value
            },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .graphicsLayer {
                            val s = 1f + ring.value * 0.9f
                            scaleX = s
                            scaleY = s
                            alpha = (1f - ring.value) * 0.8f
                        }
                        .border(3.dp, palette.primary, CircleShape),
                )
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .graphicsLayer { rotationZ = (1f - appear.value) * -240f }
                        .clip(CircleShape)
                        .background(palette.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Language, contentDescription = null, tint = palette.primary, modifier = Modifier.size(54.dp))
                }
            }
            Spacer(Modifier.height(22.dp))
            Text(
                "Browser mode",
                color = palette.textPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (6f * (1f - appear.value)).sp,
                modifier = Modifier.graphicsLayer { translationY = (1f - appear.value) * 36.dp.toPx() },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Press back to return to Hi Player",
                color = palette.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.graphicsLayer {
                    alpha = ((appear.value - 0.4f) / 0.6f).coerceIn(0f, 1f)
                    translationY = (1f - appear.value) * 48.dp.toPx()
                },
            )
        }
    }
}
