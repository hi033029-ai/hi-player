package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun PlayPauseButton(isPlaying: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = Color(0xFF00D4FF)) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.8f else 1f, tween(110), label = "playPauseScale")
    IconButton(onClick = { pressed = true; onClick() }, modifier = modifier.size(44.dp).scale(scale).clip(CircleShape)) {
        AnimatedContent(
            targetState = isPlaying,
            transitionSpec = { (scaleIn(initialScale = 0.55f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.55f) + fadeOut()) },
            label = "playPauseIcon",
        ) { playing ->
            Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
        }
    }
    LaunchedEffect(pressed) { if (pressed) { kotlinx.coroutines.delay(110); pressed = false } }
}

@Composable
fun SkipButton(icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = Color.White) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.78f else 1f, tween(100), label = "skipScale")
    IconButton(onClick = { pressed = true; onClick() }, modifier = modifier.size(36.dp).scale(scale)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
    LaunchedEffect(pressed) { if (pressed) { kotlinx.coroutines.delay(100); pressed = false } }
}
