package com.example.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserAppearancePolicyTest {
    @Test fun zoomRespectsSupportedBounds() {
        assertEquals(80, BrowserAppearancePolicy.textZoom(10))
        assertEquals(100, BrowserAppearancePolicy.textZoom(100))
        assertEquals(150, BrowserAppearancePolicy.textZoom(150))
        assertEquals(200, BrowserAppearancePolicy.textZoom(300))
    }

    @Test fun desktopRequestPreservesInstalledChromiumVersion() {
        val desktop = BrowserAppearancePolicy.desktopUserAgent("Mozilla/5.0 (Linux; Android 15; wv) AppleWebKit/537.36 Version/4.0 Chrome/140.0.7339.123 Mobile Safari/537.36")
        assertTrue(desktop.contains("Chrome/140.0.7339.123"))
        assertTrue(desktop.contains("X11; Linux x86_64"))
        assertFalse(desktop.contains("Android"))
        assertFalse(desktop.contains("Mobile"))
        assertFalse(desktop.contains("wv"))
    }

    @Test fun unknownEngineDoesNotGetInventedVersion() {
        assertEquals("Example/1.0", BrowserAppearancePolicy.desktopUserAgent("Example/1.0 Mobile"))
    }
}
