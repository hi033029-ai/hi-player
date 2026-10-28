package com.example.downloads

import com.sun.net.httpserver.HttpServer
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress

class DownloadLinkProbeTest {
    @Test fun previewOnlyUsesHeadAndReturnsActualFileInformation() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var method: String? = null
        server.createContext("/video") { x ->
            method = x.requestMethod
            x.responseHeaders.add("Content-Type", "video/x-matroska")
            x.responseHeaders.add("Content-Disposition", "attachment; filename=\"Movie.mkv\"")
            x.responseHeaders.add("Content-Length", "1000000")
            x.sendResponseHeaders(200, -1); x.close()
        }
        server.start()
        try {
            val request = BrowserDownloadRequest("http://127.0.0.1:${server.address.port}/video", "Test", "", "application/octet-stream", "download.bin")
            val result = DownloadLinkProbe.inspect(request, "")
            assertEquals("HEAD", method)
            assertEquals("Movie.mkv", result.fileName)
            assertEquals("video/x-matroska", result.mimeType)
            assertEquals(1000000L, result.totalBytes)
        } finally { server.stop(0) }
    }
    @Test fun unsupportedHeadDoesNotStartAGetDownload() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var calls = 0
        server.createContext("/video") { x -> calls++; assertEquals("HEAD", x.requestMethod); x.sendResponseHeaders(405, -1); x.close() }
        server.start()
        try {
            val request = BrowserDownloadRequest("http://127.0.0.1:${server.address.port}/video", "Test", "", null, "download.bin")
            assertEquals(request, DownloadLinkProbe.inspect(request, ""))
            assertEquals(1, calls)
        } finally { server.stop(0) }
    }
}
