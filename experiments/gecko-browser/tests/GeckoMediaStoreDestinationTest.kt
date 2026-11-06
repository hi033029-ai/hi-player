package com.example.browser.gecko

import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GeckoMediaStoreDestinationTest {
    private class Backend : PendingMediaStore {
        val uri = Uri.parse("content://downloads/1")
        val output = ByteArrayOutputStream()
        var name = ""
        var mime = ""
        var created = 0
        var published = false
        var deleted = false
        var failOpen = false
        var failPublish = false
        override fun create(name: String, mime: String): Uri { created++; this.name = name; this.mime = mime; return uri }
        override fun open(uri: Uri): OutputStream? = if (failOpen) null else output
        override fun publish(uri: Uri): Boolean { if (failPublish) return false; published = true; return true }
        override fun delete(uri: Uri) { deleted = true }
    }
    @Test fun approvalThenSuccessfulCopyPublishesOneFile() {
        val backend = Backend()
        val target = GeckoMediaStoreDestination(backend, "../song.mp3", "audio/mpeg; charset=utf-8")
        assertEquals(0, backend.created)
        assertEquals(GeckoDownloadOutcome.Complete(3), transferGeckoDownload(ByteArrayInputStream(byteArrayOf(1, 2, 3)), target, GeckoDownloadCancellation()))
        assertEquals(1, backend.created)
        assertFalse(backend.name.contains('/'))
        assertEquals("audio/mpeg", backend.mime)
        assertArrayEquals(byteArrayOf(1, 2, 3), backend.output.toByteArray())
        assertTrue(backend.published)
        assertFalse(backend.deleted)
        assertEquals(backend.uri, target.publishedUri)
    }
    @Test fun cancelledBeforeCopyNeverCreatesAFile() {
        val backend = Backend()
        val target = GeckoMediaStoreDestination(backend, "song.mp3", null)
        val cancel = GeckoDownloadCancellation().apply { cancel() }
        assertEquals(GeckoDownloadOutcome.Cancelled, transferGeckoDownload(ByteArrayInputStream(byteArrayOf(1)), target, cancel))
        assertEquals(0, backend.created)
        assertNull(target.publishedUri)
    }
    @Test fun failedOpenOrPublishDeletesPendingEntry() {
        for (openFailure in listOf(true, false)) {
            val backend = Backend().apply { failOpen = openFailure; failPublish = !openFailure }
            val target = GeckoMediaStoreDestination(backend, "file", null)
            assertEquals(GeckoDownloadOutcome.Failed, transferGeckoDownload(ByteArrayInputStream(byteArrayOf(1)), target, GeckoDownloadCancellation()))
            assertTrue(backend.deleted)
            assertFalse(backend.published)
            assertNull(target.publishedUri)
        }
    }
    @Test fun readFailureDeletesPendingEntry() {
        val backend = Backend()
        val target = GeckoMediaStoreDestination(backend, "file", null)
        val input = object : java.io.InputStream() { override fun read(): Int = throw IOException() }
        assertEquals(GeckoDownloadOutcome.Failed, transferGeckoDownload(input, target, GeckoDownloadCancellation()))
        assertTrue(backend.deleted)
        assertFalse(backend.published)
    }
}
