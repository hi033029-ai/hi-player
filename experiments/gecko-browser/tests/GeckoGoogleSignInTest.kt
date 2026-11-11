package com.example.browser.gecko

import org.junit.Assert.*
import org.junit.Test

class GeckoGoogleSignInTest {
    @Test fun entryUrlPreselectsOnlyAValidEmail() {
        val withEmail = GeckoGoogleSignIn.entryUrl("person@example.com")
        assertTrue(withEmail.startsWith("https://accounts.google.com/AccountChooser?continue=https%3A%2F%2Fmyaccount.google.com%2F"))
        assertTrue(withEmail.endsWith("&Email=person%40example.com"))
        assertFalse(GeckoGoogleSignIn.entryUrl(null).contains("Email="))
        assertFalse(GeckoGoogleSignIn.entryUrl("not an email").contains("Email="))
        assertFalse(GeckoGoogleSignIn.entryUrl("a@b@c").contains("Email="))
    }
}
