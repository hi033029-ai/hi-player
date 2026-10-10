package com.example.model

import androidx.media3.common.MimeTypes
import java.net.URI

enum class StreamUrlKind {
    PROGRESSIVE,
    HLS,
    DASH,
    PLATFORM_PAGE,
    INVALID
}

data class StreamUrlResult(
    val kind: StreamUrlKind,
    val normalizedUrl: String? = null,
    val mimeType: String? = null,
    val errorMessage: String? = null
) {
    val isPlayableInHiPlayer: Boolean
        get() = kind == StreamUrlKind.PROGRESSIVE || kind == StreamUrlKind.HLS || kind == StreamUrlKind.DASH
}

/**
 * Validates URLs accepted by Hi Player's native Media3 stream pipeline.
 *
 * Native playback intentionally accepts direct progressive files, HLS playlists
 * and DASH manifests. Provider page URLs are not passed to Media3 because a
 * YouTube/Facebook/Instagram page is HTML, not a media stream. Their official
 * embeds/apps own authentication, ads, DRM and quality selection.
 */
object StreamUrlClassifier {
    fun classify(rawUrl: String): StreamUrlResult {
        val normalized = rawUrl.trim()
        if (normalized.isBlank()) {
            return invalid("Enter a direct video, HLS (.m3u8), or DASH (.mpd) URL.")
        }

        val uri = runCatching { URI(normalized) }.getOrNull()
            ?: return invalid("Enter a valid HTTP or HTTPS URL.")
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        if (scheme !in setOf("http", "https") || host.isNullOrBlank()) {
            return invalid("Only HTTP and HTTPS stream URLs are supported.")
        }

        if (isPlatformPage(host)) {
            return StreamUrlResult(
                kind = StreamUrlKind.PLATFORM_PAGE,
                errorMessage = "This is a platform page, not a direct video stream. Use the provider's official player or paste a direct MP4, HLS (.m3u8), or DASH (.mpd) stream URL."
            )
        }

        val path = uri.path?.lowercase().orEmpty()
        return when {
            path.endsWith(".m3u8") -> StreamUrlResult(
                kind = StreamUrlKind.HLS,
                normalizedUrl = normalized,
                mimeType = MimeTypes.APPLICATION_M3U8
            )
            path.endsWith(".mpd") -> StreamUrlResult(
                kind = StreamUrlKind.DASH,
                normalizedUrl = normalized,
                mimeType = MimeTypes.APPLICATION_MPD
            )
            else -> StreamUrlResult(
                kind = StreamUrlKind.PROGRESSIVE,
                normalizedUrl = normalized,
                // Leave progressive MIME type unset so Media3 can sniff the
                // actual container and select its appropriate extractor.
                mimeType = null
            )
        }
    }

    private fun invalid(message: String) = StreamUrlResult(
        kind = StreamUrlKind.INVALID,
        errorMessage = message
    )

    private fun isPlatformPage(host: String): Boolean =
        host == "youtu.be" ||
            host.endsWith("youtube.com") ||
            host.endsWith("facebook.com") ||
            host.endsWith("fb.watch") ||
            host.endsWith("instagram.com")
}
