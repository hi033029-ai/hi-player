package com.example.downloads

import org.junit.Assert.*
import org.junit.Test

class BrowserDownloadModelTest {
    private fun record(status: BrowserDownloadStatus) = BrowserDownload(1, "video.mp4", "https://example.test/video", status = status)
    @Test fun pausedRowsRemainInQueueButDoNotRequireForegroundService() {
        assertTrue(record(BrowserDownloadStatus.PAUSED).isActive)
        assertFalse(record(BrowserDownloadStatus.PAUSED).needsService)
        assertTrue(record(BrowserDownloadStatus.QUEUED).needsService)
        assertTrue(record(BrowserDownloadStatus.RUNNING).needsService)
        assertFalse(record(BrowserDownloadStatus.COMPLETED).isActive)
        assertFalse(record(BrowserDownloadStatus.CANCELLED).isActive)
    }
    @Test fun etaIsOnlyShownWhenMeaningful() {
        val item = record(BrowserDownloadStatus.RUNNING).copy(downloadedBytes = 100, totalBytes = 1100, speedBytesPerSecond = 100)
        assertEquals(10L, item.etaSeconds)
        assertNull(item.copy(totalBytes = 0).etaSeconds)
        assertNull(item.copy(speedBytesPerSecond = 0).etaSeconds)
    }
    @Test fun fileNamesCannotEscapeDownloadFolder() {
        assertFalse(BrowserDownloads.safeFileName("../../bad\\file\u0000.mp4").contains('/'))
        assertFalse(BrowserDownloads.safeFileName("../../bad\\file\u0000.mp4").contains('\\'))
        assertFalse(BrowserDownloads.safeFileName("x\ny").contains('\n'))
        assertEquals("download", BrowserDownloads.safeFileName("..."))
        assertEquals(120, BrowserDownloads.safeFileName("a".repeat(500)).length)
    }
}
