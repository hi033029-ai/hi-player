package com.example.model

import androidx.media3.common.MimeTypes
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64

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
    val displayTitle: String? = null,
    val requiresWebResolution: Boolean = false,
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

        // HubCloud deliberately wraps a direct file in a small HTML Video.js
        // page. Feeding that page to Media3 produces a source error because
        // Media3 receives HTML rather than media. Its public URL format stores
        // the actual source, MIME type, and optional title in Base64 query
        // parameters, so unwrap only this known wrapper before playback.
        if (host == "hbplay.pages.dev") {
            return unwrapHubCloud(uri)
        }

        return classifyDirectStream(normalized, uri, displayTitle = null)
    }

    private fun unwrapHubCloud(wrapperUri: URI): StreamUrlResult {
        val encodedSource = queryParameter(wrapperUri, "u")
            ?: return invalid("This HubCloud link has no encoded video source.")
        val source = decodeBase64(encodedSource)
            ?: return invalid("This HubCloud link contains an invalid video source.")
        val sourceUri = runCatching { URI(source) }.getOrNull()
            ?: return invalid("This HubCloud link contains an invalid video URL.")
        val sourceScheme = sourceUri.scheme?.lowercase()
        if (sourceScheme !in setOf("http", "https") || sourceUri.host.isNullOrBlank()) {
            return invalid("This HubCloud link must contain an HTTP or HTTPS video URL.")
        }
        if (sourceUri.host.equals("hbplay.pages.dev", ignoreCase = true)) {
            return invalid("Nested HubCloud links are not playable sources.")
        }

        val title = queryParameter(wrapperUri, "t")?.let(::decodeBase64)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        val sourceMimeType = queryParameter(wrapperUri, "m")?.let(::decodeBase64)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        return classifyDirectStream(source, sourceUri, title, sourceMimeType)
    }

    /** Classifies a URL discovered from a direct response or public webpage. */
    fun classifyResolvedMediaUrl(
        rawUrl: String,
        suppliedMimeType: String? = null,
        displayTitle: String? = null,
        trustAsDirectSource: Boolean = false
    ): StreamUrlResult {
        val normalized = rawUrl.trim()
        val uri = runCatching { URI(normalized) }.getOrNull()
            ?: return invalid("The resolved media URL is invalid.")
        val scheme = uri.scheme?.lowercase()
        if (scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) {
            return invalid("The resolved media URL must use HTTP or HTTPS.")
        }
        return classifyDirectStream(normalized, uri, displayTitle, suppliedMimeType, trustAsDirectSource)
    }

    private fun classifyDirectStream(
        normalizedUrl: String,
        uri: URI,
        displayTitle: String?,
        suppliedMimeType: String? = null,
        trustAsDirectSource: Boolean = false
    ): StreamUrlResult {
        val host = uri.host?.lowercase().orEmpty()

        if (isPlatformPage(host)) {
            return StreamUrlResult(
                kind = StreamUrlKind.PLATFORM_PAGE,
                errorMessage = "This is a platform page, not a direct video stream. Use the provider's official player or paste a direct MP4, HLS (.m3u8), or DASH (.mpd) stream URL."
            )
        }

        val path = uri.path?.lowercase().orEmpty()
        val normalizedMime = normalizeMimeType(suppliedMimeType)
        return when {
            normalizedMime == MimeTypes.APPLICATION_M3U8 || path.endsWith(".m3u8") -> StreamUrlResult(
                kind = StreamUrlKind.HLS,
                normalizedUrl = normalizedUrl,
                mimeType = MimeTypes.APPLICATION_M3U8,
                displayTitle = displayTitle
            )
            normalizedMime == MimeTypes.APPLICATION_MPD || path.endsWith(".mpd") -> StreamUrlResult(
                kind = StreamUrlKind.DASH,
                normalizedUrl = normalizedUrl,
                mimeType = MimeTypes.APPLICATION_MPD,
                displayTitle = displayTitle
            )
            else -> StreamUrlResult(
                kind = StreamUrlKind.PROGRESSIVE,
                normalizedUrl = normalizedUrl,
                // Leave progressive MIME type unset so Media3 can sniff the
                // actual container unless the wrapper supplied one.
                mimeType = normalizedMime,
                displayTitle = displayTitle,
                requiresWebResolution = !trustAsDirectSource && normalizedMime == null && !looksLikeDirectVideoPath(path)
            )
        }
    }

    private fun normalizeMimeType(value: String?): String? = value
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase()
        ?.takeIf { it.isNotBlank() }
        ?.let { mime ->
            when (mime) {
                "application/x-mpegurl", "application/vnd.apple.mpegurl" -> MimeTypes.APPLICATION_M3U8
                "application/dash+xml" -> MimeTypes.APPLICATION_MPD
                else -> mime
            }
        }

    private fun looksLikeDirectVideoPath(path: String): Boolean =
        listOf(".mp4", ".m4v", ".mkv", ".webm", ".mov", ".avi", ".ts", ".mts", ".m2ts", ".3gp", ".flv", ".wmv", ".vob")
            .any(path::endsWith)

    private fun queryParameter(uri: URI, name: String): String? = uri.rawQuery
        ?.split('&')
        ?.firstOrNull { it.substringBefore('=').equals(name, ignoreCase = false) }
        ?.substringAfter('=', missingDelimiterValue = "")
        ?.takeIf { it.isNotBlank() }
        ?.let { rawValue -> URLDecoder.decode(rawValue.replace("+", "%2B"), StandardCharsets.UTF_8) }

    private fun decodeBase64(value: String): String? = runCatching {
        var normalized = value
            .replace('-', '+')
            .replace('_', '/')
            .replace(Regex("\\s"), "")
        while (normalized.length % 4 != 0) normalized += "="
        String(Base64.getDecoder().decode(normalized), StandardCharsets.UTF_8)
    }.getOrNull()

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
