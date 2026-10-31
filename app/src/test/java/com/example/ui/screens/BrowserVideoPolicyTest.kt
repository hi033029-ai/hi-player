package com.example.ui.screens
import org.junit.Assert.*
import org.junit.Test
class BrowserVideoPolicyTest {
    @Test fun knownAdvertisingHostsAndMediaPathsAreRejected() {
        listOf("https://ads.example.test/main.mp4", "https://cdn.doubleclick.net/movie.mp4", "https://cdn.example.test/ads/master.m3u8", "https://video.example.test/preroll.mp4", "https://video.example.test/media?adtag=123", "https://example.test/vast/stream.mpd").forEach { assertTrue(it, BrowserVideoPolicy.isAdUrl(it)) }
    }
    @Test fun realCdnAndWordsContainingAdAreNotBlocked() {
        listOf("https://cdn.example.test/download/movie.mp4", "https://example.test/shadow/master.m3u8?token=123", "https://cdn.example.test/movie.mkv").forEach { assertFalse(it, BrowserVideoPolicy.isAdUrl(it)) }
    }
    @Test fun unsolicitedOrRedirectedStreamsNeverOfferPlayerNavigation() {
        assertFalse(BrowserVideoPolicy.offerNavigation(false, false, false))
        assertFalse(BrowserVideoPolicy.offerNavigation(true, true, false))
        assertFalse(BrowserVideoPolicy.offerNavigation(true, false, true))
        assertTrue(BrowserVideoPolicy.offerNavigation(true, false, false))
    }
    @Test fun bridgeHandsOffOnlyUserStartedNonAdVideos() {
        val reports = mutableListOf<String>()
        val handoffs = mutableListOf<String?>()
        val bridge = HiBrowserVideoBridge(onReport = { reports += it }, onAutoPlay = { handoffs += it; true })
        assertTrue(bridge.videoStarted("https://cdn.example.test/movie.mp4", true, false))
        assertFalse(bridge.videoStarted("https://ads.example.test/ad.mp4", true, false))
        assertFalse(bridge.videoStarted("https://cdn.example.test/movie.mp4", false, false))
        // blob:/MSE players report no direct source; the native side picks the page's stream.
        assertTrue(bridge.videoStarted("", true, false))
        assertEquals(listOf<String?>("https://cdn.example.test/movie.mp4", null), handoffs)
        bridge.reportVideo("https://ads.example.test/ad.mp4")
        bridge.reportVideo("https://cdn.example.test/movie.mp4")
        assertEquals(listOf("https://cdn.example.test/movie.mp4", "https://cdn.example.test/movie.mp4"), reports)
    }
    @Test fun bridgeWithoutAutoPlayNeverHandsOff() {
        val bridge = HiBrowserVideoBridge({})
        assertFalse(bridge.videoStarted("https://cdn.example.test/movie.mp4", true, false))
    }
    @Test fun detectionScriptNeedsTrustedGestureLongVideoAndNoAdContainer() {
        assertTrue(HI_BROWSER_VIDEO_HOOK_SCRIPT.contains("videoStarted("))
        assertFalse(HI_BROWSER_VIDEO_HOOK_SCRIPT.contains(".pause("))
        assertTrue(HI_BROWSER_VIDEO_HOOK_SCRIPT.contains("e.isTrusted"))
        assertTrue(HI_BROWSER_VIDEO_HOOK_SCRIPT.contains("MIN_DURATION=45"))
        assertTrue(HI_BROWSER_VIDEO_HOOK_SCRIPT.contains("inAd(v)"))
    }
}
