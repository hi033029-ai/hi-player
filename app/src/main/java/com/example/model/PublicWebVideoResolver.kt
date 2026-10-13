package com.example.model

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Resolves webpages that publicly publish direct media through common web
 * standards. It intentionally does not execute provider JavaScript, decrypt
 * opaque responses, or bypass protected player, token, DRM, or login flows.
 */
data class PublicWebVideoResolution(
    val stream: StreamUrlResult? = null,
    val errorMessage: String? = null
)

object PublicWebVideoResolver {
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 12_000
    private const val MAX_HTML_BYTES = 256 * 1024
    private const val MAX_EMBED_DEPTH = 1
    private const val USER_AGENT = "HiPlayer/1.0 (Android; public web video resolver)"

    fun resolve(rawUrl: String): PublicWebVideoResolution {
        val classified = StreamUrlClassifier.classify(rawUrl)
        if (!classified.isPlayableInHiPlayer) {
            return PublicWebVideoResolution(errorMessage = classified.errorMessage)
        }
        if (!classified.requiresWebResolution) {
            return PublicWebVideoResolution(stream = classified)
        }
        return resolvePublicPage(rawUrl, linkedSetOf(), depth = 0)
    }

    private fun resolvePublicPage(
        pageUrl: String,
        visitedPages: MutableSet<String>,
        depth: Int
    ): PublicWebVideoResolution {
        if (depth > MAX_EMBED_DEPTH) {
            return PublicWebVideoResolution(
                errorMessage = "No direct public video source was found in the available embed pages."
            )
        }
        if (!visitedPages.add(pageUrl)) {
            return PublicWebVideoResolution(errorMessage = "The public video page redirects in a loop.")
        }

        return try {
            val connection = openConnection(pageUrl)
            connection.use {
                val responseCode = it.responseCode
                if (responseCode !in 200..299) {
                    return@use PublicWebVideoResolution(
                        errorMessage = "The webpage returned HTTP $responseCode and did not expose a playable video."
                    )
                }

                val finalUrl = it.url.toString()
                val contentType = normalizedContentType(it.contentType)
                // Some CDNs expose a real media file through a tokenized URL
                // but label it application/octet-stream or omit the type. Read
                // only the bounded response prefix and identify standard media
                // signatures before treating the URL as a webpage.
                val responseBytes = readLimitedBytes(it)
                classifyDirectProbe(finalUrl, contentType, responseBytes)?.let { direct ->
                    return@use playableOrError(
                        direct
                    )
                }
                if (contentType != null && !contentType.contains("html")) {
                    return@use PublicWebVideoResolution(
                        errorMessage = "This URL returned $contentType, not a playable video or public video page."
                    )
                }

                val html = responseBytes.toString(Charsets.UTF_8)
                val candidates = linkedMediaCandidates(html)
                val pageUri = runCatching { URI(finalUrl) }.getOrNull()
                    ?: return@use PublicWebVideoResolution(errorMessage = "The final webpage URL is invalid.")

                for (candidate in candidates) {
                    val resolvedUrl = resolveCandidateUrl(pageUri, candidate.url) ?: continue
                    if (candidate.isDirectMedia) {
                        val direct = StreamUrlClassifier.classifyResolvedMediaUrl(
                            rawUrl = resolvedUrl,
                            suppliedMimeType = candidate.mimeType,
                            displayTitle = candidate.title ?: pageTitle(html),
                            trustAsDirectSource = true
                        )
                        if (direct.isPlayableInHiPlayer) return@use playableOrError(direct)
                        continue
                    }

                    if (candidate.isPublicEmbed && depth < MAX_EMBED_DEPTH) {
                        val nestedClassified = StreamUrlClassifier.classify(resolvedUrl)
                        if (nestedClassified.isPlayableInHiPlayer && nestedClassified.requiresWebResolution) {
                            val nested = resolvePublicPage(resolvedUrl, visitedPages, depth + 1)
                            if (nested.stream != null) return@use nested
                        }
                    }
                }

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
        for (candidate in linkedMediaCandidates(html)) {
            if (!candidate.isDirectMedia) continue
            val resolvedUrl = resolveCandidateUrl(pageUri, candidate.url) ?: continue
            val result = StreamUrlClassifier.classifyResolvedMediaUrl(
                rawUrl = resolvedUrl,
                suppliedMimeType = candidate.mimeType,
                displayTitle = candidate.title ?: pageTitle(html),
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
            setRequestProperty(
                "Accept",
                "text/html,video/*,application/vnd.apple.mpegurl,application/dash+xml;q=0.9,*/*;q=0.1"
            )
            // A page resolver never needs the full body of a large media file.
            setRequestProperty("Range", "bytes=0-${MAX_HTML_BYTES - 1}")
        }

    private fun HttpURLConnection.use(block: (HttpURLConnection) -> PublicWebVideoResolution): PublicWebVideoResolution =
        try {
            block(this)
        } finally {
            disconnect()
        }

    private fun readLimitedBytes(connection: HttpURLConnection): ByteArray {
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
        return output.toByteArray()
    }

    /**
     * Classifies only a bounded prefix already fetched for a user-supplied URL.
     * It deliberately does not execute JavaScript or follow undocumented API
     * calls; it merely recognizes normal public HLS/DASH and container bytes.
     */
    internal fun classifyDirectProbe(
        finalUrl: String,
        contentType: String?,
        sample: ByteArray
    ): StreamUrlResult? {
        val normalizedType = normalizedContentType(contentType)
        val text = sample.toString(Charsets.UTF_8).trimStart()
        val detectedType = when {
            normalizedType != null && isDirectMediaType(normalizedType) -> normalizedType
            text.startsWith("#EXTM3U", ignoreCase = true) -> "application/x-mpegurl"
            Regex("(?is)^\\s*<\\?xml[^>]*>\\s*<mpd\\b|^\\s*<mpd\\b").containsMatchIn(text) -> "application/dash+xml"
            containsAscii(sample, "ftyp") -> "video/mp4"
            startsWith(sample, byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte())) -> "video/webm"
            isAviHeader(sample) -> "video/x-msvideo"
            else -> null
        } ?: return null

        return StreamUrlClassifier.classifyResolvedMediaUrl(
            rawUrl = finalUrl,
            suppliedMimeType = detectedType,
            displayTitle = null,
            trustAsDirectSource = true
        ).takeIf { it.isPlayableInHiPlayer }
    }

    private fun containsAscii(bytes: ByteArray, text: String): Boolean {
        val target = text.encodeToByteArray()
        return bytes.indices.any { start ->
            start + target.size <= bytes.size && target.indices.all { offset -> bytes[start + offset] == target[offset] }
        }
    }

    private fun startsWith(bytes: ByteArray, prefix: ByteArray): Boolean =
        bytes.size >= prefix.size && prefix.indices.all { bytes[it] == prefix[it] }

    private fun isAviHeader(bytes: ByteArray): Boolean =
        bytes.size >= 12 &&
            bytes.copyOfRange(0, 4).decodeToString() == "RIFF" &&
            bytes.copyOfRange(8, 12).decodeToString() == "AVI "

    private data class MediaCandidate(
        val url: String,
        val mimeType: String? = null,
        val title: String? = null,
        val isDirectMedia: Boolean = false,
        val isPublicEmbed: Boolean = false
    )

    private fun linkedMediaCandidates(html: String): List<MediaCandidate> {
        val candidates = linkedSetOf<MediaCandidate>()
        val title = pageTitle(html)
        val metaTags = Regex("""(?is)<meta\b[^>]*>""").findAll(html).map { it.value }.toList()
        val meta = metaTags.associateNotNull { tag ->
            val name = attribute(tag, "property") ?: attribute(tag, "name") ?: return@associateNotNull null
            val content = attribute(tag, "content") ?: return@associateNotNull null
            name.lowercase() to decodeHtml(content)
        }

        listOf("og:video", "og:video:secure_url", "twitter:player:stream").forEach { key ->
            meta[key]?.let { url ->
                val mime = when (key) {
                    "twitter:player:stream" -> meta["twitter:player:stream:content_type"]
                    else -> meta["og:video:type"]
                }
                candidates += MediaCandidate(url, mime, title, isDirectMedia = true)
            }
        }

        val tags = Regex("""(?is)<(?:source|video|link|meta|iframe|embed|object)\b[^>]*>""").findAll(html)
        for (tagMatch in tags) {
            val tag = tagMatch.value
            val itemProperty = attribute(tag, "itemprop")?.lowercase()
            val source = attribute(tag, "src") ?: attribute(tag, "href") ?: attribute(tag, "data")
            val mime = attribute(tag, "type")
            if (source != null) {
                val isTypedMedia = mime?.startsWith("video/", ignoreCase = true) == true ||
                    mime.equals("application/vnd.apple.mpegurl", ignoreCase = true) ||
                    mime.equals("application/x-mpegurl", ignoreCase = true) ||
                    mime.equals("application/dash+xml", ignoreCase = true)
                val looksDirect = looksLikeMediaUrl(source)
                when {
                    itemProperty == "contenturl" || isTypedMedia || looksDirect -> {
                        candidates += MediaCandidate(source, mime, title, isDirectMedia = true)
                    }
                    tag.startsWith("<iframe", ignoreCase = true) ||
                        tag.startsWith("<embed", ignoreCase = true) ||
                        tag.startsWith("<object", ignoreCase = true) ||
                        itemProperty == "embedurl" -> {
                        candidates += MediaCandidate(source, null, title, isPublicEmbed = true)
                    }
                }
            }
        }

        // Schema.org VideoObject JSON-LD is a standard way to expose actual
        // media bytes (contentUrl) and its optional encodingFormat.
        Regex("""(?is)<script\b[^>]*>(.*?)</script>""").findAll(html).forEach { match ->
            val script = match.value
            if (!attribute(script, "type").equals("application/ld+json", ignoreCase = true)) return@forEach
            val json = match.groupValues.getOrNull(1).orEmpty()
            val jsonTitle = jsonStringValues(json, "name").firstOrNull() ?: title
            val jsonMime = jsonStringValues(json, "encodingFormat").firstOrNull()
            jsonStringValues(json, "contentUrl").forEach { url ->
                candidates += MediaCandidate(url, jsonMime, jsonTitle, isDirectMedia = true)
            }
        }

        // Static HTML player settings often include a literal public HLS, DASH,
        // or file URL. Only extract literal media extensions, never opaque API
        // endpoints, tokens, or executable provider logic.
        Regex(
            """(?i)(?:https?:)?//[^\"'<>{}\s]+?\.(?:m3u8|mpd|mp4|m4v|mkv|webm|mov|avi|ts|m2ts|3gp|flv|wmv|vob)(?:\?[^\"'<>{}\s]*)?"""
        ).findAll(html).forEach { match ->
            candidates += MediaCandidate(match.value, null, title, isDirectMedia = true)
        }
        Regex(
            """(?i)(?:file|source|src|contentUrl)\s*[:=]\s*[\"']([^\"']+\.(?:m3u8|mpd|mp4|m4v|mkv|webm|mov|avi|ts|m2ts|3gp|flv|wmv|vob)(?:\?[^\"']*)?)[\"']"""
        ).findAll(html).forEach { match ->
            match.groupValues.getOrNull(1)?.let { url ->
                candidates += MediaCandidate(url, null, title, isDirectMedia = true)
            }
        }

        return candidates.toList()
    }

    private fun resolveCandidateUrl(pageUri: URI, rawCandidate: String): String? = runCatching {
        val candidate = decodeHtml(rawCandidate).trim()
        val absolute = when {
            candidate.startsWith("//") -> "${pageUri.scheme}:$candidate"
            else -> pageUri.resolve(candidate).toString()
        }
        val uri = URI(absolute)
        if (uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank()) absolute else null
    }.getOrNull()

    private fun pageTitle(html: String): String? =
        findMetaContent(html, "og:title") ?: findDocumentTitle(html)

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

    private fun jsonStringValues(json: String, key: String): List<String> {
        val escapedKey = Regex.escape(key)
        val direct = Regex("""(?is)[\"']$escapedKey[\"']\s*:\s*[\"']((?:\\.|[^\"'])*)[\"']""")
            .findAll(json)
            .mapNotNull { it.groupValues.getOrNull(1)?.let(::decodeJsonString) }
        val arrays = Regex("""(?is)[\"']$escapedKey[\"']\s*:\s*\[\s*[\"']((?:\\.|[^\"'])*)[\"']""")
            .findAll(json)
            .mapNotNull { it.groupValues.getOrNull(1)?.let(::decodeJsonString) }
        return (direct + arrays).map(String::trim).filter(String::isNotBlank).distinct().toList()
    }

    private fun decodeJsonString(value: String): String {
        var decoded = value
            .replace("\\/", "/")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
        decoded = Regex("""\\u([0-9a-fA-F]{4})""").replace(decoded) { match ->
            match.groupValues[1].toInt(16).toChar().toString()
        }
        return decodeHtml(decoded)
    }

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
            ?: Regex("""(?is)\b$escapedName\s*=\s*([^\s\"'=<>`]+)""")
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

    private fun decodeHtml(value: String): String {
        var decoded = value
            .replace("&amp;", "&", ignoreCase = true)
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'", ignoreCase = true)
        decoded = Regex("""&#x([0-9a-fA-F]+);""").replace(decoded) { match ->
            match.groupValues[1].toInt(16).toChar().toString()
        }
        return Regex("""&#(\d+);""").replace(decoded) { match ->
            match.groupValues[1].toInt().toChar().toString()
        }
    }
}

private inline fun <K, V> Iterable<V>.associateNotNull(transform: (V) -> Pair<K, V>?): Map<K, V> {
    val destination = linkedMapOf<K, V>()
    for (element in this) {
        transform(element)?.let { (key, value) -> destination[key] = value }
    }
    return destination
}
