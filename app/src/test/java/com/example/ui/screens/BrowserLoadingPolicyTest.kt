package com.example.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserLoadingPolicyTest {
    @Test fun selectsOnlyKnownMobileYoutubeRouteStructures() {
        assertEquals(BrowserPlaceholderLayout.YOUTUBE_FEED, BrowserLoadingPolicy.placeholder("https://m.youtube.com/", false))
        assertEquals(BrowserPlaceholderLayout.YOUTUBE_WATCH, BrowserLoadingPolicy.placeholder("https://www.youtube.com/watch?v=example", false))
        assertEquals(BrowserPlaceholderLayout.NONE, BrowserLoadingPolicy.placeholder("https://www.youtube.com/watch?v=example", true))
        assertEquals(BrowserPlaceholderLayout.VERTICAL_VIDEO, BrowserLoadingPolicy.placeholder("https://m.youtube.com/shorts/example", false))
    }
    @Test fun mapsMobileSiteLayoutsAndLeavesAuthenticationPagesNeutral() {
        val routes = mapOf(
            "m.facebook.com/" to BrowserPlaceholderLayout.SOCIAL_FEED,
            "facebook.com/reel/12" to BrowserPlaceholderLayout.VERTICAL_VIDEO,
            "instagram.com/" to BrowserPlaceholderLayout.PHOTO_FEED,
            "www.instagram.com/reels/12" to BrowserPlaceholderLayout.VERTICAL_VIDEO,
            "tiktok.com/@user/video/12" to BrowserPlaceholderLayout.VERTICAL_VIDEO,
            "katmoviehd.example/" to BrowserPlaceholderLayout.POSTER_GRID,
            "4khdhub.example/" to BrowserPlaceholderLayout.POSTER_GRID,
            "uhdmovies.example/" to BrowserPlaceholderLayout.POSTER_GRID,
            "7starhd.example/" to BrowserPlaceholderLayout.CATALOG_LIST,
            "7starhd.example.evil.test/" to BrowserPlaceholderLayout.NONE,
            "m.facebook.com/login/" to BrowserPlaceholderLayout.NONE,
        )
        for ((route, layout) in routes) assertEquals(route, layout, BrowserLoadingPolicy.placeholder("https://$route", false))
    }

    @Test fun unknownAndLookalikeSitesNeverReceiveAGuessedYoutubeSkeleton() {
        listOf("https://example.org", "https://youtube.com.attacker.invalid/watch", "https://notyoutube.com/",
            "https://youtube.com@attacker.invalid/watch", "file:///youtube.com", "not a url").forEach {
            assertEquals(it, BrowserPlaceholderLayout.NONE, BrowserLoadingPolicy.placeholder(it, false))
        }
    }
}
