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

val LocalHiUiMetrics = compositionLocalOf {
    HiUiMetrics(
        headerHeight = 64.dp,
        logoSize = 38.dp,
        titleTextSize = 24f.sp
    )
}

private fun createColorScheme(palette: HiThemePalette) = if (palette.isDark) {
    darkColorScheme(
        primary = palette.primary,
        onPrimary = contrastContentColor(palette.primary, palette.surface),
        primaryContainer = palette.surfaceElevated,
        onPrimaryContainer = palette.textPrimary,
        secondary = palette.secondary,
        onSecondary = contrastContentColor(palette.secondary, palette.surface),
        secondaryContainer = palette.surfaceElevated,
        onSecondaryContainer = palette.textPrimary,
        tertiary = palette.secondary,
        onTertiary = contrastContentColor(palette.secondary, palette.surface),
        background = palette.background,
        onBackground = palette.textPrimary,
        surface = palette.surface,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.surfaceElevated,
        onSurfaceVariant = palette.textSecondary,
        outline = palette.surfaceBorder
    )
} else {
    lightColorScheme(
        primary = palette.primary,
        onPrimary = contrastContentColor(palette.primary, palette.surface),
        primaryContainer = palette.surfaceElevated,
        onPrimaryContainer = palette.textPrimary,
        secondary = palette.secondary,
        onSecondary = contrastContentColor(palette.secondary, palette.surface),
        secondaryContainer = palette.surfaceElevated,
        onSecondaryContainer = palette.textPrimary,
        tertiary = palette.secondary,
        onTertiary = contrastContentColor(palette.secondary, palette.surface),
        background = palette.background,
        onBackground = palette.textPrimary,
        surface = palette.surface,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.surfaceElevated,
        onSurfaceVariant = palette.textSecondary,
        outline = palette.surfaceBorder
    )
}

@Composable
fun HiPlayerTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    uiTextScale: Float = 1f,
    customPalette: HiThemePalette? = null,
    content: @Composable () -> Unit
) {
    val palette = if (themeMode == AppThemeMode.CUSTOM) {
        customPalette ?: IceBluePalette
    } else {
        getPaletteForTheme(themeMode, isSystemInDarkTheme())
    }
    val colorScheme = remember(palette) { createColorScheme(palette) }
    val baseDensity = LocalDensity.current
    val scaledDensity = Density(
        density = baseDensity.density,
        fontScale = (baseDensity.fontScale * uiTextScale).coerceAtLeast(0.75f)
    )

    val uiMetrics = HiUiMetrics(
        headerHeight = (64.dp * uiTextScale).coerceIn(56.dp, 88.dp),
        logoSize = (38.dp * uiTextScale).coerceIn(34.dp, 52.dp),
        titleTextSize = 24.sp
    )

    CompositionLocalProvider(
        LocalHiPalette provides palette,
        LocalHiUiMetrics provides uiMetrics,
        LocalDensity provides scaledDensity
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
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
        themeMode = if (darkTheme) AppThemeMode.CYAN_NEON_DARK else AppThemeMode.WARM_SUNSET_LIGHT,
        content = content
    )
}
