package com.example.browser.gecko

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeckoPasswordAutofillTest {
    @Test fun requiresOptInAndARegularHttpsTab() {
        val tab = GeckoTabSnapshot(1, false, "https://example.org/login")
        assertTrue(GeckoPasswordAutofill.shouldEnable(true, tab))
        assertFalse(GeckoPasswordAutofill.shouldEnable(false, tab))
        assertFalse(GeckoPasswordAutofill.shouldEnable(true, tab.copy(privateTab = true)))
        assertFalse(GeckoPasswordAutofill.shouldEnable(true, tab.copy(url = "http://example.org/login")))
        assertFalse(GeckoPasswordAutofill.shouldEnable(true, tab.copy(error = GeckoPageError.SECURITY)))
    }
}
