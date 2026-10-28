package com.example.ui.theme

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Decorative separators stay quiet; text and icons carry accessibility contrast. */
fun visibleThemeBorder(surface: Color, accent: Color, isDark: Boolean): Color {
    val canvas = surface.compositeOver(if (isDark) Color.Black else Color.White).copy(alpha = 1f)
    return lerp(canvas, if (isDark) Color.White else Color.Black, if (isDark) .18f else .12f)
}

@Composable
fun Modifier.hiContainerBorder(shape: Shape = RoundedCornerShape(14.dp), selected: Boolean = false, width: Dp = 0.5.dp): Modifier {
    val palette = LocalHiPalette.current
    val color = if (selected) palette.primary.copy(alpha = .65f) else palette.surfaceBorder
    return border(if (selected) maxOf(width, 1.dp) else width, color, shape)
}
