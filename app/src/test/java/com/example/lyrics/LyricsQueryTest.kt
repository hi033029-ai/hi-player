package com.example.lyrics

import org.junit.Assert.*
import org.junit.Test

class LyricsQueryTest {
    @Test fun extractsArtistAndTitleFromDownloadedFilename() {
        assertEquals(LyricsQuery("Yellow", "Coldplay"), LyricsQuery.from("Coldplay - Yellow (Official Video).mp3", "Unknown Artist"))
    }
    @Test fun knownArtistIsNotOverwrittenByTitleHyphen() {
        assertEquals(LyricsQuery("Part One - Part Two", "Artist"), LyricsQuery.from("Part One - Part Two", "Artist"))
    }
    @Test fun unicodeTitlesRemainSearchable() {
        val q = LyricsQuery.from("हिंदी गीत", "कलाकार")
        assertEquals(1.0, q.score("हिंदी गीत", "कलाकार", 180000, 180.0), 0.001)
        assertTrue(LyricsQuery.normalize("أغنية عربية").contains("أغنية"))
    }
    @Test fun wrongSongArtistAndDurationAreRejected() {
        val q = LyricsQuery("Yellow", "Coldplay")
        assertEquals(0.0, q.score("Other Song", "Coldplay", 267000, 267.0), 0.001)
        assertEquals(0.0, q.score("Yellow", "Unrelated Singer", 267000, 267.0), 0.001)
        assertEquals(0.0, q.score("Yellow", "Coldplay", 267000, 30.0), 0.001)
    }
    @Test fun unknownArtistSearchStillRequiresTitleMatch() {
        val q = LyricsQuery.from("Yellow.mp3", "<unknown>")
        assertEquals("", q.artist)
        assertTrue(q.score("Yellow", "Coldplay", 0, 267.0) >= 0.6)
        assertEquals(0.0, q.score("Different", "Coldplay", 0, 267.0), 0.001)
    }
}
