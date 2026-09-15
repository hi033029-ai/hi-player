package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/**
 * The HDR icon in the controls bar:
 *  - amber glow pulse (infinite, subtle) while HDR is active on the current stream
 *  - grey/static when inactive
 *  - a small spinner replaces the icon while `isSwitching` is true, and the icon is
 *    NON-CLICKABLE during that window — this is what prevents the double-tap race
 *    that caused the freeze (see HdrColorModeManager.kt).
 *  - a quick scale "pop" on tap for tactile feedback.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HdrIcon(
    isActive: Boolean,
    isSwitching: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.85f else 1f,
        animationSpec = tween(120),
        label = "hdrPressScale",
    )

    val infiniteTransition = rememberInfiniteTransition(label = "hdrGlow")
    val glow by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "hdrGlowAlpha",
    )

    val baseColor = if (isActive) {
        lerp(Color(0xFFB98A2E), Color(0xFFFFC94A), glow) // amber pulse when HDR is live
    } else {
        Color.White.copy(alpha = 0.7f)
    }

    if (isSwitching) {
        CircularProgressIndicator(
            modifier = modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = Color.White,
        )
    } else {
        Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = baseColor,
            modifier = modifier
                .scale(pressScale)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = !isSwitching, // guard: no taps can queue up while a switch is in flight
                    onClick = {
                        pressed = true
                        onClick()
                    },
                    onLongClick = onLongClick,
                ),
        )
    }

    LaunchedEffect(pressed) {
        if (pressed) {
            kotlinx.coroutines.delay(120)
            pressed = false
        }
    }
}
