package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AspectRatioMode

/** A text-only, transparent indicator shown briefly after the screen resize mode changes. */
@Composable
fun AspectRatioOverlayIndicator(
    visible: Boolean,
    mode: AspectRatioMode,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                text = mode.displayName,
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
