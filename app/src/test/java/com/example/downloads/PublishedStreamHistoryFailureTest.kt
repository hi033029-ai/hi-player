package com.example.downloads

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PublishedStreamHistoryFailureTest {
    @Test fun failedJournalWriteDoesNotLeaveAnInMemoryRecord() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val journal = File(context.noBackupFilesDir, "browser_downloads/queue.json")
        journal.parentFile!!.mkdirs()
        assertTrue(journal.mkdir())
        File(journal, "keep").writeText("not empty") // Prevent replacement by AtomicFile.
        val uri = Uri.parse("content://test/unrecordable")
        assertFalse(BrowserDownloads.recordPublishedStream(context, uri, null, "file.txt", "text/plain", 3, "Downloads"))
        assertTrue(BrowserDownloads.downloads.value.none { it.savedUri == uri.toString() })
    }
}
