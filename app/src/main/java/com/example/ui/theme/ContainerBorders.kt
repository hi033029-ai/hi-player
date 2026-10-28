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

/** Contrast-aware outline, including custom themes with transparent accents. */
fun visibleThemeBorder(surface: Color, accent: Color, isDark: Boolean): Color {
    val canvas = surface.compositeOver(if (isDark) Color.Black else Color.White).copy(alpha = 1f)
    fun contrast(a: Color, b: Color): Float {
        val x = a.luminance(); val y = b.luminance()
        return (maxOf(x, y) + .05f) / (minOf(x, y) + .05f)
    }
    val endpoint = if (contrast(Color.Black, canvas) > contrast(Color.White, canvas)) Color.Black else Color.White
    var outline = lerp(canvas, accent.compositeOver(canvas).copy(alpha = 1f), .55f)
    repeat(20) {
        if (contrast(outline, canvas) >= 3f) return outline
        outline = lerp(outline, endpoint, .14f)
    }
    return endpoint
}

@Composable
fun Modifier.hiContainerBorder(shape: Shape = RoundedCornerShape(14.dp), selected: Boolean = false, width: Dp = 1.dp): Modifier {
    val palette = LocalHiPalette.current
    val color = if (selected) visibleThemeBorder(palette.surfaceElevated, palette.primary, palette.isDark) else palette.surfaceBorder
    return border(if (selected) maxOf(width, 2.dp) else width, color, shape)
}
