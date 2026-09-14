package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SeekPreviewHud(
    targetMs: Long?,
    deltaMs: Long,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = targetMs != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        targetMs?.let { target ->
            val totalSeconds = target.coerceAtLeast(0L) / 1000L
            val hours = totalSeconds / 3600L
            val minutes = (totalSeconds % 3600L) / 60L
            val seconds = totalSeconds % 60L
            val time = if (hours > 0L) {
                "%d:%02d:%02d".format(hours, minutes, seconds)
            } else {
                "%02d:%02d".format(minutes, seconds)
            }
            val deltaSeconds = deltaMs / 1000L
            val sign = if (deltaSeconds >= 0L) "+" else ""
            Row(
                modifier = Modifier
                    .background(Color(0xE60A0E18), RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(time, color = Color.White, fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "[$sign${deltaSeconds}s]",
                    color = if (deltaSeconds >= 0L) HiPrimaryCyan else HiAccentAmber,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

private val HiAccentAmber: Color
    get() = Color(0xFFFFB020)
private val HiPrimaryCyan: Color
    get() = Color(0xFF00D9FF)
