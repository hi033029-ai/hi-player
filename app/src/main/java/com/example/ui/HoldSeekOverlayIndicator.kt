package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material3.Icon
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (side == HoldSeekSide.REWIND) {
                    Icon(
                        imageVector = Icons.Default.FastRewind,
                        contentDescription = "Rewinding at 2x",
                        tint = Color.White,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Rewind 2×", color = Color.White, fontSize = 15.sp)
                } else {
                    Text("Fast forward 2×", color = Color.White, fontSize = 15.sp)
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "Fast forwarding at 2x",
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

enum class HoldSeekSide {
    REWIND,
    FORWARD,
}
