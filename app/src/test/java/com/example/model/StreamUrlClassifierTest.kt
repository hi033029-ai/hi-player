package com.example.model

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamUrlClassifierTest {

    @Test
    fun hlsManifestIsRoutedToNativeHlsPlayback() {
        val result = StreamUrlClassifier.classify("https://cdn.example.test/live/master.m3u8?token=abc")

        assertTrue(result.isPlayableInHiPlayer)
        assertEquals(StreamUrlKind.HLS, result.kind)
        assertEquals(MimeTypes.APPLICATION_M3U8, result.mimeType)
    }

    @Test
    fun dashManifestIsRoutedToNativeDashPlayback() {
        val result = StreamUrlClassifier.classify("https://cdn.example.test/video/manifest.mpd")

        assertTrue(result.isPlayableInHiPlayer)
        assertEquals(StreamUrlKind.DASH, result.kind)
        assertEquals(MimeTypes.APPLICATION_MPD, result.mimeType)
    }

    @Test
    fun progressiveUrlUsesContainerDetection() {
        val result = StreamUrlClassifier.classify("https://cdn.example.test/feature")

        assertTrue(result.isPlayableInHiPlayer)
        assertEquals(StreamUrlKind.PROGRESSIVE, result.kind)
        assertEquals(null, result.mimeType)
    }

    @Test
    fun providerPageIsRejectedBeforeMedia3ReceivesHtml() {
        val result = StreamUrlClassifier.classify("https://www.youtube.com/watch?v=abc123")

        assertFalse(result.isPlayableInHiPlayer)
        assertEquals(StreamUrlKind.PLATFORM_PAGE, result.kind)
    }
}
