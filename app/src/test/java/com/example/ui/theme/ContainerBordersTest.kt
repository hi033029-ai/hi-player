package com.example.ui.theme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test
class ContainerBordersTest {
    @Test fun builtInThemesHaveSubtleDistinctSeparators() {
        val borders = listOf(MonochromeDarkPalette, MonochromeLightPalette, IceBluePalette).map { palette ->
            assertEquals(1f, palette.surfaceBorder.alpha, .001f)
            assertNotEquals(palette.surfaceElevated, palette.surfaceBorder)
            palette.surfaceBorder
        }
        assertEquals(3, borders.distinct().size)
    }
    @Test fun decorativeOutlinesDoNotBecomeHighContrastFrames() {
        listOf(Color.Black, Color.White, Color(0xFFD8ECF7), Color(0xFF473F81)).forEach { surface ->
            listOf(Color.Transparent, Color.Cyan, Color.Black, Color.White).forEach { accent ->
                val border = visibleThemeBorder(surface, accent, surface.luminance() < .5f)
                val a = border.luminance(); val b = surface.luminance()
                val contrast = (maxOf(a,b) + .05f) / (minOf(a,b) + .05f)
                assertTrue(contrast > 1f && contrast < 2.5f)
                assertEquals(1f, border.alpha, .001f)
            }
        }
    }
}
