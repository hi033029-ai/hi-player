package com.example.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackBackendPolicyTest {
    @Test
    fun `growing MKV seeking never extrapolates outside verified cluster timestamps`() {
        val clusters = listOf(0L, 1_500_000L, 4_000_000L)
        assertNull(VlcGrowingSeekPolicy.clusterTimeMs(100L, emptyList()))
        assertEquals(0L, VlcGrowingSeekPolicy.clusterTimeMs(900L, clusters))
        assertEquals(1_500L, VlcGrowingSeekPolicy.clusterTimeMs(2_000L, clusters))
        assertEquals(4_000L, VlcGrowingSeekPolicy.clusterTimeMs(40_000L, clusters))
        assertEquals(0L, VlcGrowingSeekPolicy.clusterTimeMs(-1L, clusters))
    }

    @Test
    fun `index without an initial cluster does not invent time zero`() {
        val clusters = listOf(2_000_000L, 5_000_000L)
        assertNull(VlcGrowingSeekPolicy.clusterTimeMs(1_000L, clusters))
        assertEquals(2_000L, VlcGrowingSeekPolicy.clusterTimeMs(2_001L, clusters))
    }

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
