package com.example.ui.theme

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Animate colors, never replace/recreate screens or their playback/navigation state. */
@Composable
internal fun animatedThemePalette(target: HiThemePalette): HiThemePalette {
    // Compose applies the OS animator duration scale, including disabled animations.
    val transition = updateTransition(targetState = target, label = "app_appearance")
    val background by transition.animateColor({ tween(360) }, label = "background") { it.background }
    val surface by transition.animateColor({ tween(360) }, label = "surface") { it.surface }
    val elevated by transition.animateColor({ tween(360) }, label = "raised_surface") { it.surfaceElevated }
    val border by transition.animateColor({ tween(360) }, label = "border") { it.surfaceBorder }
    val primary by transition.animateColor({ tween(360) }, label = "primary") { it.primary }
    val secondary by transition.animateColor({ tween(360) }, label = "secondary") { it.secondary }
    val text by transition.animateColor({ tween(360) }, label = "text") { it.textPrimary }
    val muted by transition.animateColor({ tween(360) }, label = "secondary_text") { it.textSecondary }
    val tertiary by transition.animateColor({ tween(360) }, label = "tertiary_text") { it.textTertiary }
    val gradientStart by transition.animateColor({ tween(360) }, label = "gradient_start") { it.backgroundGradient?.firstOrNull() ?: it.background }
    val gradientEnd by transition.animateColor({ tween(360) }, label = "gradient_end") { it.backgroundGradient?.lastOrNull() ?: it.background }
    val moving = transition.currentState != transition.targetState || transition.isRunning
    val surfaces = listOf(background, surface, elevated)
    return target.copy(background = background, surface = surface, surfaceElevated = elevated,
        surfaceBorder = border, primary = primary, secondary = secondary,
        textPrimary = if (moving) transitionText(text, surfaces) else target.textPrimary,
        textSecondary = if (moving) transitionText(muted, surfaces) else target.textSecondary,
        textTertiary = if (moving) transitionText(tertiary, surfaces) else target.textTertiary,
        backgroundGradient = if (moving || target.backgroundGradient != null) listOf(gradientStart, gradientEnd) else null)
}

/** Black/white themes otherwise pass through unreadable grey-on-grey halfway through. */
internal fun transitionText(candidate: Color, surfaces: List<Color>): Color {
    fun minimumContrast(foreground: Color): Float = surfaces.minOf { background ->
        val a = foreground.luminance(); val b = background.luminance()
        (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
    }
    if (minimumContrast(candidate) >= 4.5f) return candidate
    return if (minimumContrast(Color.Black) >= minimumContrast(Color.White)) Color.Black else Color.White
}
