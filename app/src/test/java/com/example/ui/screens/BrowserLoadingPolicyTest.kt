package com.example.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserLoadingPolicyTest {
    @Test fun selectsOnlyKnownMobileYoutubeRouteStructures() {
        assertEquals(BrowserPlaceholderLayout.YOUTUBE_FEED, BrowserLoadingPolicy.placeholder("https://m.youtube.com/", false))
        assertEquals(BrowserPlaceholderLayout.YOUTUBE_WATCH, BrowserLoadingPolicy.placeholder("https://www.youtube.com/watch?v=example", false))
        assertEquals(BrowserPlaceholderLayout.NONE, BrowserLoadingPolicy.placeholder("https://www.youtube.com/watch?v=example", true))
        assertEquals(BrowserPlaceholderLayout.NONE, BrowserLoadingPolicy.placeholder("https://m.youtube.com/shorts/example", false))
    }
    @Test fun unknownAndLookalikeSitesNeverReceiveAGuessedYoutubeSkeleton() {
        listOf("https://example.org", "https://youtube.com.attacker.invalid/watch", "https://notyoutube.com/",
            "https://youtube.com@attacker.invalid/watch", "file:///youtube.com", "not a url").forEach {
            assertEquals(it, BrowserPlaceholderLayout.NONE, BrowserLoadingPolicy.placeholder(it, false))
        }
    }
}
