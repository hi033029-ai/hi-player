package com.example.ui.screens

import org.junit.Assert.*
import org.junit.Test

class BrowserQuickNavigationTest {
    @Test fun navigationExpiresAfterFiveSecondsWithoutActivity() {
        assertEquals(5_000L, BrowserNavigationPolicy.remainingIdleMillis(10_000L, 10_000L))
        assertEquals(1L, BrowserNavigationPolicy.remainingIdleMillis(14_999L, 10_000L))
        assertEquals(0L, BrowserNavigationPolicy.remainingIdleMillis(15_000L, 10_000L))
        assertEquals(0L, BrowserNavigationPolicy.remainingIdleMillis(99_000L, 10_000L))
    }
    @Test fun syntheticAndHoverEventsCannotKeepNavigationOpen() {
        assertFalse(BrowserNavigationPolicy.isUserActivity(false, false, true))
        assertFalse(BrowserNavigationPolicy.isUserActivity(true, true, false))
        assertTrue(BrowserNavigationPolicy.isUserActivity(true, false, false))
        assertTrue(BrowserNavigationPolicy.isUserActivity(true, true, true))
        assertTrue(BrowserNavigationPolicy.isUserActivity(false, true, false))
    }
    @Test fun onlyIntentionalUpwardSwipeRevealsNavigation() {
        assertTrue(BrowserNavigationPolicy.isUpSwipe(60f, 10f, 44f))
        assertFalse(BrowserNavigationPolicy.isUpSwipe(20f, 0f, 44f))
        assertFalse(BrowserNavigationPolicy.isUpSwipe(-60f, 0f, 44f))
        assertFalse(BrowserNavigationPolicy.isUpSwipe(60f, 60f, 44f))
    }
    @Test fun suggestionsMatchTitlesAndUrlsAndKeepRecency() {
        val items = listOf(OrbitBrowserEntry("Music", "https://new.example/music"), OrbitBrowserEntry("Video", "https://video.example"))
        assertEquals(items.take(1), BrowserHistoryPolicy.suggestions(items, "music"))
        assertEquals(items.takeLast(1), BrowserHistoryPolicy.suggestions(items, "video.example"))
        assertEquals(items, BrowserHistoryPolicy.suggestions(items, ""))
    }
    @Test fun suggestionsRejectNonWebAndCredentialUrlsAndDeduplicate() {
        val valid = OrbitBrowserEntry("New site", "https://new.example/")
        val items = listOf(valid, valid, OrbitBrowserEntry("Local", "file:///secret"), OrbitBrowserEntry("Auth", "https://user:password@example.com"))
        assertEquals(listOf(valid), BrowserHistoryPolicy.suggestions(items, ""))
    }
    @Test fun suggestionsAreBoundedAndNeverInventDomainReplacements() {
        val items = (1..10).map { OrbitBrowserEntry("Site $it", "https://site$it.example") }
        assertEquals(4, BrowserHistoryPolicy.suggestions(items, "").size)
        assertTrue(BrowserHistoryPolicy.suggestions(items, "unknown.example").isEmpty())
    }
}
