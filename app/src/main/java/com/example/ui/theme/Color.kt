package com.example.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
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
)

// 1. Monochrome Dark: Background: #000000 | Accent: #FFFFFF | Text: #FFFFFF | Subtext: #8A8A8A
val MonochromeDarkPalette = HiThemePalette(
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceElevated = Color(0xFF121212),
    surfaceBorder = Color.Transparent,
    primary = Color(0xFFFFFFFF),
    secondary = Color(0xFFCCCCCC),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF8A8A8A),
    textTertiary = Color(0xFF5A6578),
    isDark = true
)

// 2. Monochrome Light: Background: #FFFFFF | Accent: #000000 | Text: #000000 | Subtext: #8A8A8A
val MonochromeLightPalette = HiThemePalette(
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFF5F5F5),
    surfaceBorder = Color.Transparent,
    primary = Color(0xFF000000),
    secondary = Color(0xFF333333),
    textPrimary = Color(0xFF000000),
    textSecondary = Color(0xFF8A8A8A),
    textTertiary = Color(0xFFAAAAAA),
    isDark = false
)

// 3. Classic Red & Black: Background: #000000 | Accent: #E10600 | Text: #E10600 | Subtext: #6B6B6B
val ClassicRedBlackPalette = HiThemePalette(
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceElevated = Color(0xFF121212),
    surfaceBorder = Color.Transparent,
    primary = Color(0xFFE10600),
    secondary = Color(0xFFB30500),
    textPrimary = Color(0xFFE10600),
    textSecondary = Color(0xFF6B6B6B),
    textTertiary = Color(0xFF909090),
    isDark = true
)

// 4. Hi Player Logo: Background: Ice Blue Gradient | Accent: Deep Blue & Cyan | Text: Dark Slate
val HiPlayerLogoPalette = HiThemePalette(
    background = Color(0xFFB5D8EB),
    backgroundGradient = listOf(Color(0xFFB5D8EB), Color(0xFFA5CCE2)),
    surface = Color(0xFFC7E3F2),
    surfaceElevated = Color(0xFFD8ECF7),
    surfaceBorder = Color.Transparent,
    primary = Color(0xFF0056B3),
    secondary = Color(0xFF1E3A8A),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF334155),
    textTertiary = Color(0xFF64748B),
    isDark = false
)

fun getPaletteForTheme(mode: AppThemeMode): HiThemePalette {
    return when (mode) {
        AppThemeMode.CYAN_NEON_DARK -> MonochromeDarkPalette
        AppThemeMode.WARM_SUNSET_LIGHT -> MonochromeLightPalette
        AppThemeMode.CLASSIC_RED_BLACK -> ClassicRedBlackPalette
        AppThemeMode.HI_PLAYER_BLUE -> HiPlayerLogoPalette
    }
}

val LocalHiPalette = staticCompositionLocalOf { MonochromeDarkPalette }
