package com.example.browser.gecko

import org.junit.Assert.assertEquals
import org.junit.Test

class GeckoAppearanceTest {
    @Test fun standardZoomPreservesSystemFontScale() {
        assertEquals(1.3f, GeckoAppearancePolicy.fontScale(100, 1.3f), 0.0001f)
    }
    @Test fun userZoomMultipliesSystemScale() {
        assertEquals(1.8f, GeckoAppearancePolicy.fontScale(150, 1.2f), 0.0001f)
    }
    @Test fun userZoomUsesExistingBounds() {
        assertEquals(0.8f, GeckoAppearancePolicy.fontScale(10, 1f), 0.0001f)
        assertEquals(2f, GeckoAppearancePolicy.fontScale(300, 1f), 0.0001f)
    }
    @Test fun invalidSystemScaleUsesNeutralFallback() {
        assertEquals(1f, GeckoAppearancePolicy.fontScale(100, Float.NaN), 0.0001f)
        assertEquals(1f, GeckoAppearancePolicy.fontScale(100, 0f), 0.0001f)
    }
}
