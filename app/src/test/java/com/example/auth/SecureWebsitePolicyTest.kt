package com.example.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SecureWebsitePolicyTest {
    @Test fun normalizesHttpsOriginAndDropsPathsQueriesAndFragments() {
        assertEquals("https://example.com", SecureWebsitePolicy.originKey("https://EXAMPLE.com:443/profile?token=not-stored#account"))
        assertEquals(SecureWebsitePolicy.originKey("https://example.com/first"), SecureWebsitePolicy.originKey("https://example.com/second"))
    }
    @Test fun rejectsInsecureAndCredentialBearingUrls() {
        for (url in listOf("http://example.com", "javascript:alert(1)", "file:///tmp/a", "https://user:secret@example.com", "https:///missing-host", "not a url", "https://example.com:99999")) {
            assertNull(url, SecureWebsitePolicy.originKey(url))
        }
    }
    @Test fun differentSitesSubdomainsAndPortsNeverShareRoutingPermission() {
        assertNotEquals(SecureWebsitePolicy.originKey("https://example.com"), SecureWebsitePolicy.originKey("https://other.example.com"))
        assertNotEquals(SecureWebsitePolicy.originKey("https://example.com"), SecureWebsitePolicy.originKey("https://example.com:8443"))
        assertNotEquals(SecureWebsitePolicy.originKey("https://example.com"), SecureWebsitePolicy.originKey("https://example.com.evil.invalid"))
    }
    @Test fun supportsNonDefaultHttpsPortWithoutMergingWithDefault() {
        assertEquals("https://example.com:8443", SecureWebsitePolicy.originKey("https://example.com:8443/path"))
    }
}
