package com.example.lyrics

import org.junit.Assert.*
import org.junit.Test

class LyricsTextTest {
    @Test fun plainLyricsStayVisibleWithoutFabricatedTimestamps() {
        val parsed = LyricsText.parse("[ar:Artist]\nFirst line\n\nسطر عربي\nहिंदी गीत")
        assertTrue(parsed.timed.isEmpty())
        assertEquals(listOf("First line", "سطر عربي", "हिंदी गीत"), parsed.displayLines)
    }
    @Test fun parsesSecondsAndOneTwoThreeDigitFractions() {
        val parsed = LyricsText.parse("[00:01]one\n[00:02.5]two\n[00:03.25]three\n[00:04.125]four")
        assertEquals(listOf(1000L, 2500L, 3250L, 4125L), parsed.timed.map { it.first })
    }
    @Test fun expandsRepeatedTimestampsSortsAndAppliesOffset() {
        val parsed = LyricsText.parse("\uFEFF[offset:-500]\r\n[01:02.00][00:02.00]Chorus\r\n[00:00.20]Intro")
        assertEquals(listOf(0L to "Intro", 1500L to "Chorus", 61500L to "Chorus"), parsed.timed)
    }
    @Test fun emptyAndMetadataOnlyHaveNoDisplayLines() {
        assertTrue(LyricsText.parse("").displayLines.isEmpty())
        assertTrue(LyricsText.parse("[ti:Title]\n[ar:Artist]").displayLines.isEmpty())
    }
}
