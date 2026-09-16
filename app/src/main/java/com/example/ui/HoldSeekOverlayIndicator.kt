package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HoldSeekOverlayIndicator(
    side: HoldSeekSide?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = side != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.28f), RoundedCornerShape(10.dp))
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Text(
                text = if (side == HoldSeekSide.REWIND) "‹‹ 2×" else "2× ››",
                color = Color.White,
                fontSize = 17.sp,
            )
        }
    }
}

enum class HoldSeekSide {
    REWIND,
    FORWARD,
}
