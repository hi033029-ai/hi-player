package com.example.browser.gecko

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeckoSitePopupExceptionsTest {
    @Test fun exceptionsAreTemporaryExactOriginAndTabScoped() {
        val exceptions = GeckoSitePopupExceptions<Any>()
        val tab = Any()
        val other = Any()
        assertFalse(exceptions.isAllowed(tab, "https://files.example/page"))
        assertTrue(exceptions.allow(tab, "https://files.example/page"))
        assertTrue(exceptions.isAllowed(tab, "https://FILES.example:443/next"))
        assertFalse(exceptions.isAllowed(other, "https://files.example/"))
        assertFalse(exceptions.isAllowed(tab, "https://other.files.example/"))
        assertFalse(exceptions.isAllowed(tab, "http://files.example/"))
        assertFalse(exceptions.isAllowed(tab, "https://files.example:8443/"))
        assertFalse(exceptions.isAllowed(tab, "https://files.example@evil.example/"))
        assertFalse(exceptions.allow(tab, "javascript:alert(1)"))
        assertTrue(exceptions.isAllowed(tab, "https://files.example/"))
        exceptions.revoke(tab)
        assertFalse(exceptions.isAllowed(tab, "https://files.example/"))
    }
    @Test fun adDestinationsStayBlockedEvenWithSitePopupException() {
        val exceptions = GeckoSitePopupExceptions<Any>()
        val tab = Any()
        assertTrue(exceptions.allow(tab, "https://files.example/"))
        val effectiveBlock = true && !exceptions.isAllowed(tab, "https://files.example/")
        assertTrue(GeckoPopupPolicy.allows(effectiveBlock, true, false))
        assertFalse(GeckoPopupPolicy.allows(effectiveBlock, true, true))
    }
}
