package com.example.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PendingDownloadFormatTest {
    @Test
    fun `generic pending title is replaced with actual temporary filename`() {
        assertEquals(
            "The.Movie.mkv",
            PendingDownloadFormat.displayName("Pending video.mp4", "/storage/emulated/0/Download/The.Movie.mkv.part"),
        )
    }

    @Test
    fun `real media store title is preserved while pending suffix is removed`() {
        assertEquals(
            "The.Movie.mkv",
            PendingDownloadFormat.displayName("The.Movie.mkv.part", "/storage/emulated/0/Download/other-name.mp4.part"),
        )
    }

    @Test
    fun `missing provider name falls back to path name`() {
        assertEquals(
            "The.Movie.mp4",
            PendingDownloadFormat.displayName(null, "/storage/emulated/0/Download/The.Movie.mp4.crdownload"),
        )
    }

    @Test
    fun `non-placeholder title is not replaced by unrelated path`() {
        assertEquals(
            "Pending Video Recap.mp4",
            PendingDownloadFormat.displayName("Pending Video Recap.mp4", "/storage/emulated/0/Download/actual-file.mp4.part"),
        )
    }
}
