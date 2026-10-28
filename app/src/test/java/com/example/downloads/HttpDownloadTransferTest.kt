package com.example.downloads

import com.sun.net.httpserver.HttpServer
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.Executors

class HttpDownloadTransferTest {
    private lateinit var server: HttpServer
    private lateinit var dir: File
    private val payload = ByteArray(256 * 1024) { (it % 251).toByte() }
    private val executor = Executors.newCachedThreadPool()
    private val base get() = "http://127.0.0.1:${server.address.port}"
    @Before fun setup() {
        dir = java.nio.file.Files.createTempDirectory("hi-download-test").toFile()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = executor
        server.start()
    }
    @After fun cleanup() { server.stop(0); executor.shutdownNow(); dir.deleteRecursively() }

    @Test fun downloadsEntireFileExactly() {
        server.createContext("/file") { x ->
            assertEquals("identity", x.requestHeaders.getFirst("Accept-Encoding"))
            x.responseHeaders.add("ETag", "\"v1\"")
            x.sendResponseHeaders(200, payload.size.toLong()); x.responseBody.use { it.write(payload) }
        }
        val target = File(dir, "file.part")
        var reported = 0L
        HttpDownloadTransfer().download("$base/file", target, emptyMap(), null, null, {}, { bytes, _ -> reported = bytes })
        assertArrayEquals(payload, target.readBytes())
        assertEquals(payload.size.toLong(), reported)
    }
    @Test fun resumesWithRangeAndIfRangeWithoutCorruption() {
        val start = 32000
        server.createContext("/file") { x ->
            assertEquals("bytes=$start-", x.requestHeaders.getFirst("Range"))
            assertEquals("\"v1\"", x.requestHeaders.getFirst("If-Range"))
            x.responseHeaders.add("ETag", "\"v1\"")
            x.responseHeaders.add("Content-Range", "bytes $start-${payload.lastIndex}/${payload.size}")
            x.sendResponseHeaders(206, (payload.size - start).toLong())
            x.responseBody.use { it.write(payload, start, payload.size - start) }
        }
        val target = File(dir, "file.part").apply { writeBytes(payload.copyOf(start)) }
        HttpDownloadTransfer().download("$base/file", target, emptyMap(), "\"v1\"", null, {}, { _, _ -> })
        assertArrayEquals(payload, target.readBytes())
    }
    @Test fun ignoredRangeRestartsInsteadOfAppending() {
        server.createContext("/file") { x ->
            x.sendResponseHeaders(200, payload.size.toLong()); x.responseBody.use { it.write(payload) }
        }
        val target = File(dir, "file.part").apply { writeBytes(ByteArray(17000) { 99 }) }
        HttpDownloadTransfer().download("$base/file", target, emptyMap(), "\"old\"", null, {}, { _, _ -> })
        assertArrayEquals(payload, target.readBytes())
    }
    @Test fun noValidatorRestartsSafely() {
        server.createContext("/file") { x ->
            assertNull(x.requestHeaders.getFirst("Range"))
            x.sendResponseHeaders(200, payload.size.toLong()); x.responseBody.use { it.write(payload) }
        }
        val target = File(dir, "file.part").apply { writeBytes(ByteArray(100)) }
        HttpDownloadTransfer().download("$base/file", target, emptyMap(), null, null, {}, { _, _ -> })
        assertArrayEquals(payload, target.readBytes())
    }
    @Test fun wrongRangeIsRejectedWithoutAppending() {
        server.createContext("/file") { x ->
            x.responseHeaders.add("Content-Range", "bytes 0-${payload.lastIndex}/${payload.size}")
            x.sendResponseHeaders(206, payload.size.toLong()); x.responseBody.use { runCatching { it.write(payload) } }
        }
        val target = File(dir, "file.part").apply { writeBytes(payload.copyOf(100)) }
        assertThrows(DownloadHttpException::class.java) {
            HttpDownloadTransfer().download("$base/file", target, emptyMap(), "\"v1\"", null, {}, { _, _ -> })
        }
        assertEquals(100L, target.length())
    }
    @Test fun unsatisfiableRangeRestarts() {
        var calls = 0
        server.createContext("/file") { x ->
            calls++
            if (x.requestHeaders.getFirst("Range") != null) { x.sendResponseHeaders(416, -1); x.close() }
            else { x.sendResponseHeaders(200, payload.size.toLong()); x.responseBody.use { it.write(payload) } }
        }
        val target = File(dir, "file.part").apply { writeBytes(ByteArray(100)) }
        HttpDownloadTransfer().download("$base/file", target, emptyMap(), "\"v1\"", null, {}, { _, _ -> })
        assertEquals(2, calls)
        assertArrayEquals(payload, target.readBytes())
    }
    @Test fun followsSameOriginRedirectAndPreservesHeaders() {
        server.createContext("/redirect") { x -> x.responseHeaders.add("Location", "/file"); x.sendResponseHeaders(302, -1); x.close() }
        server.createContext("/file") { x ->
            assertEquals("session=test", x.requestHeaders.getFirst("Cookie"))
            assertEquals("$base/page", x.requestHeaders.getFirst("Referer"))
            x.sendResponseHeaders(200, payload.size.toLong()); x.responseBody.use { it.write(payload) }
        }
        val target = File(dir, "file.part")
        HttpDownloadTransfer().download("$base/redirect", target, mapOf("Cookie" to "session=test", "Referer" to "$base/page"), null, null, {}, { _, _ -> })
        assertArrayEquals(payload, target.readBytes())
    }
    @Test fun crossOriginRedirectDoesNotLeakCookiesOrReferer() {
        server.createContext("/redirect") { x -> x.responseHeaders.add("Location", "http://localhost:${server.address.port}/file"); x.sendResponseHeaders(302, -1); x.close() }
        server.createContext("/file") { x ->
            assertNull(x.requestHeaders.getFirst("Cookie"))
            assertNull(x.requestHeaders.getFirst("Authorization"))
            assertNull(x.requestHeaders.getFirst("Referer"))
            x.sendResponseHeaders(200, payload.size.toLong()); x.responseBody.use { it.write(payload) }
        }
        HttpDownloadTransfer().download("$base/redirect", File(dir, "file.part"), mapOf("Cookie" to "secret", "Authorization" to "Bearer secret", "Referer" to "$base/page"), null, null, {}, { _, _ -> })
    }
    @Test fun authenticationErrorsAreNotRetriedByTransport() {
        server.createContext("/file") { x -> x.sendResponseHeaders(403, -1); x.close() }
        val error = assertThrows(DownloadHttpException::class.java) {
            HttpDownloadTransfer().download("$base/file", File(dir, "file.part"), emptyMap(), null, null, {}, { _, _ -> })
        }
        assertFalse(error.retryable)
        assertTrue(error.message!!.contains("Access denied"))
    }
    @Test fun cancelBeforeStartDoesNotWrite() {
        val transport = HttpDownloadTransfer().apply { cancel() }
        val target = File(dir, "file.part")
        assertThrows(java.io.InterruptedIOException::class.java) {
            transport.download("$base/file", target, emptyMap(), null, null, {}, { _, _ -> })
        }
        assertFalse(target.exists())
    }
    @Test fun changedEtagRejectsMixedRepresentations() {
        server.createContext("/file") { x ->
            x.responseHeaders.add("Content-Range", "bytes 100-${payload.lastIndex}/${payload.size}")
            x.responseHeaders.add("ETag", "\"new\"")
            x.sendResponseHeaders(206, (payload.size - 100).toLong())
            x.responseBody.use { runCatching { it.write(payload, 100, payload.size - 100) } }
        }
        val target = File(dir, "file.part").apply { writeBytes(payload.copyOf(100)) }
        assertThrows(DownloadHttpException::class.java) {
            HttpDownloadTransfer().download("$base/file", target, emptyMap(), "\"old\"", null, {}, { _, _ -> })
        }
        assertEquals(100L, target.length())
    }
    @Test fun chunkedUnknownLengthIsDownloadedAndCounted() {
        server.createContext("/file") { x ->
            x.sendResponseHeaders(200, 0)
            x.responseBody.use { it.write(payload) }
        }
        val target = File(dir, "file.part")
        var finalTotal = 0L
        HttpDownloadTransfer().download("$base/file", target, emptyMap(), null, null, {}, { _, total -> finalTotal = total })
        assertArrayEquals(payload, target.readBytes())
        assertEquals(payload.size.toLong(), finalTotal)
    }
    @Test fun cancellationDuringTransferKeepsPartialFile() {
        server.createContext("/file") { x ->
            x.sendResponseHeaders(200, payload.size.toLong())
            x.responseBody.use { output -> runCatching {
                for (offset in payload.indices step 2048) {
                    output.write(payload, offset, minOf(2048, payload.size - offset))
                    output.flush()
                    Thread.sleep(8)
                }
            } }
        }
        val target = File(dir, "file.part")
        val transport = HttpDownloadTransfer()
        assertThrows(java.io.InterruptedIOException::class.java) {
            transport.download("$base/file", target, emptyMap(), null, null, {}, { bytes, _ -> if (bytes > 0) transport.cancel() })
        }
        assertTrue(target.length() > 0)
        assertTrue(target.length() < payload.size)
    }
    @Test fun malformedRangeIsRejected() {
        assertNull(HttpDownloadTransfer.parseContentRange("bytes 10-9/100"))
        assertNull(HttpDownloadTransfer.parseContentRange("bytes 0-100/100"))
        assertNull(HttpDownloadTransfer.parseContentRange("invalid"))
        assertEquals(10L to 100L, HttpDownloadTransfer.parseContentRange("bytes 10-99/100"))
    }
    @Test fun schemeHostAndPortAllDefineOrigin() {
        assertFalse(HttpDownloadTransfer.sameOrigin("https://a.test/file".toHttpUrl(), "http://a.test/file".toHttpUrl()))
        assertFalse(HttpDownloadTransfer.sameOrigin("http://a.test:80/file".toHttpUrl(), "http://a.test:81/file".toHttpUrl()))
        assertTrue(HttpDownloadTransfer.sameOrigin("https://a.test/file".toHttpUrl(), "https://a.test/other".toHttpUrl()))
    }
}
