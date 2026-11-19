package com.example.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.example.data.AppPlayerSettings
import com.example.data.AppThemeMode

// Base Defaults for Cinema Dark
val HiCinemaBlack = Color(0xFF0A0E14)
val HiSurfaceDark = Color(0xFF131922)
val HiSurfaceElevated = Color(0xFF1C2533)
val HiSurfaceBorder = Color(0xFF273549)

val HiPrimaryCyan = Color(0xFF00E5FF)
val HiPrimaryBlue = Color(0xFF2979FF)
val HiAccentAmber = Color(0xFFFFB300)
val HiAccentEmerald = Color(0xFF00E676)
val HiErrorRed = Color(0xFFFF5252)

val HiTextPrimary = Color(0xFFFFFFFF)
val HiTextSecondary = Color(0xFF8A94A6)
val HiTextTertiary = Color(0xFF5A6578)

// Material 3 mappings
val HiPrimary = Color(0xFF00E5FF)
val HiOnPrimary = Color(0xFF003258)
val HiPrimaryContainer = Color(0xFF075985)
val HiOnPrimaryContainer = Color(0xFFE0F2FE)

val HiSecondary = Color(0xFF38D39F)
val HiOnSecondary = Color(0xFF003822)
val HiSecondaryContainer = Color(0xFF047857)
val HiOnSecondaryContainer = Color(0xFFD1FAE5)

val HiTertiary = Color(0xFFF59E0B)
val HiOnTertiary = Color(0xFF451A03)
val HiTertiaryContainer = Color(0xFF78350F)
val HiOnTertiaryContainer = Color(0xFFFEF3C7)

val HiBackground = Color(0xFF0A0E14)
val HiOnBackground = Color(0xFFFFFFFF)
val HiSurface = Color(0xFF131922)
val HiOnSurface = Color(0xFFE2E8F0)
val HiSurfaceVariant = Color(0xFF1C2533)
val HiOnSurfaceVariant = Color(0xFFCBD5E1)
val HiOutline = Color(0xFF273549)

data class HiThemePalette(
    val background: Color,
    val backgroundGradient: List<Color>? = null,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceBorder: Color,
    val primary: Color,
    val secondary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val isDark: Boolean
) {
    val selectionSurface: Color
        get() = primary.copy(alpha = primary.alpha * 0.12f).compositeOver(surface)
}

// Neutral surfaces keep artwork prominent while making card boundaries visible.
val MonochromeDarkPalette = HiThemePalette(
    background = Color(0xFF0C0E12),
    surface = Color(0xFF171A20),
    surfaceElevated = Color(0xFF22262E),
    surfaceBorder = visibleThemeBorder(Color(0xFF171A20), Color.White, true),
    primary = Color(0xFFF2F4F8),
    secondary = Color(0xFFCED5E0),
    textPrimary = Color(0xFFF2F4F8),
    textSecondary = Color(0xFFB1BAC8),
    textTertiary = Color(0xFF96A1B2),
    isDark = true
)

val MonochromeLightPalette = HiThemePalette(
    background = Color(0xFFF3F5F8),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFE9EDF2),
    surfaceBorder = visibleThemeBorder(Color.White, Color.Black, false),
    primary = Color(0xFF202631),
    secondary = Color(0xFF495568),
    textPrimary = Color(0xFF171D27),
    textSecondary = Color(0xFF515D6E),
    textTertiary = Color(0xFF586577),
    isDark = false
)

val IceBluePalette = HiThemePalette(
    background = Color(0xFFEDF3FB),
    backgroundGradient = listOf(Color(0xFFEDF3FB), Color(0xFFE6F0FB)),
    surface = Color(0xFFFAFCFF),
    surfaceElevated = Color(0xFFDFEAF8),
    surfaceBorder = visibleThemeBorder(Color(0xFFFAFCFF), Color(0xFF245FD6), false),
    primary = Color(0xFF245FD6),
    secondary = Color(0xFF22676E),
    textPrimary = Color(0xFF172B46),
    textSecondary = Color(0xFF455B76),
    textTertiary = Color(0xFF536780),
    isDark = false
)

fun getPaletteForTheme(mode: AppThemeMode, systemDark: Boolean = false): HiThemePalette {
    return when (mode) {
        AppThemeMode.SYSTEM -> if (systemDark) MonochromeDarkPalette else MonochromeLightPalette
        AppThemeMode.HI_PLAYER_BLUE -> IceBluePalette
        AppThemeMode.CUSTOM -> IceBluePalette
    }
}

private fun customColor(hex: String, opacity: Int): Color = try {
    Color(android.graphics.Color.parseColor(hex)).copy(alpha = opacity.coerceIn(0, 100) / 100f)
} catch (_: IllegalArgumentException) {
    Color.White.copy(alpha = opacity.coerceIn(0, 100) / 100f)
}

fun getCustomPalette(settings: AppPlayerSettings): HiThemePalette {
    val background = customColor(settings.customThemeBackgroundHex, settings.customThemeBackgroundOpacity)
    val text = customColor(settings.customThemeTextHex, settings.customThemeTextOpacity)
    val primary = customColor(settings.customThemePrimaryHex, settings.customThemePrimaryOpacity)
    val surface = customColor(settings.customThemeSurfaceHex, settings.customThemeSurfaceOpacity)
    val luminance = background.red * 0.2126f + background.green * 0.7152f + background.blue * 0.0722f
    val isDark = luminance < 0.5f
    return HiThemePalette(
        background = background,
        surface = surface,
        surfaceElevated = surface.copy(alpha = (surface.alpha + 0.12f).coerceAtMost(1f)),
        surfaceBorder = visibleThemeBorder(surface, primary, isDark),
        primary = primary,
        secondary = primary.copy(alpha = 0.72f),
        textPrimary = text,
        textSecondary = text.copy(alpha = (text.alpha * 0.68f).coerceIn(0f, 1f)),
        textTertiary = text.copy(alpha = (text.alpha * 0.45f).coerceIn(0f, 1f)),
        isDark = isDark,
    )
}

val LocalHiPalette = staticCompositionLocalOf { MonochromeDarkPalette }
