package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R

/** One vector master for headers, onboarding, splash and Hi Browser branding. */
@Composable
fun rememberHiPlayerLogoPainter(): Painter = painterResource(R.drawable.hi_player_brand_mark)

/** White-backed vector badge. No decorative frame competes with the pixel detail. */
@Composable
fun HiPlayerLogoBadge(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    shape: Shape = RoundedCornerShape(percent = 38), // Continuous smooth Squircle
    backgroundColor: Color = Color(0xFFFFFFFF)
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = rememberHiPlayerLogoPainter(),
            contentDescription = "Hi Player Logo",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(size)
                .clip(shape)
        )
    }
}
