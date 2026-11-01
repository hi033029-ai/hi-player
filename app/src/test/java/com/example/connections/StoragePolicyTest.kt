package com.example.connections

import org.junit.Assert.*
import org.junit.Test

/** Authored only. Run with the next explicitly approved validation/build. */
class StoragePolicyTest {
    @Test fun graphPagingCannotForwardTokensToOtherOriginsOrResources() {
        assertTrue(StoragePolicy.graph("https://graph.microsoft.com/v1.0/me/drive/root/children?\$skiptoken=abc"))
        assertTrue(StoragePolicy.graph("https://graph.microsoft.com:443/v1.0/me/drive/items/ABC/children"))
        listOf("http://graph.microsoft.com/v1.0/me/drive/root/children", "https://graph.microsoft.com.evil/v1.0/me/drive/root/children",
            "https://other.test/v1.0/me/drive/root/children", "https://graph.microsoft.com:8443/v1.0/me/drive/root/children",
            "https://user:pass@graph.microsoft.com/v1.0/me/drive/root/children", "https://graph.microsoft.com/v1.0/me/messages",
            "https://graph.microsoft.com/v1.0/me/drive/../messages").forEach { assertFalse(StoragePolicy.graph(it)) }
    }
    @Test fun signedDownloadsCannotUsePlaintextOrCredentialUrls() {
        assertTrue(StoragePolicy.download("https://content.example/file?temporary=opaque"))
        listOf("http://content.example/file", "file:///private", "https://user:password@content.example/file", "https://content.example:8443/file", "https://content.example/file#token").forEach {
            assertFalse(StoragePolicy.download(it))
        }
    }
    @Test fun smbPathsStayRelativeToConfiguredShare() {
        assertEquals("Videos\\Movie.mkv", StoragePolicy.smbPath("Videos/Movie.mkv"))
        assertEquals("Music\\song.flac", StoragePolicy.smbPath("Music\\song.flac"))
        listOf("../file", "sub/../file", "/root/file", "\\server\\share", "C:/file", "sub/./file", "sub/fi\u0000le").forEach {
            assertTrue(runCatching { StoragePolicy.smbPath(it) }.isFailure)
        }
    }
    @Test fun mimeClassificationDoesNotInventPlaybackForUnknownFiles() {
        assertEquals("video/x-matroska", StoragePolicy.mime("MOVIE.MKV"))
        assertEquals("audio/flac", StoragePolicy.mime("song.flac"))
        assertFalse(StorageFile("id", "notes.pdf", false, StoragePolicy.mime("notes.pdf")).playable)
        assertFalse(StorageFile("id", "folder.mp4", true, StoragePolicy.mime("folder.mp4")).playable)
    }
}
