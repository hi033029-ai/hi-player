package com.example.downloads

import org.junit.Assert.*
import org.junit.Test

class DownloadFileResolverTest {
    private fun mp4(brand: String = "isom") = byteArrayOf(0, 0, 0, 24) + "ftyp${brand}0000".toByteArray(Charsets.ISO_8859_1)
    @Test fun genericBinaryMp4IsRenamedFromActualBytes() {
        val result = DownloadFileResolver.resolve("videoplayback.bin", "https://cdn.test/stream?id=1", "application/octet-stream", prefix = mp4())
        assertEquals("videoplayback.mp4", result.fileName)
        assertEquals("video/mp4", result.mimeType)
        assertTrue(result.isVideo)
    }
    @Test fun staleGenericMimeDoesNotHideCorrectResponseType() {
        val result = DownloadFileResolver.resolve("download.bin", "https://cdn.test/stream", "video/mp4; charset=binary")
        assertEquals("download.mp4", result.fileName)
        assertTrue(result.isVideo)
    }
    @Test fun responseDispositionKeepsRealName() {
        val result = DownloadFileResolver.resolve("download.bin", "https://cdn.test/get", "video/mp4", "attachment; filename=\"A movie.mp4\"")
        assertEquals("A movie.mp4", result.fileName)
    }
    @Test fun utf8ExtendedDispositionTakesPrecedence() {
        val result = DownloadFileResolver.resolve("download.bin", "https://cdn.test/get", "video/mp4", "attachment; filename=wrong.bin; filename*=UTF-8''My%20video%20%2B%20test.mp4")
        assertEquals("My video + test.mp4", result.fileName)
    }
    @Test fun finalRedirectFilenameCanSupplyExtension() {
        val result = DownloadFileResolver.resolve("download.bin", "https://cdn.test/path/Clip.webm?token=123", "application/octet-stream")
        assertEquals("Clip.webm", result.fileName)
        assertEquals("video/webm", result.mimeType)
    }
    @Test fun filenameQueryIsRecognized() {
        val result = DownloadFileResolver.resolve("download.bin", "https://cdn.test/file?filename=Movie.mkv", "application/octet-stream")
        assertEquals("Movie.mkv", result.fileName)
        assertEquals("video/x-matroska", result.mimeType)
    }
    @Test fun mkvAndWebmAreDistinguishedByDocType() {
        val ebml = byteArrayOf(0x1a, 0x45, 0xdf.toByte(), 0xa3.toByte())
        val mkv = DownloadFileResolver.resolve("movie.bin", "https://x.test/get", null, prefix = ebml + "matroska".toByteArray())
        val webm = DownloadFileResolver.resolve("movie.bin", "https://x.test/get", null, prefix = ebml + "webm".toByteArray())
        assertEquals("movie.mkv", mkv.fileName)
        assertEquals("movie.webm", webm.fileName)
    }
    @Test fun aviSignatureOverridesWrongExtension() {
        val result = DownloadFileResolver.resolve("movie.mp4", "https://x.test/get", "video/mp4", prefix = "RIFF0000AVI ".toByteArray())
        assertEquals("movie.avi", result.fileName)
        assertEquals("video/x-msvideo", result.mimeType)
    }
    @Test fun audioOnlyMp4IsNotRegisteredAsVideo() {
        val result = DownloadFileResolver.resolve("song.bin", "https://x.test/get", "application/octet-stream", prefix = mp4("M4A "))
        assertEquals("song.m4a", result.fileName)
        assertEquals("audio/mp4", result.mimeType)
        assertFalse(result.isVideo)
    }
    @Test fun retrieverDisambiguatesAudioOnlyIsom() {
        val result = DownloadFileResolver.resolve("song.bin", "https://x.test/get", null, prefix = mp4(), detectedMime = "audio/mp4")
        assertEquals("song.m4a", result.fileName)
        assertFalse(result.isVideo)
    }
    @Test fun heicAndAvifAreNotMistakenForMp4() {
        assertFalse(DownloadFileResolver.resolve("photo.bin", "https://x.test/get", null, prefix = mp4("heic")).isVideo)
        val avif = DownloadFileResolver.resolve("photo.bin", "https://x.test/get", null, prefix = mp4("avif"))
        assertEquals("photo.avif", avif.fileName)
        assertFalse(avif.isVideo)
    }
    @Test fun htmlErrorResponseDoesNotBecomeAVideo() {
        val result = DownloadFileResolver.resolve("movie.bin", "https://x.test/get", "video/mp4", prefix = "<!DOCTYPE html><html>Forbidden".toByteArray())
        assertEquals("movie.html", result.fileName)
        assertFalse(result.isVideo)
    }
    @Test fun arbitraryBytesAreNotBlindlyRenamedMp4() {
        val result = DownloadFileResolver.resolve("data.bin", "https://x.test/get", "application/octet-stream", prefix = byteArrayOf(1, 2, 3, 4))
        assertEquals("data.bin", result.fileName)
        assertFalse(result.isVideo)
    }
    @Test fun filenameIsSanitizedAndExtensionSurvivesTruncation() {
        val result = DownloadFileResolver.resolve("download.bin", "https://x.test/get", "video/mp4", "attachment; filename=\"../../${"a".repeat(140)}.mp4\"")
        assertFalse(result.fileName.contains('/'))
        assertTrue(result.fileName.endsWith(".mp4"))
        assertEquals(120, result.fileName.length)
    }
    @Test fun noDuplicateExtensionForMp4Bin() {
        assertEquals("clip.mp4", DownloadFileResolver.resolve("clip.mp4.bin", "https://x.test/get", null, prefix = mp4()).fileName)
    }
}
