package com.example.model

import androidx.media3.common.MimeTypes
import java.nio.charset.StandardCharsets
import java.util.Base64
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

    @Test
    fun hubCloudLinkIsUnwrappedToItsDirectVideoSource() {
        val directUrl = "https://cdn.example.test/movies/feature.mkv?token=abc"
        val source = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(directUrl.toByteArray(StandardCharsets.UTF_8))
        val mime = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("video/x-matroska".toByteArray(StandardCharsets.UTF_8))
        val title = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("Feature Film".toByteArray(StandardCharsets.UTF_8))

        val result = StreamUrlClassifier.classify(
            "https://hbplay.pages.dev/?u=$source&m=$mime&t=$title"
        )

        assertTrue(result.isPlayableInHiPlayer)
        assertEquals(StreamUrlKind.PROGRESSIVE, result.kind)
        assertEquals(directUrl, result.normalizedUrl)
        assertEquals("video/x-matroska", result.mimeType)
        assertEquals("Feature Film", result.displayTitle)
    }

    @Test
    fun ordinaryWebpageIsMarkedForPublicVideoResolution() {
        val result = StreamUrlClassifier.classify("https://news.example.test/watch/feature-film")

        assertTrue(result.isPlayableInHiPlayer)
        assertTrue(result.requiresWebResolution)
    }

    @Test
    fun publicOpenGraphVideoIsResolvedToNativePlayback() {
        val result = PublicWebVideoResolver.findEmbeddedMedia(
            pageUrl = "https://news.example.test/articles/feature",
            html = """
                <html><head>
                  <meta property="og:title" content="Public Feature" />
                  <meta property="og:video" content="https://cdn.example.test/media/feature.mp4" />
                  <meta property="og:video:type" content="video/mp4" />
                </head></html>
            """.trimIndent()
        )

        requireNotNull(result)
        assertTrue(result.isPlayableInHiPlayer)
        assertFalse(result.requiresWebResolution)
        assertEquals("https://cdn.example.test/media/feature.mp4", result.normalizedUrl)
        assertEquals("Public Feature", result.displayTitle)
    }

    @Test
    fun extensionlessMp4ProbeUsesNativeProgressivePlayback() {
        val sample = byteArrayOf(0, 0, 0, 24) + "ftypisom".toByteArray(StandardCharsets.US_ASCII)

        val result = PublicWebVideoResolver.classifyDirectProbe(
            finalUrl = "https://cdn.example.test/play?id=opaque-token",
            contentType = "application/octet-stream",
            sample = sample
        )

        requireNotNull(result)
        assertTrue(result.isPlayableInHiPlayer)
        assertEquals(StreamUrlKind.PROGRESSIVE, result.kind)
        assertEquals(MimeTypes.VIDEO_MP4, result.mimeType)
    }

    @Test
    fun hlsPayloadProbeUsesNativeHlsPlayback() {
        val result = PublicWebVideoResolver.classifyDirectProbe(
            finalUrl = "https://cdn.example.test/play?id=live-token",
            contentType = "application/octet-stream",
            sample = "#EXTM3U\n#EXT-X-VERSION:3\n".toByteArray(StandardCharsets.UTF_8)
        )

        requireNotNull(result)
        assertEquals(StreamUrlKind.HLS, result.kind)
        assertEquals(MimeTypes.APPLICATION_M3U8, result.mimeType)
    }
}
