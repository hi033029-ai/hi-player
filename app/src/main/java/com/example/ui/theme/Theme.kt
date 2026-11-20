package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import com.example.data.AppThemeMode
import com.example.ui.theme.getCustomPalette

/** Shared header geometry; dimensions scale with the persisted global text-size setting. */
data class HiUiMetrics(
    val headerHeight: Dp,
    val logoSize: Dp,
    val titleTextSize: androidx.compose.ui.unit.TextUnit
)
private const val COMPACT_APP_FONT_SCALE = 0.85f

val LocalHiUiMetrics = compositionLocalOf {
    HiUiMetrics(
        headerHeight = 56.dp,
        logoSize = 32.dp,
        titleTextSize = 18f.sp
    )
}

private fun createColorScheme(palette: HiThemePalette) = if (palette.isDark) {
    darkColorScheme(
        primary = palette.primary,
        onPrimary = contrastContentColor(palette.primary, palette.surface),
        primaryContainer = palette.selectionSurface,
        onPrimaryContainer = palette.textPrimary,
        secondary = palette.secondary,
        onSecondary = contrastContentColor(palette.secondary, palette.surface),
        secondaryContainer = palette.selectionSurface,
        onSecondaryContainer = palette.textPrimary,
        tertiary = palette.secondary,
        onTertiary = contrastContentColor(palette.secondary, palette.surface),
        background = palette.background,
        onBackground = palette.textPrimary,
        surface = palette.surface,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.surfaceElevated,
        onSurfaceVariant = palette.textSecondary,
        outline = palette.surfaceBorder,
        outlineVariant = palette.surfaceBorder.copy(alpha = 0.55f),
        surfaceContainerLowest = palette.background,
        surfaceContainerLow = palette.surface,
        surfaceContainer = palette.surface,
        surfaceContainerHigh = palette.surfaceElevated,
        surfaceContainerHighest = palette.surfaceElevated,
        surfaceBright = palette.surfaceElevated,
        surfaceDim = palette.background,
    )
} else {
    lightColorScheme(
        primary = palette.primary,
        onPrimary = contrastContentColor(palette.primary, palette.surface),
        primaryContainer = palette.selectionSurface,
        onPrimaryContainer = palette.textPrimary,
        secondary = palette.secondary,
        onSecondary = contrastContentColor(palette.secondary, palette.surface),
        secondaryContainer = palette.selectionSurface,
        onSecondaryContainer = palette.textPrimary,
        tertiary = palette.secondary,
        onTertiary = contrastContentColor(palette.secondary, palette.surface),
        background = palette.background,
        onBackground = palette.textPrimary,
        surface = palette.surface,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.surfaceElevated,
        onSurfaceVariant = palette.textSecondary,
        outline = palette.surfaceBorder,
        outlineVariant = palette.surfaceBorder.copy(alpha = 0.55f),
        surfaceContainerLowest = palette.background,
        surfaceContainerLow = palette.surface,
        surfaceContainer = palette.surface,
        surfaceContainerHigh = palette.surfaceElevated,
        surfaceContainerHighest = palette.surfaceElevated,
        surfaceBright = palette.surface,
        surfaceDim = palette.background,
    )
}

@Composable
fun HiPlayerTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    uiTextScale: Float = 1f,
    customPalette: HiThemePalette? = null,
    systemDarkOverride: Boolean? = null,
    content: @Composable () -> Unit
) {
    val targetPalette = if (themeMode == AppThemeMode.CUSTOM) {
        customPalette ?: IceBluePalette
    } else {
        getPaletteForTheme(themeMode, systemDarkOverride ?: isSystemInDarkTheme())
    }
    val palette = animatedThemePalette(targetPalette)
    val colorScheme = remember(palette) { createColorScheme(palette) }
    val baseDensity = LocalDensity.current
    val scaledDensity = Density(
        density = baseDensity.density,
        fontScale = (baseDensity.fontScale * uiTextScale * COMPACT_APP_FONT_SCALE).coerceAtLeast(0.65f)
    )

    val uiMetrics = HiUiMetrics(
        headerHeight = (56.dp * uiTextScale).coerceIn(48.dp, 76.dp),
        logoSize = (32.dp * uiTextScale).coerceIn(28.dp, 46.dp),
        titleTextSize = 18.sp
    )

    CompositionLocalProvider(
        LocalHiPalette provides palette,
        LocalHiUiMetrics provides uiMetrics,
        LocalDensity provides scaledDensity
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = HiShapes,
            content = content
        )
    }
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    HiPlayerTheme(
        themeMode = AppThemeMode.SYSTEM,
        systemDarkOverride = darkTheme,
        content = content
    )
}
