package com.example.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsTimingTest {
    private val lines = listOf(1000L to "one", 2000L to "two", 2000L to "duplicate", 3500L to "three")

    @Test fun selectsAtTimestampAndAfterSeekInEitherDirection() {
        assertEquals(-1, LyricsTiming.activeLineIndex(lines, 999))
        assertEquals(0, LyricsTiming.activeLineIndex(lines, 1000))
        assertEquals(2, LyricsTiming.activeLineIndex(lines, 2000))
        assertEquals(3, LyricsTiming.activeLineIndex(lines, 9000))
        assertEquals(0, LyricsTiming.activeLineIndex(lines, 1500))
        assertEquals(-1, LyricsTiming.activeLineIndex(emptyList(), 1000))
    }
}
