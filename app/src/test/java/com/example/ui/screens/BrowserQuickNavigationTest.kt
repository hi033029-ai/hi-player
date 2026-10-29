package com.example.ui.screens

import org.junit.Assert.*
import org.junit.Test

class BrowserQuickNavigationTest {
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
