package com.example.player

import org.junit.Assert.*
import org.junit.Test

class OwnedDownloadRegistryTest {
    @Test fun publicScannerCannotForgetPrivatePartialDownload() {
        val path = "/private/test-${System.nanoTime()}.mp4.part"
        try {
            DownloadingFilesRegistry.markOwnedDownloading(path, 100)
            DownloadingFilesRegistry.markMissingPendingFileDownloadsComplete(emptySet())
            assertTrue(DownloadingFilesRegistry.isDownloading(path))
            DownloadingFilesRegistry.completeOwned(path, "content://media/external/downloads/42")
            assertFalse(DownloadingFilesRegistry.isDownloading(path))
            assertEquals("content://media/external/downloads/42", DownloadingFilesRegistry.completedUri(path))
        } finally { DownloadingFilesRegistry.completeOwned(path) }
    }
    @Test fun ownedByteProgressRemainsAvailableForLibraryAndPlayer() {
        val path = "/private/test-${System.nanoTime()}.mkv.part"
        try {
            DownloadingFilesRegistry.markOwnedDownloading(path, 2048)
            assertEquals(2048L, DownloadingFilesRegistry.progress(path)!!.downloadedBytes)
            assertTrue(DownloadingFilesRegistry.isActivelyGrowing(path))
        } finally { DownloadingFilesRegistry.completeOwned(path) }
    }
}
