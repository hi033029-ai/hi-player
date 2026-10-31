package com.example.downloads

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Application-owned HTTP transport. Never delegates transfers to a system downloader. */
internal class HttpDownloadTransfer {
    private val stopped = AtomicBoolean(false)
    @Volatile private var call: okhttp3.Call? = null

    fun cancel() { stopped.set(true); call?.cancel() }
    fun checkActive() { if (stopped.get()) throw InterruptedIOException("Transfer stopped") }

    data class Metadata(val total: Long, val etag: String?, val modified: String?, val mime: String?, val fileName: String? = null, val finalUrl: String? = null)

    fun download(
        url: String, file: File, headers: Map<String, String>, etag: String?, modified: String?,
        onMetadata: (Metadata) -> Unit, onProgress: (Long, Long) -> Unit,
    ) {
        var current = Request.Builder().url(url).build().url
        val origin = current
        var offset = file.length()
        // Without a server validator it is unsafe to append to an old representation.
        val validator = etag?.takeUnless { it.startsWith("W/") } ?: modified
        if (offset > 0 && validator.isNullOrBlank()) { RandomAccessFile(file, "rw").use { it.setLength(0) }; offset = 0 }
        var redirects = 0
        while (true) {
            checkActive()
            val request = Request.Builder().url(current).header("Accept-Encoding", "identity")
            headers.forEach { (name, value) ->
                val sensitive = name.equals("Cookie", true) || name.equals("Authorization", true) || name.equals("Referer", true)
                if (!sensitive || sameOrigin(origin, current)) request.header(name, value)
            }
            if (offset > 0) { request.header("Range", "bytes=$offset-"); request.header("If-Range", validator!!) }
            val activeCall = client.newCall(request.build())
            call = activeCall
            checkActive()
            activeCall.execute().use { response ->
                if (response.code in setOf(301, 302, 303, 307, 308)) {
                    if (++redirects > 10) throw DownloadHttpException("This link keeps redirecting. Reopen the website and try a fresh download link.", false)
                    val next = response.header("Location")?.let { current.resolve(it) }
                        ?: throw DownloadHttpException("This download link is unavailable. Reopen the website and try a fresh link.", false)
                    if (current.isHttps && !next.isHttps) throw DownloadHttpException("This link isn't secure. Use a trusted secure download link.", false)
                    current = next
                } else {
                    // A changed resource or a server ignoring Range must restart, never append.
                    if (response.code == 416 && offset > 0) {
                        RandomAccessFile(file, "rw").use { it.setLength(0) }
                        offset = 0
                        if (++redirects > 10) throw DownloadHttpException("This download couldn't resume. Cancel it and start again from the website.", false)
                    } else {
                        if (!response.isSuccessful) {
                            val message = when (response.code) {
                                401, 403 -> "Access denied. Reopen the website to refresh the download link."
                                404, 410 -> "This file is no longer available at this link. Reopen the website and try a fresh link."
                                else -> "The download service is unavailable. Wait a moment and tap Retry."
                            }
                            throw DownloadHttpException(message, response.code == 408 || response.code == 429 || response.code >= 500)
                        }
                        val body = response.body ?: throw IOException("Empty download response")
                        var total = body.contentLength().coerceAtLeast(0)
                        if (response.code == 206) {
                            val range = parseContentRange(response.header("Content-Range"))
                                ?: throw DownloadHttpException("This download couldn't resume. Cancel it and start again from the website.", false)
                            if (range.first != offset) throw DownloadHttpException("This download couldn't resume. Cancel it and start again from the website.", false)
                            val newTag = response.header("ETag")
                            if (offset > 0 && !etag.isNullOrBlank() && newTag != null && etag != newTag) {
                                throw DownloadHttpException("The file changed. Cancel and download it again.", false)
                            }
                            total = range.second
                        } else offset = 0
                        val metadata = Metadata(total, response.header("ETag"), response.header("Last-Modified"), response.header("Content-Type")?.substringBefore(';'), DownloadFileResolver.dispositionName(response.header("Content-Disposition")), current.toString())
                        RandomAccessFile(file, "rw").use { output ->
                            output.setLength(offset)
                            output.seek(offset)
                            onMetadata(metadata)
                            onProgress(offset, total)
                            var received = offset
                            var lastReport = System.nanoTime()
                            body.byteStream().use { input ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    checkActive()
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    output.write(buffer, 0, count)
                                    received += count
                                    val now = System.nanoTime()
                                    if (now - lastReport >= 400_000_000L) { onProgress(received, total); lastReport = now }
                                }
                            }
                            output.fd.sync()
                            if (total > 0 && received != total) throw IOException("Connection ended before the file was complete")
                            checkActive()
                            onProgress(received, if (total > 0) total else received)
                        }
                        return
                    }
                }
            }
        }
    }

    companion object {
        private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
        internal fun sameOrigin(a: okhttp3.HttpUrl, b: okhttp3.HttpUrl) = a.scheme == b.scheme && a.host == b.host && a.port == b.port
        internal fun parseContentRange(value: String?): Pair<Long, Long>? {
            val match = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(value.orEmpty()) ?: return null
            val (start, end, total) = match.destructured
            val s = start.toLongOrNull() ?: return null
            val e = end.toLongOrNull() ?: return null
            val t = total.toLongOrNull() ?: return null
            return if (s <= e && e < t) s to t else null
        }
    }
}

internal class DownloadHttpException(message: String, val retryable: Boolean) : IOException(message)
