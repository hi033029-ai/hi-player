package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import com.example.data.AppThemeMode

/** Shared header geometry; dimensions scale with the persisted global text-size setting. */
data class HiUiMetrics(
    val headerHeight: Dp,
    val logoSize: Dp
)

val LocalHiUiMetrics = compositionLocalOf { HiUiMetrics(headerHeight = 64.dp, logoSize = 38.dp) }

private fun createColorScheme(palette: HiThemePalette) = if (palette.isDark) {
    darkColorScheme(
        primary = palette.primary,
        onPrimary = if (palette.isDark) palette.background else palette.surface,
        primaryContainer = palette.surfaceElevated,
        onPrimaryContainer = palette.textPrimary,
        secondary = palette.secondary,
        onSecondary = palette.background,
        secondaryContainer = palette.surfaceElevated,
        onSecondaryContainer = palette.textPrimary,
        tertiary = palette.secondary,
        onTertiary = palette.background,
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
        onPrimary = androidx.compose.ui.graphics.Color.White,
        primaryContainer = palette.surfaceElevated,
        onPrimaryContainer = palette.textPrimary,
        secondary = palette.secondary,
        onSecondary = androidx.compose.ui.graphics.Color.White,
        secondaryContainer = palette.surfaceElevated,
        onSecondaryContainer = palette.textPrimary,
        tertiary = palette.secondary,
        onTertiary = androidx.compose.ui.graphics.Color.White,
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
    themeMode: AppThemeMode = AppThemeMode.WARM_SUNSET_LIGHT,
    uiTextScale: Float = 1f,
    content: @Composable () -> Unit
) {
    val palette = getPaletteForTheme(themeMode)
    val colorScheme = createColorScheme(palette)
    val baseDensity = LocalDensity.current
    val scaledDensity = Density(
        density = baseDensity.density,
        fontScale = (baseDensity.fontScale * uiTextScale).coerceIn(0.75f, 1.5f)
    )

    val uiMetrics = HiUiMetrics(
        headerHeight = (64.dp * uiTextScale).coerceIn(56.dp, 88.dp),
        logoSize = (38.dp * uiTextScale).coerceIn(34.dp, 52.dp)
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


