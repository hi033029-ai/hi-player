package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.player.ColorPreset
import com.example.player.valuesSummary
import com.example.ui.theme.HiPrimaryCyan

/** Animated, touch-transparent on-screen indicator shown when HDR is toggled ON/OFF or a preset changes. */
@Composable
fun HdrOverlayIndicator(
    visible: Boolean,
    hdrActive: Boolean,
    preset: ColorPreset,
    modifier: Modifier = Modifier,
) {
    val accent by animateColorAsState(
        targetValue = if (hdrActive) HiPrimaryCyan else Color.White.copy(alpha = 0.72f),
        animationSpec = tween(durationMillis = 240),
        label = "hdr_indicator_accent",
    )

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.86f, animationSpec = tween(220, easing = FastOutSlowInEasing)),
        exit = fadeOut(tween(240)) + scaleOut(targetScale = 0.92f, animationSpec = tween(220)),
        modifier = modifier,
    ) {
        val shape = RoundedCornerShape(16.dp)
        Column(
            modifier = Modifier
                .clip(shape)
                .background(Color(0xDC0A0F19), shape)
                .border(
                    width = 1.dp,
                    color = accent.copy(alpha = if (hdrActive) 0.85f else 0.35f),
                    shape = shape,
                )
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = if (hdrActive) 0.85f else 0.55f)),
                )
                Text(
                    text = if (hdrActive) "HDR ON" else "HDR OFF",
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                )
                Text(
                    text = "· ${preset.name}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = preset.valuesSummary(),
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 9.sp,
                maxLines = 1,
            )
        }
    }
}
