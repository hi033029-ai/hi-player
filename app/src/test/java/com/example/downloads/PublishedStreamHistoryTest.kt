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
    @Test fun activeGeckoTransferAppearsImmediatelyAndPublishesInPlace() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val id = BrowserDownloads.beginGeckoTransfer(context, "movie.mp4", "application/octet-stream", 100, "Downloads")
        val active = BrowserDownloads.downloads.value.first { it.id == id }
        assertEquals(BrowserDownloadStatus.RUNNING, active.status)
        assertTrue(active.ephemeral)
        BrowserDownloads.updateGeckoTransfer(id, "movie.mp4", "application/octet-stream", 60, 100, 30)
        val progress = BrowserDownloads.downloads.value.first { it.id == id }
        assertEquals(60L, progress.downloadedBytes)
        assertEquals(30L, progress.speedBytesPerSecond)
        assertTrue(progress.isVideo)
        val journal = File(context.noBackupFilesDir, "browser_downloads/queue.json")
        val beforePublish = if (journal.isFile) JSONArray(journal.readText()) else JSONArray()
        assertFalse(beforePublish
            .let { json -> (0 until json.length()).any { json.getJSONObject(it).optLong("id") == id } })

        val uri = Uri.parse("content://example.test/gecko-movie")
        assertTrue(BrowserDownloads.recordPublishedStream(context, uri, null, "movie.mp4", "application/octet-stream", 100, "Downloads", id))
        val complete = BrowserDownloads.downloads.value.first { it.id == id }
        assertEquals(BrowserDownloadStatus.COMPLETED, complete.status)
        assertEquals(uri.toString(), complete.savedUri)
        assertFalse(complete.ephemeral)
        assertTrue(complete.isVideo)
        val json = JSONArray(journal.readText())
        assertTrue((0 until json.length()).any { json.getJSONObject(it).optLong("id") == id })
    }

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
