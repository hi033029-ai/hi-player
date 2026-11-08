package com.example.browser.gecko
import org.junit.Assert.*
import org.junit.Test
class GeckoGoogleSignInTest {
    @Test fun onlyExactGoogleAuthHostsAreSignInPages() {
        assertTrue(GeckoGoogleSignIn.isAuthPage("https://accounts.google.com/o/oauth2/v2/auth?client_id=x"))
        assertTrue(GeckoGoogleSignIn.isAuthPage("https://accounts.youtube.com/accounts/SetSID"))
        assertFalse(GeckoGoogleSignIn.isAuthPage("https://accounts.google.com.evil.test/"))
        assertFalse(GeckoGoogleSignIn.isAuthPage("http://accounts.google.com/"))
        assertFalse(GeckoGoogleSignIn.isAuthPage("https://user:pw@accounts.google.com/"))
    }
    @Test fun rejectionPageIsDetected() {
        assertTrue(GeckoGoogleSignIn.isRejected("https://accounts.google.com/v3/signin/rejected?TL=abc"))
        assertTrue(GeckoGoogleSignIn.isRejected("https://accounts.google.com/signin/rejected"))
        assertFalse(GeckoGoogleSignIn.isRejected("https://accounts.google.com/v3/signin/identifier"))
        assertFalse(GeckoGoogleSignIn.isRejected("https://example.test/signin/rejected"))
    }
    @Test fun entryUrlPreselectsOnlyAValidEmail() {
        val withEmail = GeckoGoogleSignIn.entryUrl("person@example.com")
        assertTrue(withEmail.startsWith("https://accounts.google.com/AccountChooser?continue=https%3A%2F%2Fmyaccount.google.com%2F"))
        assertTrue(withEmail.endsWith("&Email=person%40example.com"))
        assertFalse(GeckoGoogleSignIn.entryUrl(null).contains("Email="))
        assertFalse(GeckoGoogleSignIn.entryUrl("not an email").contains("Email="))
        assertFalse(GeckoGoogleSignIn.entryUrl("a@b@c").contains("Email="))
    }
    @Test fun flowReturnsToStartingTabOnlyAfterCompletion() {
        val flow = GeckoGoogleSignInFlow()
        flow.begin(returnTo = 1, signInTab = 2)
        assertNull(flow.onUrl(2, "https://accounts.google.com/v3/signin/challenge/pwd"))
        assertNull(flow.onUrl(1, "https://myaccount.google.com/"))
        assertEquals(1, flow.onUrl(2, "https://myaccount.google.com/?pli=1"))
        assertNull(flow.signInTabId)
        assertNotNull(flow.notice)
        assertNull(flow.onUrl(2, "https://myaccount.google.com/"))
    }
    @Test fun closedSignInTabCancelsFlow() {
        val flow = GeckoGoogleSignInFlow()
        flow.begin(1, 2)
        flow.forgetMissing(setOf(1, 2))
        assertEquals(2, flow.signInTabId)
        flow.forgetMissing(setOf(1))
        assertNull(flow.signInTabId)
    }
}
