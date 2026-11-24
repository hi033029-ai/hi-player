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
        assertEquals(GeckoNavigationDecision.SecureSignIn("https://accounts.google.com/signin", "https://example.org/page", userGesture = true),
            policy.decide("https://accounts.google.com/signin", "https://example.org/page", true))
    }
    private val googleSitePreferred = GeckoNavigationPolicy(
        isGoogleAuthentication = { it.startsWith("https://accounts.google.com/") },
        secureEntryFor = { if (it.startsWith("https://example.org/")) it else "https://accounts.google.com/" },
        prefersSecureSite = { it.startsWith("https://accounts.google.com/") || it.startsWith("https://secure.example/") },
    )
    @Test fun googleWebsiteAuthenticationAlwaysUsesSecureSignInRoute() {
        assertEquals(GeckoNavigationDecision.SecureSignIn("https://accounts.google.com/o/oauth2/v2/auth?client_id=x", "https://example.org/page"),
            googleSitePreferred.decide("https://accounts.google.com/o/oauth2/v2/auth?client_id=x", "https://example.org/page", false))
    }
    private val inBrowserGooglePolicy = GeckoNavigationPolicy(
        isGoogleAuthentication = { it.startsWith("https://accounts.google.com/") },
        secureEntryFor = { if (it.startsWith("https://example.org/")) it else "https://accounts.google.com/" },
        prefersSecureSite = { it.startsWith("https://secure.example/") },
        googleSignInInBrowser = true,
    )
    @Test fun inBrowserGoogleSignInStaysInGeckoUnlessRejected() {
        assertEquals(
            GeckoNavigationDecision.Allow,
            inBrowserGooglePolicy.decide("https://accounts.google.com/ServiceLogin?service=youtube", "https://m.youtube.com/", true),
        )
        assertEquals(
            GeckoNavigationDecision.SecureSignIn("https://accounts.google.com/v3/signin/rejected?TL=abc", "https://example.org/page", userGesture = false),
            inBrowserGooglePolicy.decide("https://accounts.google.com/v3/signin/rejected?TL=abc", "https://example.org/page", false),
        )
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
        assertEquals(GeckoNavigationDecision.Ignore, policy.decide("intent://example", "", true))
        assertEquals(GeckoNavigationDecision.Block, policy.decide("javascript:alert(1)", "", true))
    }
    @Test fun loginInBlankPopupRetainsItsOpenerAsTheReturnSite() {
        val request = "https://accounts.google.com/o/oauth2/v2/auth?client_id=x"
        assertEquals(GeckoNavigationDecision.SecureSignIn(request, "https://example.org/page"),
            policy.withPopupOpener("https://example.org/page").decide(request, "", false))
    }
    @Test fun promotionalAppLinksStayInBrowser() {
        assertEquals(GeckoNavigationDecision.Ignore, policy.decide("market://details?id=app.example", "https://example.org/a", true))
        assertEquals(GeckoNavigationDecision.Ignore, policy.decide("snssdk1233://aweme/detail/1", "https://example.org/a", false))
        assertEquals(GeckoNavigationDecision.Ignore, policy.decide("snssdk1340://aweme/detail/1", "https://www.tiktok.com/", false))
        assertEquals(GeckoNavigationDecision.Ignore, policy.decide("tiktok://feed", "https://www.tiktok.com/", true))
        assertEquals(GeckoNavigationDecision.Ignore, policy.decide("musically://feed", "https://www.tiktok.com/", false))
        assertEquals(GeckoNavigationDecision.Ignore, policy.decide("  snssdk-beta://feed  ", "https://www.tiktok.com/", false))
    }
    @Test fun mobileAppRelaysDoNotReplaceTheWebsite() {
        val source = "https://www.tiktok.com/"
        assertEquals(GeckoNavigationDecision.Ignore, policy.decide("https://snssdk1233.onelink.me/launch", source, true))
        assertEquals(GeckoNavigationDecision.Ignore, policy.decide("https://app-va.tiktokv.com/app", source, false))
        assertEquals(GeckoNavigationDecision.Ignore, policy.decide("https://app-va.tiktok.com/launch", source, false))
        assertEquals(GeckoNavigationDecision.Ignore, policy.decide("https://APP.TIKTOK.COM./launch", "", false))
        assertEquals(GeckoNavigationDecision.Ignore, policy.decide("https://app-m.tiktok.com/launch", source, false))
        assertEquals(GeckoNavigationDecision.Allow, policy.decide("https://www.tiktok.com/@view/video/1", source, true))
        assertEquals(GeckoNavigationDecision.Allow, policy.decide("https://app.tiktok.com.example.org/", source, false))
        assertEquals(GeckoNavigationDecision.Allow, policy.decide("https://app-va.tiktokv.com/app", "https://example.org/", true))
        assertEquals(GeckoNavigationDecision.Allow, policy.decide("https://snssdk1233.onelink.me.example.org/", source, true))
    }
    @Test fun automaticAuthenticationFromBlankTabIsIgnored() {
        assertEquals(GeckoNavigationDecision.Ignore, policy.decide("https://accounts.google.com/signin", "", false))
    }
    @Test fun malformedUrlIsBlocked() {
        assertEquals(GeckoNavigationDecision.Block, policy.decide("not a url", "", true))
    }
}
