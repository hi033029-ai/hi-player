package com.example.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeckoEnginePolicyTest {
    @Test fun androidSevenUsesLegacyWithoutInitializingGecko() {
        assertFalse(GeckoEnginePolicy.supported(24))
        assertFalse(GeckoEnginePolicy.supported(25))
    }
    @Test fun supportedAndroidVersionsCanUseGecko() {
        assertTrue(GeckoEnginePolicy.supported(26))
        assertTrue(GeckoEnginePolicy.supported(35))
    }
}
