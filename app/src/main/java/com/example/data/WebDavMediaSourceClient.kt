package com.example.data

import android.net.Uri
import android.util.Xml
import com.example.model.RemoteMediaMatch
import com.example.model.WebDavConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import java.net.URI
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

/**
 * Bounded WebDAV browser for a user-owned folder. It uses PROPFIND Depth: 1
 * recursively to a small fixed depth, rather than attempting a costly whole
 * server crawl. Direct item URLs are handed to Media3 with Basic auth headers.
 */
object WebDavMediaSourceClient {
    private const val MAX_DEPTH = 3
    private const val MAX_ENTRIES = 500
    private const val MAX_FOLDERS = 40
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val videoExtensions = setOf("mp4", "m4v", "mkv", "webm", "mov", "avi", "ts", "m2ts", "3gp", "flv", "wmv", "vob")

    suspend fun search(connection: WebDavConnection, query: String): List<RemoteMediaMatch> = withContext(Dispatchers.IO) {
        if (!connection.isConfigured || query.isBlank()) return@withContext emptyList()
        val normalizedRoot = normalizeFolderUrl(connection.folderUrl)
        val headers = requestHeaders(connection)
        val entries = crawl(normalizedRoot, headers)
        val queryTokens = query.lowercase()
            .replace(Regex("""[^a-z0-9]+"""), " ")
            .split(' ')
            .filter { it.length >= 2 }
            .take(6)
        entries
            .asSequence()
            .filter { !it.isCollection && extensionOf(it.url) in videoExtensions }
            .filter { entry ->
                val haystack = cleanFileName(entry.url).lowercase()
                queryTokens.all { it in haystack }
            }
            .take(80)
            .map { entry ->
                val fileName = cleanFileName(entry.url)
                val extension = extensionOf(entry.url)
                val upper = fileName.uppercase()
                val isHdr = listOf("HDR", "HDR10", "DV", "DOLBY VISION").any(upper::contains)
                val hdr = when {
                    upper.contains("DOLBY VISION") || Regex("""\bDV\b""").containsMatchIn(upper) -> "Dolby Vision"
                    upper.contains("HDR10+") -> "HDR10+"
                    upper.contains("HDR10") -> "HDR10"
                    upper.contains("HDR") -> "HDR"
                    else -> null
                }
                val audioLabels = listOf("Hindi", "English", "Tamil", "Telugu", "Korean", "Japanese", "Spanish", "French")
                    .filter { upper.contains(it.uppercase()) }
                    .map { language ->
                        val channels = when {
                            upper.contains("$language 7.1") -> " 7.1"
                            upper.contains("$language 5.1") -> " 5.1"
                            upper.contains("$language 2.0") -> " 2.0"
                            else -> ""
                        }
                        language + channels
                    }
                RemoteMediaMatch(
                    id = "webdav:${entry.url}",
                    sourceName = connection.displayName.ifBlank { "WebDAV" },
                    title = fileName.substringBeforeLast('.', fileName),
                    streamUri = Uri.parse(entry.url),
                    streamMimeType = mimeTypeFor(extension),
                    requestHeaders = headers,
                    width = if (upper.contains("2160P") || upper.contains("4K") || upper.contains("UHD")) 3840 else 0,
                    height = if (upper.contains("2160P") || upper.contains("4K") || upper.contains("UHD")) 2160 else 0,
                    sizeBytes = entry.sizeBytes,
                    isHdr = isHdr,
                    hdrFormat = hdr,
                    codec = when {
                        upper.contains("AV1") -> "AV1"
                        upper.contains("HEVC") || upper.contains("X265") || upper.contains("H.265") -> "HEVC"
                        else -> "Direct WebDAV stream"
                    },
                    audioLabels = audioLabels
                )
            }
            .toList()
    }

    fun requestHeaders(connection: WebDavConnection): Map<String, String> = mapOf(
        "Authorization" to Credentials.basic(connection.username, connection.password)
    )

    /** Validates only the selected root folder before persisting credentials. */
    suspend fun verify(connection: WebDavConnection): Unit = withContext(Dispatchers.IO) {
        require(connection.isConfigured) { "Enter the WebDAV folder URL, username, and password." }
        propfind(normalizeFolderUrl(connection.folderUrl), requestHeaders(connection))
        Unit
    }

    private fun crawl(rootUrl: String, headers: Map<String, String>): List<DavEntry> {
        val queue = ArrayDeque<Pair<String, Int>>()
        queue += rootUrl to 0
        val visited = mutableSetOf<String>()
        val files = mutableListOf<DavEntry>()
        while (queue.isNotEmpty() && files.size < MAX_ENTRIES && visited.size < MAX_FOLDERS) {
            val (url, depth) = queue.removeFirst()
            if (!visited.add(url)) continue
            val entries = propfind(url, headers)
            for (entry in entries) {
                if (entry.url.trimEnd('/') == url.trimEnd('/')) continue
                if (entry.isCollection && depth < MAX_DEPTH) {
                    queue += normalizeFolderUrl(entry.url) to (depth + 1)
                } else if (!entry.isCollection) {
                    files += entry
                    if (files.size >= MAX_ENTRIES) break
                }
            }
        }
        return files
    }

    private fun propfind(url: String, headers: Map<String, String>): List<DavEntry> {
        val request = Request.Builder()
            .url(url)
            .header("Depth", "1")
            .header("Accept", "application/xml,text/xml,*/*")
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .method("PROPFIND", "".toRequestBody("text/xml; charset=utf-8".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code !in 200..299) {
                throw IllegalStateException("WebDAV returned HTTP ${response.code}.")
            }
            return parseMultiStatus(url, response.body?.byteStream() ?: return emptyList())
        }
    }

    private fun parseMultiStatus(requestUrl: String, input: java.io.InputStream): List<DavEntry> {
        val parser = Xml.newPullParser().apply { setInput(input, "UTF-8") }
        val entries = mutableListOf<DavEntry>()
        var current: MutableDavEntry? = null
        var text = ""
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name.substringAfter(':')) {
                    "response" -> current = MutableDavEntry()
                    "collection" -> current?.isCollection = true
                }
                XmlPullParser.TEXT -> text = parser.text.orEmpty()
                XmlPullParser.END_TAG -> {
                    val target = current
                    when (parser.name.substringAfter(':')) {
                        "href" -> if (target != null) target.href = text.trim()
                        "getcontentlength" -> if (target != null) target.sizeBytes = text.trim().toLongOrNull() ?: 0L
                        "response" -> {
                            val href = target?.href.orEmpty()
                            if (href.isNotBlank()) {
                                entries += DavEntry(
                                    url = resolveHref(requestUrl, href),
                                    isCollection = target?.isCollection == true,
                                    sizeBytes = target?.sizeBytes ?: 0L
                                )
                            }
                            current = null
                        }
                    }
                    text = ""
                }
            }
            parser.next()
        }
        return entries
    }

    private fun resolveHref(requestUrl: String, href: String): String = runCatching {
        URI(requestUrl).resolve(href).toString()
    }.getOrDefault(href)

    private fun normalizeFolderUrl(raw: String): String {
        val value = raw.trim()
        require(value.startsWith("http://") || value.startsWith("https://")) { "Use a full WebDAV folder URL starting with http:// or https://." }
        return value.trimEnd('/') + "/"
    }

    private fun cleanFileName(url: String): String = url.substringAfterLast('/').let { encoded ->
        runCatching { URLDecoder.decode(encoded, Charsets.UTF_8.name()) }.getOrDefault(encoded)
    }

    private fun extensionOf(url: String): String = cleanFileName(url).substringAfterLast('.', "").lowercase()

    private fun mimeTypeFor(extension: String): String? = when (extension) {
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        "avi" -> "video/x-msvideo"
        "ts", "m2ts" -> "video/mp2t"
        "mp4", "m4v" -> "video/mp4"
        else -> null
    }

    private data class DavEntry(val url: String, val isCollection: Boolean, val sizeBytes: Long)
    private class MutableDavEntry {
        var href: String = ""
        var isCollection: Boolean = false
        var sizeBytes: Long = 0L
    }
}
