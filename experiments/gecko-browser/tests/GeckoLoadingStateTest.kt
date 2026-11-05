package com.example.browser.gecko

import org.junit.Assert.*
import org.junit.Test

class GeckoLoadingStateTest {
    private val initial = GeckoTabSnapshot(1, false)

    @Test fun loadingBeginsImmediatelyAndUsesRealClampedProgress() {
        val start = GeckoLoadingState.start(initial, "https://example.org")
        assertTrue(start.loading)
        assertEquals(0, start.progress)
        assertEquals(37, GeckoLoadingState.progress(start, 37).progress)
        assertEquals(100, GeckoLoadingState.progress(start, 120).progress)
        assertEquals(0, GeckoLoadingState.progress(start, -1).progress)
    }
    @Test fun locationsUpdateForRedirectsAndSinglePageNavigation() {
        val start = GeckoLoadingState.start(initial, "https://example.org")
        val moved = GeckoLoadingState.location(start, "https://example.org/next")
        assertEquals("https://example.org/next", moved.url)
        assertSame(moved, GeckoLoadingState.location(moved, null))
    }
    @Test fun failuresEndLoadingWithoutShowingErrorDetails() {
        val start = GeckoLoadingState.start(initial, "https://example.org")
        val failed = GeckoLoadingState.error(start, GeckoPageError.SECURITY)
        assertFalse(failed.loading)
        assertEquals(GeckoPageError.SECURITY, failed.error)
        assertTrue(failed.loadFailed)
        assertEquals(GeckoPageError.SECURITY, GeckoLoadingState.stop(failed, false).error)
    }
    @Test fun successfulStopDoesNotEraseAReportedError() {
        val failed = GeckoLoadingState.error(GeckoLoadingState.start(initial, "https://example.org"), GeckoPageError.NETWORK)
        assertEquals(GeckoPageError.NETWORK, GeckoLoadingState.stop(failed, true).error)
    }
    @Test fun freshNavigationClearsOldFailure() {
        val failed = GeckoLoadingState.error(initial, GeckoPageError.RENDERER)
        val next = GeckoLoadingState.start(failed, "https://example.org")
        assertNull(next.error)
        assertTrue(next.loading)
    }
    @Test fun interceptedNavigationStopsTheLoadingIndicator() {
        val state = GeckoLoadingState.cancel(GeckoLoadingState.start(initial, "https://example.org"))
        assertFalse(state.loading)
        assertNull(state.error)
    }
    @Test fun stopWithoutSpecificFailureIsNotReportedAsSuccess() {
        val stopped = GeckoLoadingState.stop(GeckoLoadingState.start(initial, "https://example.org"), false)
        assertFalse(stopped.loading)
        assertTrue(stopped.loadFailed)
        assertEquals(GeckoPageError.CONTENT, stopped.error)
    }
}
