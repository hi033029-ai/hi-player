package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance

/** Choose the higher WCAG-contrast label for the actual composited control fill. */
fun contrastContentColor(fill: Color, surface: Color = Color.White): Color {
    val opaqueSurface = surface.compositeOver(Color.White)
    return if (fill.compositeOver(opaqueSurface).luminance() > 0.179f) Color.Black else Color.White
}
