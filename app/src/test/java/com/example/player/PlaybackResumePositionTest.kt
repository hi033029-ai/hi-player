package com.example.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackResumePositionTest {
    @Test fun finishedTrailerAndItsLastProgressSampleRestart() {
        assertEquals(0L, playbackResumePosition(94_000, 94_000, false))
        assertEquals(0L, playbackResumePosition(93_500, 94_000, false))
        assertEquals(0L, playbackResumePosition(100_000, 94_000, false))
    }

    @Test fun unfinishedFilmAndShortClipKeepTheirResumePoint() {
        assertEquals(60_000L, playbackResumePosition(60_000, 94_000, false))
        assertEquals(7_000L, playbackResumePosition(7_000, 8_000, false))
        assertEquals(0L, playbackResumePosition(7_900, 8_000, false))
    }

    @Test fun growingOrUnknownDurationNeverTurnsItsCurrentFrontierIntoTheBeginning() {
        assertEquals(94_000L, playbackResumePosition(94_000, 94_000, true))
        assertEquals(94_000L, playbackResumePosition(94_000, 0, false))
        assertEquals(0L, playbackResumePosition(-1, 94_000, false))
    }
}
