package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R

/**
 * Modern Custom Shaped Logo Badge for Hi Player
 * Features a modern Squircle/Stadium shape with dynamic double-ring gradient border.
 */
@Composable
fun HiPlayerLogoBadge(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    shape: Shape = RoundedCornerShape(percent = 38), // Continuous smooth Squircle
    backgroundColor: Color = Color(0xFFFFFFFF),
    borderColor: Color = Color(0xFF0056B3)
) {
    val outerGlowBrush = Brush.linearGradient(
        colors = listOf(
            borderColor,
            Color(0xFF00E5FF),
            borderColor.copy(alpha = 0.6f)
        )
    )

    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = 6.dp,
                shape = shape,
                ambientColor = borderColor.copy(alpha = 0.3f),
                spotColor = borderColor.copy(alpha = 0.4f)
            )
            .clip(shape)
            .background(outerGlowBrush)
            .padding(2.dp) // Outer border width
            .clip(shape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.hi_player_logo),
            contentDescription = "Hi Player Logo",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(size - 4.dp)
                .clip(shape)
        )
    }
}
