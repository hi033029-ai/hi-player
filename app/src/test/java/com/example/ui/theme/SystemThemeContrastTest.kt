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
    @Test fun systemResolvesBothModesAndRemovedMonoIdsFollowSystem() {
        assertEquals(MonochromeDarkPalette, getPaletteForTheme(AppThemeMode.SYSTEM, true))
        assertEquals(MonochromeLightPalette, getPaletteForTheme(AppThemeMode.SYSTEM, false))
        for (id in listOf("CYAN_NEON_DARK", "WARM_SUNSET_LIGHT", "MONOCHROME_DARK", "MONOCHROME_LIGHT"))
            assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromId(id))
        assertEquals(listOf("SYSTEM", "HI_PLAYER_BLUE", "CUSTOM"), AppThemeMode.entries.map { it.id })
    }
    @Test fun labelsContrastWithActualFillIncludingTransparency() {
        assertEquals(Color.Black, contrastContentColor(Color.White, Color.Black))
        assertEquals(Color.White, contrastContentColor(Color.Black, Color.White))
        assertEquals(Color.White, contrastContentColor(Color.White.copy(alpha = 0.05f), Color.Black))
        assertEquals(Color.Black, contrastContentColor(Color.Black.copy(alpha = 0.05f), Color.White))
    }
}
