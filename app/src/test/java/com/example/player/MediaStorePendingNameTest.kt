package com.example.player

import com.example.model.PendingDownloadFormat

import org.junit.Assert.*
import org.junit.Test

class MediaStorePendingNameTest {
    @Test fun detectsOnlyAndroidHiddenPendingNames() {
        assertTrue(PendingDownloadFormat.isMediaStorePendingName(".pending-1795105388-movie.mkv"))
        assertFalse(PendingDownloadFormat.isMediaStorePendingName("movie.mkv"))
        assertFalse(PendingDownloadFormat.isMediaStorePendingName(".pending-not-a-number-movie.mkv"))
        assertFalse(PendingDownloadFormat.isMediaStorePendingName(".pending-123-"))
    }
}
