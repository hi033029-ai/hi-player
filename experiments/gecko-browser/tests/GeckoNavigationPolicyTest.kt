package com.example.browser.gecko

import org.junit.Assert.*
import org.junit.Test

class GeckoNavigationPolicyTest {
    private val policy = GeckoNavigationPolicy(
        isGoogleAuthentication = { it.startsWith("https://accounts.google.com/") },
        secureEntryFor = { if (it.startsWith("https://example.org/")) it else "https://accounts.google.com/" },
        prefersSecureSite = { it.startsWith("https://secure.example/") },
    )
    @Test fun webPagesRemainInsideBrowser() {
        assertEquals(GeckoNavigationDecision.Allow, policy.decide("https://example.org/a", "", true))
        assertEquals(GeckoNavigationDecision.Allow, policy.decide("http://example.org/a", "", false))
    }
    @Test fun googleSignInRequestsSecureBrowserFlow() {
        assertEquals(GeckoNavigationDecision.SecureSignIn("https://example.org/page"),
            policy.decide("https://accounts.google.com/signin", "https://example.org/page", true))
    }
    private val inBrowser = GeckoNavigationPolicy(
        isGoogleAuthentication = { it.startsWith("https://accounts.google.com/") },
        secureEntryFor = { if (it.startsWith("https://example.org/")) it else "https://accounts.google.com/" },
        prefersSecureSite = { it.startsWith("https://accounts.google.com/") || it.startsWith("https://secure.example/") },
        googleSignInInBrowser = { true },
    )
    @Test fun inBrowserGoogleSignInStaysInHiBrowserEvenForSavedOrigin() {
        assertEquals(GeckoNavigationDecision.Allow,
            inBrowser.decide("https://accounts.google.com/o/oauth2/v2/auth?client_id=x", "https://example.org/page", false))
        assertEquals(GeckoNavigationDecision.SecureSite("https://secure.example/a", true),
            inBrowser.decide("https://secure.example/a", "", true))
    }
    @Test fun googleRejectionFallsBackToDeviceBrowser() {
        assertEquals(GeckoNavigationDecision.SecureSignIn("https://example.org/page"),
            inBrowser.decide("https://accounts.google.com/v3/signin/rejected?TL=x", "https://example.org/page", false))
    }
    @Test fun savedSecureSiteRequiresUserGestureBeforeExternalLaunch() {
        assertEquals(GeckoNavigationDecision.SecureSite("https://secure.example/a", false),
            policy.decide("https://secure.example/a", "", false))
        assertEquals(GeckoNavigationDecision.SecureSite("https://secure.example/a", true),
            policy.decide("https://secure.example/a", "", true))
    }
    @Test fun externalLinksRequireTapAndOtherSchemesAreBlocked() {
        assertEquals(GeckoNavigationDecision.Block, policy.decide("tel:123", "", false))
        assertEquals(GeckoNavigationDecision.ExternalLink("tel:123"), policy.decide("tel:123", "", true))
        assertEquals(GeckoNavigationDecision.Block, policy.decide("intent://example", "", true))
        assertEquals(GeckoNavigationDecision.Block, policy.decide("javascript:alert(1)", "", true))
    }
    @Test fun malformedUrlIsBlocked() {
        assertEquals(GeckoNavigationDecision.Block, policy.decide("not a url", "", true))
    }
}
