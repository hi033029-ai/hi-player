package com.example.model

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Resolves public webpages that explicitly publish a direct video source.
 *
 * This is intentionally a small, non-browser resolver: it follows normal HTTP
 * redirects, accepts a direct media response, and reads standard public HTML
 * metadata (<video>, <source>, Open Graph and Twitter stream tags). It does
 * not execute page JavaScript, bypass provider access controls, or extract
 * signed/DRM provider streams.
 */
data class PublicWebVideoResolution(
    val stream: StreamUrlResult? = null,
    val errorMessage: String? = null
)

object PublicWebVideoResolver {
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 12_000
    private const val MAX_HTML_BYTES = 256 * 1024
    private const val USER_AGENT = "HiPlayer/1.0 (Android; public web video resolver)"

    fun resolve(rawUrl: String): PublicWebVideoResolution {
        val classified = StreamUrlClassifier.classify(rawUrl)
        if (!classified.isPlayableInHiPlayer) {
            return PublicWebVideoResolution(errorMessage = classified.errorMessage)
        }
        if (!classified.requiresWebResolution) {
            return PublicWebVideoResolution(stream = classified)
        }

        return try {
            val connection = openConnection(rawUrl)
            connection.use {
                val responseCode = it.responseCode
                if (responseCode !in 200..299) {
                    return@use PublicWebVideoResolution(
                        errorMessage = "The webpage returned HTTP $responseCode and did not expose a playable video."
                    )
                }

                val finalUrl = it.url.toString()
                val contentType = normalizedContentType(it.contentType)
                if (isDirectMediaType(contentType)) {
                    val direct = StreamUrlClassifier.classifyResolvedMediaUrl(
                        rawUrl = finalUrl,
                        suppliedMimeType = contentType,
                        displayTitle = null,
                        trustAsDirectSource = true
                    )
                    return@use playableOrError(direct)
                }
                if (contentType != null && !contentType.contains("html")) {
                    return@use PublicWebVideoResolution(
                        errorMessage = "This URL returned $contentType, not a playable video or public video page."
                    )
                }

                val html = readLimitedText(it)
                val resolved = findEmbeddedMedia(finalUrl, html)
                if (resolved != null) return@use playableOrError(resolved)

                PublicWebVideoResolution(
                    errorMessage = "No public direct video source was found on this webpage. Paste an MP4/WebM/MKV, HLS (.m3u8), or DASH (.mpd) link instead."
                )
            }
        } catch (error: Exception) {
            PublicWebVideoResolution(
                errorMessage = "Could not inspect this webpage: ${error.message ?: "network error"}"
            )
        }
    }

    /** Visible for regression coverage; production resolution calls this after fetching public HTML. */
    internal fun findEmbeddedMedia(pageUrl: String, html: String): StreamUrlResult? {
        val pageUri = runCatching { URI(pageUrl) }.getOrNull() ?: return null
        val pageTitle = findMetaContent(html, "og:title") ?: findDocumentTitle(html)
        val candidates = linkedMediaCandidates(html)
        for (candidate in candidates) {
            val resolvedUrl = runCatching { pageUri.resolve(decodeHtml(candidate.url)).toString() }.getOrNull()
                ?: continue
            val result = StreamUrlClassifier.classifyResolvedMediaUrl(
                rawUrl = resolvedUrl,
                suppliedMimeType = candidate.mimeType,
                displayTitle = pageTitle,
                trustAsDirectSource = true
            )
            if (result.isPlayableInHiPlayer) return result
        }
        return null
    }

    private fun openConnection(rawUrl: String): HttpURLConnection =
        (URL(rawUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "text/html,video/*,application/vnd.apple.mpegurl,application/dash+xml;q=0.9,*/*;q=0.1")
            // A page resolver never needs the full body of a large media file.
            setRequestProperty("Range", "bytes=0-${MAX_HTML_BYTES - 1}")
        }

    private fun HttpURLConnection.use(block: (HttpURLConnection) -> PublicWebVideoResolution): PublicWebVideoResolution =
        try {
            block(this)
        } finally {
            disconnect()
        }

    private fun readLimitedText(connection: HttpURLConnection): String {
        val output = ByteArrayOutputStream()
        connection.inputStream.use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (output.size() < MAX_HTML_BYTES) {
                val remaining = MAX_HTML_BYTES - output.size()
                val count = input.read(buffer, 0, minOf(buffer.size, remaining))
                if (count <= 0) break
                output.write(buffer, 0, count)
            }
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private data class MediaCandidate(val url: String, val mimeType: String?)

    private fun linkedMediaCandidates(html: String): List<MediaCandidate> {
        val candidates = linkedSetOf<MediaCandidate>()
        val tags = Regex("""(?is)<(?:meta|source|video|link)\b[^>]*>""").findAll(html)
        for (tagMatch in tags) {
            val tag = tagMatch.value
            val name = attribute(tag, "property") ?: attribute(tag, "name")
            val content = attribute(tag, "content")
            when (name?.lowercase()) {
                "og:video", "og:video:secure_url", "twitter:player:stream" -> {
                    content?.let { candidates += MediaCandidate(it, attribute(tag, "type")) }
                }
            }
            val source = attribute(tag, "src") ?: attribute(tag, "href")
            if (source != null) {
                val mime = attribute(tag, "type")
                if (mime?.startsWith("video/", ignoreCase = true) == true ||
                    mime.equals("application/vnd.apple.mpegurl", ignoreCase = true) ||
                    mime.equals("application/dash+xml", ignoreCase = true) ||
                    looksLikeMediaUrl(source)
                ) {
                    candidates += MediaCandidate(source, mime)
                }
            }
        }

        // Some ordinary static pages publish HLS/DASH links in JSON state rather
        // than source tags. Recognize only literal public playlist/manifest URLs.
        Regex("""https?://[^"'<>\s]+?\.(?:m3u8|mpd)(?:\?[^"'<>\s]*)?""", RegexOption.IGNORE_CASE)
            .findAll(html)
            .forEach { candidates += MediaCandidate(it.value, null) }
        return candidates.toList()
    }

    private fun findMetaContent(html: String, name: String): String? =
        Regex("""(?is)<meta\b[^>]*>""").findAll(html)
            .map { it.value }
            .firstOrNull { tag ->
                (attribute(tag, "property") ?: attribute(tag, "name"))?.equals(name, ignoreCase = true) == true
            }
            ?.let { attribute(it, "content") }
            ?.let(::decodeHtml)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    private fun findDocumentTitle(html: String): String? =
        Regex("""(?is)<title[^>]*>(.*?)</title>""").find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.let(::decodeHtml)
            ?.replace(Regex("""\s+"""), " ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    private fun attribute(tag: String, name: String): String? {
        val escapedName = Regex.escape(name)
        return Regex("(?is)\\b$escapedName\\s*=\\s*\"([^\"]*)\"")
            .find(tag)
            ?.groupValues
            ?.getOrNull(1)
            ?: Regex("""(?is)\b$escapedName\s*=\s*'([^']*)'""")
                .find(tag)
                ?.groupValues
                ?.getOrNull(1)
    }

    private fun looksLikeMediaUrl(url: String): Boolean {
        val clean = url.substringBefore('?').substringBefore('#').lowercase()
        return listOf(".mp4", ".m4v", ".mkv", ".webm", ".mov", ".avi", ".ts", ".m2ts", ".3gp", ".flv", ".wmv", ".vob", ".m3u8", ".mpd")
            .any(clean::endsWith)
    }

    private fun normalizedContentType(raw: String?): String? = raw
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase()
        ?.takeIf { it.isNotBlank() }

    private fun isDirectMediaType(mimeType: String?): Boolean =
        mimeType?.startsWith("video/", ignoreCase = true) == true ||
            mimeType.equals("application/vnd.apple.mpegurl", ignoreCase = true) ||
            mimeType.equals("application/x-mpegurl", ignoreCase = true) ||
            mimeType.equals("application/dash+xml", ignoreCase = true)

    private fun playableOrError(result: StreamUrlResult): PublicWebVideoResolution =
        if (result.isPlayableInHiPlayer) PublicWebVideoResolution(stream = result)
        else PublicWebVideoResolution(errorMessage = result.errorMessage)

    private fun decodeHtml(value: String): String = value
        .replace("&amp;", "&", ignoreCase = true)
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'", ignoreCase = true)
}
