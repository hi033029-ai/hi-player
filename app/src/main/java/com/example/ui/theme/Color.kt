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

private val BlackGoldPalette = HiThemePalette(
    background = Color(0xFF08090B),
    surface = Color(0xFF141E36),
    surfaceElevated = Color(0xFF202A43),
    surfaceBorder = visibleThemeBorder(Color(0xFF141E36), Color(0xFFFFA20B), true),
    primary = Color(0xFFFFA20B),
    secondary = Color(0xFFCDCDD1),
    textPrimary = Color(0xFFFBFAF6),
    textSecondary = Color(0xFFDDDDE1),
    textTertiary = Color(0xFFB9BCC6),
    isDark = true,
)

private val SoftLavenderPalette = HiThemePalette(
    background = Color(0xFF202034),
    surface = Color(0xFF2C2C44),
    surfaceElevated = Color(0xFF40445C),
    surfaceBorder = visibleThemeBorder(Color(0xFF2C2C44), Color(0xFFE9CBE2), true),
    primary = Color(0xFFE9CBE2),
    secondary = Color(0xFFD8BFB6),
    textPrimary = Color(0xFFF4EAE7),
    textSecondary = Color(0xFFE0D3DC),
    textTertiary = Color(0xFFD1C0D1),
    isDark = true,
)

private val DeepSeaPalette = HiThemePalette(
    background = Color(0xFF0D1E2C),
    surface = Color(0xFF172434),
    surfaceElevated = Color(0xFF293F56),
    surfaceBorder = visibleThemeBorder(Color(0xFF172434), Color(0xFFADC4DF), true),
    primary = Color(0xFFADC4DF),
    secondary = Color(0xFFBBCDDD),
    textPrimary = Color(0xFFF0F1EC),
    textSecondary = Color(0xFFC5D3E3),
    textTertiary = Color(0xFFB0C0D3),
    isDark = true,
)

private val WarmNeutralPalette = HiThemePalette(
    background = Color(0xFFF4EAE3),
    surface = Color(0xFFFFF9F3),
    surfaceElevated = Color(0xFFF0DFD3),
    surfaceBorder = visibleThemeBorder(Color(0xFFFFF9F3), Color(0xFF804B36), false),
    primary = Color(0xFF804B36),
    secondary = Color(0xFF5D654F),
    textPrimary = Color(0xFF312720),
    textSecondary = Color(0xFF5C4B41),
    textTertiary = Color(0xFF68564A),
    isDark = false,
)

private val BoldBerryPalette = HiThemePalette(
    background = Color(0xFF390C22),
    surface = Color(0xFF4B142D),
    surfaceElevated = Color(0xFF692342),
    surfaceBorder = visibleThemeBorder(Color(0xFF4B142D), Color(0xFFFFADBA), true),
    primary = Color(0xFFFFADBA),
    secondary = Color(0xFFFAD5BA),
    textPrimary = Color(0xFFFFF2EA),
    textSecondary = Color(0xFFF5CAD2),
    textTertiary = Color(0xFFE9B2BF),
    isDark = true,
)

private val SageLinenPalette = HiThemePalette(
    background = Color(0xFFF2F4EC), surface = Color(0xFFFAFBF5), surfaceElevated = Color(0xFFE3E9D9),
    surfaceBorder = visibleThemeBorder(Color(0xFFFAFBF5), Color(0xFF395642), false),
    primary = Color(0xFF395642), secondary = Color(0xFF56604A),
    textPrimary = Color(0xFF26392C), textSecondary = Color(0xFF455445), textTertiary = Color(0xFF536050), isDark = false,
)

private val PorcelainBluePalette = HiThemePalette(
    background = Color(0xFFEFF3F9), surface = Color(0xFFFAFCFF), surfaceElevated = Color(0xFFDFE7F3),
    surfaceBorder = visibleThemeBorder(Color(0xFFFAFCFF), Color(0xFF43568B), false),
    primary = Color(0xFF43568B), secondary = Color(0xFF416376),
    textPrimary = Color(0xFF283853), textSecondary = Color(0xFF45546C), textTertiary = Color(0xFF556078), isDark = false,
)

private val RosePaperPalette = HiThemePalette(
    background = Color(0xFFF8EFF0), surface = Color(0xFFFFFAFA), surfaceElevated = Color(0xFFF1DFE3),
    surfaceBorder = visibleThemeBorder(Color(0xFFFFFAFA), Color(0xFF814A61), false),
    primary = Color(0xFF814A61), secondary = Color(0xFF705456),
    textPrimary = Color(0xFF402C35), textSecondary = Color(0xFF634C56), textTertiary = Color(0xFF735B65), isDark = false,
)

fun getPaletteForTheme(mode: AppThemeMode, systemDark: Boolean = false): HiThemePalette {
    return when (mode) {
        AppThemeMode.SYSTEM -> if (systemDark) MonochromeDarkPalette else MonochromeLightPalette
        AppThemeMode.HI_PLAYER_BLUE -> IceBluePalette
        AppThemeMode.BLACK_GOLD -> BlackGoldPalette
        AppThemeMode.SOFT_LAVENDER -> SoftLavenderPalette
        AppThemeMode.DEEP_SEA -> DeepSeaPalette
        AppThemeMode.WARM_NEUTRAL -> WarmNeutralPalette
        AppThemeMode.BOLD_BERRY -> BoldBerryPalette
        AppThemeMode.SAGE_LINEN -> SageLinenPalette
        AppThemeMode.PORCELAIN_BLUE -> PorcelainBluePalette
        AppThemeMode.ROSE_PAPER -> RosePaperPalette
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
