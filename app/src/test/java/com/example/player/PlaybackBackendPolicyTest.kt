package com.example.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackBackendPolicyTest {
    @Test fun growingMatroskaUsesByteAddressedMedia3Extractor() {
        assertFalse(PlaybackBackendPolicy.shouldUseVlc("file", "movie.mkv.part", null, growing = true))
        assertFalse(PlaybackBackendPolicy.shouldUseVlc("content", "movie.mkv", "video/x-matroska", growing = true))
    }

    @Test fun explicitColorManagementUsesSingleMedia3Processor() {
        assertFalse(PlaybackBackendPolicy.shouldUseVlc("file", "movie.mkv", null, colorManaged = true))
    }

    @Test
    fun `local Matroska and pending aliases use LibVLC`() {
        assertTrue(PlaybackBackendPolicy.shouldUseVlc("file", "movie.mkv", null))
        assertTrue(PlaybackBackendPolicy.shouldUseVlc("content", "movie.mkv.part", "application/octet-stream"))
        assertTrue(PlaybackBackendPolicy.shouldUseVlc("content", "download", "video/x-matroska"))
    }

    @Test
    fun `network and ordinary local formats remain on Media3`() {
        assertFalse(PlaybackBackendPolicy.shouldUseVlc("https", "movie.mkv", "video/x-matroska"))
        assertFalse(PlaybackBackendPolicy.shouldUseVlc("file", "movie.mp4", "video/mp4"))
        assertFalse(PlaybackBackendPolicy.shouldUseVlc("content", "movie.m3u8", "application/x-mpegURL"))
    }
}
