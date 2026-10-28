package com.example.downloads

import com.sun.net.httpserver.HttpServer
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.util.Base64

class DownloadedVideoIntegrationTest {
    @Test fun playableMp4FromOctetStreamEndpointGetsCorrectFilenameAndVideoType() {
        val data = Base64.getDecoder().decode(FIXTURE)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val directory = java.nio.file.Files.createTempDirectory("hi-real-video").toFile()
        server.createContext("/videoplayback") { exchange ->
            exchange.responseHeaders.add("Content-Type", "application/octet-stream")
            exchange.sendResponseHeaders(200, data.size.toLong())
            exchange.responseBody.use { it.write(data) }
        }
        server.start()
        try {
            val url = "http://127.0.0.1:${server.address.port}/videoplayback?token=example"
            val partial = File(directory, "download.part")
            var mime: String? = null
            HttpDownloadTransfer().download(url, partial, emptyMap(), null, null, { mime = it.mime }, { _, _ -> })
            val result = DownloadFileResolver.resolve("videoplayback.bin", url, mime, prefix = partial.readBytes())
            assertArrayEquals(data, partial.readBytes())
            assertEquals("videoplayback.mp4", result.fileName)
            assertEquals("video/mp4", result.mimeType)
            assertTrue(result.isVideo)
        } finally { server.stop(0); directory.deleteRecursively() }
    }
    companion object {
        // Generated one-frame 32x32 H.264 MP4. This is media content, not a header-only mock.
        private const val FIXTURE = "AAAAIGZ0eXBpc29tAAACAGlzb21pc28yYXZjMW1wNDEAAAMVbW9vdgAAAGxtdmhkAAAAAAAAAAAAAAAAAAAD6AAAA+gAAQAAAQAAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAAAAAAABAAAAAAAAAAAAAAAAAABAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAgAAAkB0cmFrAAAAXHRraGQAAAADAAAAAAAAAAAAAAABAAAAAAAAA+gAAAAAAAAAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAAAAAAABAAAAAAAAAAAAAAAAAABAAAAAACAAAAAgAAAAAAAkZWR0cwAAABxlbHN0AAAAAAAAAAEAAAPoAAAAAAABAAAAAAG4bWRpYQAAACBtZGhkAAAAAAAAAAAAAAAAAABAAAAAQABVxAAAAAAALWhkbHIAAAAAAAAAAHZpZGUAAAAAAAAAAAAAAABWaWRlb0hhbmRsZXIAAAABY21pbmYAAAAUdm1oZAAAAAEAAAAAAAAAAAAAACRkaW5mAAAAHGRyZWYAAAAAAAAAAQAAAAx1cmwgAAAAAQAAASNzdGJsAAAAv3N0c2QAAAAAAAAAAQAAAK9hdmMxAAAAAAAAAAEAAAAAAAAAAAAAAAAAAAAAACAAIABIAAAASAAAAAAAAAABFExhdmM2MS4zLjEwMCBsaWJ4MjY0AAAAAAAAAAAAAAAAGP//AAAANWF2Y0MBZAAK/+EAGGdkAAqs2UlsBEAAAAMAQAAAAwCDxIllgAEABmjr48siwP34+AAAAAAQcGFzcAAAAAEAAAABAAAAFGJ0cnQAAAAAAAAWUAAAFlAAAAAYc3R0cwAAAAAAAAABAAAAAQAAQAAAAAAcc3RzYwAAAAAAAAABAAAAAQAAAAEAAAABAAAAFHN0c3oAAAAAAAACygAAAAEAAAAUc3RjbwAAAAAAAAABAAADRQAAAGF1ZHRhAAAAWW1ldGEAAAAAAAAAIWhkbHIAAAAAAAAAAG1kaXJhcHBsAAAAAAAAAAAAAAAALGlsc3QAAAAkqXRvbwAAABxkYXRhAAAAAQAAAABMYXZmNjEuMS4xMDAAAAAIZnJlZQAAAtJtZGF0AAACrQYF//+p3EXpvebZSLeWLNgg2SPu73gyNjQgLSBjb3JlIDE2NCByMzE5MSA0NjEzYWMzIC0gSC4yNjQvTVBFRy00IEFWQyBjb2RlYyAtIENvcHlsZWZ0IDIwMDMtMjAyNCAtIGh0dHA6Ly93d3cudmlkZW9sYW4ub3JnL3gyNjQuaHRtbCAtIG9wdGlvbnM6IGNhYmFjPTEgcmVmPTMgZGVibG9jaz0xOjA6MCBhbmFseXNlPTB4MzoweDExMyBtZT1oZXggc3VibWU9NyBwc3k9MSBwc3lfcmQ9MS4wMDowLjAwIG1peGVkX3JlZj0xIG1lX3JhbmdlPTE2IGNocm9tYV9tZT0xIHRyZWxsaXM9MSA4eDhkY3Q9MSBjcW09MCBkZWFkem9uZT0yMSwxMSBmYXN0X3Bza2lwPTEgY2hyb21hX3FwX29mZnNldD0tMiB0aHJlYWRzPTEgbG9va2FoZWFkX3RocmVhZHM9MSBzbGljZWRfdGhyZWFkcz0wIG5yPTAgZGVjaW1hdGU9MSBpbnRlcmxhY2VkPTAgYmx1cmF5X2NvbXBhdD0wIGNvbnN0cmFpbmVkX2ludHJhPTAgYmZyYW1lcz0zIGJfcHlyYW1pZD0yIGJfYWRhcHQ9MSBiX2JpYXM9MCBkaXJlY3Q9MSB3ZWlnaHRiPTEgb3Blbl9nb3A9MCB3ZWlnaHRwPTIga2V5aW50PTI1MCBrZXlpbnRfbWluPTEgc2NlbmVjdXQ9NDAgaW50cmFfcmVmcmVzaD0wIHJjX2xvb2thaGVhZD00MCByYz1jcmYgbWJ0cmVlPTEgY3JmPTIzLjAgcWNvbXA9MC42MCBxcG1pbj0wIHFwbWF4PTY5IHFwc3RlcD00IGlwX3JhdGlvPTEuNDAgYXE9MToxLjAwAIAAAAAVZYiEABX//vfJ78Cm69vetb9gAQD5"
    }
}
