package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import com.example.data.AppPlayerSettings
import com.example.data.AppThemeMode
import org.junit.Assert.*
import org.junit.Test

class SystemThemeContrastTest {
    @Test fun systemIsFirstAndDefault() {
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.entries.first())
        assertEquals(AppThemeMode.SYSTEM, AppPlayerSettings().themeMode)
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromId(null))
    }
    @Test fun systemResolvesBothModesAndExplicitChoicesStayFixed() {
        assertEquals(MonochromeDarkPalette, getPaletteForTheme(AppThemeMode.SYSTEM, true))
        assertEquals(MonochromeLightPalette, getPaletteForTheme(AppThemeMode.SYSTEM, false))
        assertEquals(MonochromeLightPalette, getPaletteForTheme(AppThemeMode.WARM_SUNSET_LIGHT, true))
        assertEquals(AppThemeMode.CYAN_NEON_DARK, AppThemeMode.fromId("CYAN_NEON_DARK"))
        assertEquals(AppThemeMode.WARM_SUNSET_LIGHT, AppThemeMode.fromId("WARM_SUNSET_LIGHT"))
    }
    @Test fun labelsContrastWithActualFillIncludingTransparency() {
        assertEquals(Color.Black, contrastContentColor(Color.White, Color.Black))
        assertEquals(Color.White, contrastContentColor(Color.Black, Color.White))
        assertEquals(Color.White, contrastContentColor(Color.White.copy(alpha = 0.05f), Color.Black))
        assertEquals(Color.Black, contrastContentColor(Color.Black.copy(alpha = 0.05f), Color.White))
    }
}
