package com.example.auth
import org.junit.Assert.*
import org.junit.Test
class SecureWebSessionsTest {
    @Test fun onlyExactGoogleAuthHostsRouteToSecureAuthentication() {
        assertTrue(SecureWebSessions.isGoogleAuthentication("https://accounts.google.com/o/oauth2/v2/auth"))
        assertFalse(SecureWebSessions.isGoogleAuthentication("https://accounts.google.com.evil.test/login"))
        assertFalse(SecureWebSessions.isGoogleAuthentication("http://accounts.google.com/"))
    }
    @Test fun onlyHttpsPagesWithoutEmbeddedCredentialsCanBeOpened() {
        assertTrue(SecureWebSessions.validPage("https://www.youtube.com/"))
        assertFalse(SecureWebSessions.validPage("javascript:alert(1)"))
        assertFalse(SecureWebSessions.validPage("http://example.test/"))
        assertFalse(SecureWebSessions.validPage("https://user:pass@example.test/"))
    }
    @Test fun restartRpFlowRatherThanPretendingWebviewStateCanBeCopied() {
        assertEquals("https://example.test/login", SecureWebSessions.entryForGoogleAuth("https://example.test/login"))
        assertEquals(SecureWebSessions.GOOGLE_ACCOUNT, SecureWebSessions.entryForGoogleAuth("https://accounts.google.com/o/oauth2/auth?state=private"))
        assertEquals(SecureWebSessions.GOOGLE_ACCOUNT, SecureWebSessions.entryForGoogleAuth(""))
    }
}
