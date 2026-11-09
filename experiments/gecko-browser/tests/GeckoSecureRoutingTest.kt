package com.example.browser.gecko

import org.junit.Assert.*
import org.junit.Test

class GeckoSecureRoutingTest {
    private val tabs = mutableMapOf(1 to false, 2 to true)
    private val saved = mutableSetOf<String>()
    private val opened = mutableListOf<String>()
    private var succeeds = true
    private val routing = GeckoSecureRouting(
        { tabs[it] }, { com.example.auth.SecureWebsitePolicy.originKey(it) in saved },
        { com.example.auth.SecureWebsitePolicy.originKey(it)?.let(saved::add) },
        { com.example.auth.SecureWebsitePolicy.originKey(it)?.let(saved::remove) },
        { if (succeeds) opened.add(it); succeeds },
        { if (succeeds) opened.add(it); succeeds },
    )

    @Test fun signInRequiresExplicitConfirmationAndPersistsOnlyOrigin() {
        routing.decision(1, GeckoNavigationDecision.SecureSignIn("https://example.org/path?x=secret"))
        assertTrue(opened.isEmpty())
        routing.confirm()
        assertEquals(listOf("https://example.org/path?x=secret"), opened)
        assertEquals(setOf("https://example.org"), saved)
        assertEquals("https://example.org/path?x=secret", routing.route(1))
        assertTrue(routing.policy(false).decide("https://example.org/next", "", false) is GeckoNavigationDecision.SecureSite)
    }
    @Test fun privateRoutingIsMemoryOnlyAndCanBeCleared() {
        routing.decision(2, GeckoNavigationDecision.SecureSite("https://private.example/path", false))
        assertTrue(opened.isEmpty())
        routing.confirm()
        assertTrue(saved.isEmpty())
        assertTrue(routing.policy(true).decide("https://private.example/", "", true) is GeckoNavigationDecision.SecureSite)
        assertEquals(GeckoNavigationDecision.Allow, routing.policy(false).decide("https://private.example/", "", true))
        routing.clearPrivateRouting()
        assertNull(routing.route(2))
        assertEquals(GeckoNavigationDecision.Allow, routing.policy(true).decide("https://private.example/", "", true))
    }
    @Test fun staleClosedTabCannotLaunchAndPruneDismisses() {
        routing.decision(1, GeckoNavigationDecision.SecureSignIn("https://example.org/"))
        tabs.remove(1)
        routing.confirm()
        assertTrue(opened.isEmpty())
        routing.decision(2, GeckoNavigationDecision.SecureSignIn("https://example.org/"))
        routing.prune(emptySet())
        assertNull(routing.pending)
    }
    @Test fun failedLaunchDoesNotRecordOrPretendSuccess() {
        succeeds = false
        routing.decision(1, GeckoNavigationDecision.SecureSignIn("https://example.org/"))
        routing.confirm()
        assertTrue(routing.failure)
        assertTrue(saved.isEmpty())
        assertNull(routing.route(1))
    }
    @Test fun googleSignInIsAllowedOnlyInTheExplicitRegularHelperTab() {
        val google = "https://accounts.google.com/AccountChooser"
        assertTrue(routing.policy(false).decide(google, "https://example.org/", false) is GeckoNavigationDecision.SecureSignIn)
        routing.allowGoogleSignInInBrowser(1)
        assertEquals(GeckoNavigationDecision.Allow, routing.policyForTab(1, false).decide(google, "https://example.org/", false))
        assertTrue(routing.policyForTab(2, true).decide(google, "https://example.org/", false) is GeckoNavigationDecision.SecureSignIn)
        routing.leaveTab(1)
        assertTrue(routing.policyForTab(1, false).decide(google, "https://example.org/", false) is GeckoNavigationDecision.SecureSignIn)
    }
    @Test fun rejectsCredentialUrlsAndUnknownSchemes() {
        routing.decision(1, GeckoNavigationDecision.SecureSignIn("https://user:pass@example.org/"))
        assertNull(routing.pending)
        routing.decision(1, GeckoNavigationDecision.ExternalLink("intent://bad"))
        assertNull(routing.pending)
        routing.decision(1, GeckoNavigationDecision.ExternalLink("tel:123"))
        assertTrue(opened.isEmpty())
        routing.confirm()
        assertEquals(listOf("tel:123"), opened)
    }
    @Test fun closingLastPrivateTabForgetsItsOrigins() {
        routing.decision(2, GeckoNavigationDecision.SecureSignIn("https://private.example/"))
        routing.confirm()
        tabs.remove(2)
        routing.prune(tabs.keys)
        assertNull(routing.route(2))
        tabs[3] = true
        assertEquals(GeckoNavigationDecision.Allow, routing.policy(true).decide("https://private.example/", "", true))
    }
    @Test fun explicitUseHiBrowserForgetsOnlyThatOrigin() {
        routing.decision(1, GeckoNavigationDecision.SecureSite("https://example.org/a", true))
        routing.confirm()
        routing.useHiBrowser(1)
        assertTrue(saved.isEmpty())
        assertNull(routing.route(1))
    }
}
