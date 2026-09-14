package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

@Composable
fun HdrIcon(
    isActive: Boolean,
    isSwitching: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.85f else 1f,
        animationSpec = tween(120),
        label = "hdrPressScale",
    )
    val transition = rememberInfiniteTransition(label = "hdrGlow")
    val glow by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "hdrGlowAlpha",
    )
    if (isSwitching) {
        CircularProgressIndicator(modifier = modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
    } else {
        Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = "HDR",
            tint = if (isActive) lerp(Color(0xFFB98A2E), Color(0xFFFFC94A), glow) else Color.White.copy(alpha = 0.7f),
            modifier = modifier
                .size(22.dp)
                .scale(pressScale)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = !isSwitching,
                ) {
                    pressed = true
                    onClick()
                },
        )
    }
    LaunchedEffect(pressed) {
        if (pressed) {
            kotlinx.coroutines.delay(120)
            pressed = false
        }
    }
}
