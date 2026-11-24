package com.example.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PlaybackEventFailureTest {
    @Test fun lateNativeEventFailureReachesRecoveryAfterPlaybackStarts() {
        val failure = IllegalStateException("decoder unavailable")
        var delivered: Throwable? = null
        var samples = 0
        repeat(301) { index ->
            dispatchPlaybackEvent({ delivered = it }) {
                if (index == 300) throw failure
                samples++
            }
        }
        assertEquals(300, samples)
        assertSame(failure, delivered)
    }
}
