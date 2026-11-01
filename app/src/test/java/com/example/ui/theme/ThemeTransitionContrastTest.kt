package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test

class ThemeTransitionContrastTest {
    @Test fun greyMidpointUsesLegibleForeground() {
        val grey = Color(0xFF808080)
        val text = transitionText(grey, listOf(grey))
        val ratio = (maxOf(text.luminance(), grey.luminance()) + 0.05f) /
            (minOf(text.luminance(), grey.luminance()) + 0.05f)
        assertTrue(ratio >= 4.5f)
    }
    @Test fun alreadyLegibleTextIsUnchanged() {
        assertEquals(Color.White, transitionText(Color.White, listOf(Color.Black)))
    }
}
