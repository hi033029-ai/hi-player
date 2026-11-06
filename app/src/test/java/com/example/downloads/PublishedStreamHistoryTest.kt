package com.example.downloads

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PublishedStreamHistoryTest {
    @Test fun publishedFileIsRecordedOnceWithoutCredentials() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = Uri.parse("content://example.test/fresh-item")
        assertFalse(BrowserDownloads.recordPublishedStream(context, Uri.parse("https://example.test/secret"), null, "a", null, 1, "Downloads"))
        assertTrue(BrowserDownloads.recordPublishedStream(context, uri, null, "../movie.mp4", "video/mp4", 17, "Downloads"))
        assertTrue(BrowserDownloads.recordPublishedStream(context, uri, null, "another.mp4", null, 17, "Downloads"))
        val entries = BrowserDownloads.downloads.value.filter { it.savedUri == uri.toString() }
        assertEquals(1, entries.size)
        val record = entries.single()
        assertEquals(BrowserDownloadStatus.COMPLETED, record.status); assertEquals(17L, record.downloadedBytes)
        assertEquals("", record.sourceUrl); assertEquals("", record.cookie); assertEquals("", record.referer)
        assertFalse(record.fileName.contains('/'))
        val json = JSONArray(File(context.noBackupFilesDir, "browser_downloads/queue.json").readText())
        val saved = (0 until json.length()).map { json.getJSONObject(it) }.single { it.optString("savedUri") == uri.toString() }
        assertEquals("", saved.getString("url")); assertEquals("", saved.getString("cookie")); assertEquals("", saved.getString("ua"))
    }
}
