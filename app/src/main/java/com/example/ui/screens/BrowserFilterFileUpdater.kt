package com.example.ui.screens

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** Plain filter lists only. Never downloads/evaluates executable JavaScript. */
internal object BrowserFilterFileUpdater {
    private val installLock = Any()
    private const val MAX_SOURCE_BYTES = 8_000_000L
    private const val MAX_COMBINED_BYTES = 16_000_000L
    private val approvedHosts = setOf("easylist.to", "pgl.yoyo.org")

    internal fun permittedSource(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host?.lowercase() in approvedHosts &&
            uri.rawUserInfo == null && (uri.port == -1 || uri.port == 443) && uri.rawFragment == null
    }.getOrDefault(false)

    /** Reject HTTP-200 error/HTML/empty responses before touching the active list. */
    internal fun validate(file: File): Boolean {
        var first: String? = null
        var rules = 0
        var lines = 0
        file.bufferedReader().useLines { input ->
            input.forEach { raw ->
                lines++
                if (lines > 300_000 || raw.length > 16_384) return false
                val line = raw.trim()
                if (first == null && line.isNotEmpty()) first = line
                if (line.startsWith("||") && line.length > 5) rules++
            }
        }
        return rules >= 100 && (first?.startsWith("[Adblock", ignoreCase = true) == true ||
            first?.startsWith("!") == true)
    }

    private fun download(source: String, destination: File, cancelled: () -> Boolean) {
        var address = source
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(60)
        fun checkActive() {
            if (cancelled()) throw IOException("Update cancelled")
            if (System.nanoTime() >= deadline) throw IOException("Filter update timed out")
        }
        repeat(5) {
            check(permittedSource(address)) { "Unapproved filter source" }
            checkActive()
            val connection = (URL(address).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 10_000
                readTimeout = 20_000
                setRequestProperty("User-Agent", "HiPlayer-FilterUpdater/1.0")
                setRequestProperty("Accept", "text/plain")
                setRequestProperty("Accept-Encoding", "identity")
            }
            try {
                val status = connection.responseCode
                if (status in listOf(301, 302, 303, 307, 308)) {
                    address = URI(address).resolve(connection.getHeaderField("Location")
                        ?: throw IOException("Missing redirect destination")).toString()
                } else {
                    check(status == 200) { "Filter request failed" }
                    check(connection.contentLengthLong <= MAX_SOURCE_BYTES) { "Filter list too large" }
                    val type = connection.contentType.orEmpty().lowercase()
                    check(!type.contains("text/html") && !type.contains("javascript")) { "Invalid filter content" }
                    connection.inputStream.use { input ->
                        destination.outputStream().use { output ->
                            val buffer = ByteArray(16_384)
                            var bytes = 0L
                            while (true) {
                                checkActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                bytes += count
                                check(bytes <= MAX_SOURCE_BYTES) { "Filter list too large" }
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    check(validate(destination)) { "Invalid filter list" }
                    return
                }
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("Too many redirects")
    }

    fun install(context: Context, sources: List<String>, version: String,
        cancelled: () -> Boolean, publish: (() -> Unit) -> Unit): Boolean = synchronized(installLock) {
        var staging: File? = null
        var part: File? = null
        try {
            val combinedFile = File.createTempFile("browser-filters-", ".tmp", context.cacheDir).also { staging = it }
            val partFile = File.createTempFile("browser-filter-part-", ".tmp", context.cacheDir).also { part = it }
            combinedFile.outputStream().use { output ->
                // Downloaded lists extend, rather than discard, shipped YouTube/site protection.
                context.assets.open("orbit_filters.txt").use { it.copyTo(output) }
                output.write(10)
                for (source in sources) {
                    download(source, partFile, cancelled)
                    check(combinedFile.length() + partFile.length() <= MAX_COMBINED_BYTES) { "Combined filters too large" }
                    partFile.inputStream().use { it.copyTo(output) }
                    output.write(10)
                }
            }
            if (cancelled()) throw IOException("Update cancelled")
            // Check total line budget too, so later sources cannot be silently ignored at reload.
            check(combinedFile.useLines { lines -> lines.take(300_001).count() } <= 300_000) { "Too many filter rules" }
            // Share the engine's short publication lock with startup file reads.
            // Network downloads above never hold that lock.
            publish {
                val atomic = AtomicFile(File(context.filesDir, "orbit-filters.txt"))
                val output = atomic.startWrite()
                try {
                    combinedFile.inputStream().use { input ->
                        val buffer = ByteArray(16_384)
                        while (true) {
                            if (cancelled()) throw IOException("Update cancelled")
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                    }
                    if (cancelled()) throw IOException("Update cancelled")
                    atomic.finishWrite(output)
                } catch (error: Exception) {
                    atomic.failWrite(output)
                    throw error
                }
                val metadata = AtomicFile(File(context.filesDir, "orbit-filters.version"))
                val metadataOutput = metadata.startWrite()
                try {
                    metadataOutput.write(version.toByteArray(Charsets.UTF_8))
                    metadata.finishWrite(metadataOutput)
                } catch (error: Exception) {
                    metadata.failWrite(metadataOutput)
                    throw error
                }
            }
            true
        } catch (_: Exception) {
            // Do not log URLs/bodies or expose raw server errors. Keep last installed rules.
            false
        } finally {
            staging?.delete()
            part?.delete()
        }
    }
}
