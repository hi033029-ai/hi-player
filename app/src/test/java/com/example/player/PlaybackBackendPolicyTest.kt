package com.example.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackBackendPolicyTest {
    @Test
    fun `local Matroska and pending aliases use LibVLC`() {
        assertTrue(PlaybackBackendPolicy.shouldUseVlc("file", "movie.mkv", null))
        assertTrue(PlaybackBackendPolicy.shouldUseVlc("content", "movie.mkv.part", "application/octet-stream"))
        assertTrue(PlaybackBackendPolicy.shouldUseVlc("content", "download", "video/x-matroska"))
    }

    @Test
    fun `LibVLC color presets install a real filter only when explicitly enabled`() {
        assertTrue(vlcColorAdjustOptions(ColorPresets.HDR_DISABLED, enhancementEnabled = true).isEmpty())
        assertTrue(vlcColorAdjustOptions(ColorPresets.VIVID_HDR, enhancementEnabled = false).isEmpty())

        val options = vlcColorAdjustOptions(ColorPresets.VIVID_HDR, enhancementEnabled = true)
        assertTrue(":video-filter=adjust" in options)
        assertTrue(options.any { it.startsWith(":brightness=") })
        assertTrue(options.any { it.startsWith(":contrast=") })
        assertTrue(options.any { it.startsWith(":saturation=") })
    }

    @Test
    fun `network and ordinary local formats remain on Media3`() {
        assertFalse(PlaybackBackendPolicy.shouldUseVlc("https", "movie.mkv", "video/x-matroska"))
        assertFalse(PlaybackBackendPolicy.shouldUseVlc("file", "movie.mp4", "video/mp4"))
        assertFalse(PlaybackBackendPolicy.shouldUseVlc("content", "movie.m3u8", "application/x-mpegURL"))
    }
}
