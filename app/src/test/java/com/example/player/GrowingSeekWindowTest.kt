package com.example.player

import org.junit.Assert.assertEquals
import org.junit.Test

class GrowingSeekWindowTest {
    @Test fun limitFollowsVerifiedBufferedMediaAndAllowsRewind() {
        val window = GrowingSeekWindow()
        window.observe(10_000, 40_000, ready = true, seekable = true)
        assertEquals(40_000L, window.clamp(90_000, 120_000, true, true, 10_000))
        assertEquals(5_000L, window.clamp(5_000, 120_000, true, true, 10_000))
        window.observe(5_000, 20_000, ready = true, seekable = true)
        assertEquals(40_000L, window.bufferedThroughMs)
        window.observe(20_000, 60_000, ready = true, seekable = true)
        assertEquals(60_000L, window.clamp(90_000, 120_000, true, true, 20_000))
    }

    @Test fun unknownIndexAndDownloadCompletionAreHandledSafely() {
        val window = GrowingSeekWindow()
        window.observe(10_000, 100_000, ready = false, seekable = true)
        window.observe(10_000, 100_000, ready = true, seekable = false)
        assertEquals(0L, window.bufferedThroughMs)
        assertEquals(10_000L, window.clamp(80_000, 120_000, true, false, 10_000))
        assertEquals(120_000L, window.clamp(150_000, 120_000, false, true, 10_000))
        window.reset()
        assertEquals(0L, window.bufferedThroughMs)
    }
}
