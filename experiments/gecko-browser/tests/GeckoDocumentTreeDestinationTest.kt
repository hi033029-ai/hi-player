package com.example.browser.gecko

import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
class GeckoDocumentTreeDestinationTest {
    private class Backend : GeckoDocumentTreeBackend {
        val uri = Uri.parse("content://provider/tree/item")
        val output = ByteArrayOutputStream()
        var name = ""
        var deleted = false
        var failOpen = false
        override fun create(name: String, mime: String): Uri { this.name = name; return uri }
        override fun open(uri: Uri) = if (failOpen) null else output
        override fun delete(uri: Uri) { assertEquals(this.uri, uri); deleted = true }
    }
    @Test fun successfulCopyPublishesUniquelyNamedDocument() {
        val backend = Backend()
        val dest = GeckoDocumentTreeDestination(backend, "../movie.mp4", "video/mp4") { "abc-123" }
        assertEquals(GeckoDownloadOutcome.Complete(3), transferGeckoDownload(ByteArrayInputStream(byteArrayOf(1, 2, 3)), dest, GeckoDownloadCancellation()))
        assertEquals(backend.uri, dest.publishedUri); assertFalse(backend.deleted)
        assertTrue(backend.name.endsWith("-abc123.mp4")); assertFalse(backend.name.contains('/'))
        assertArrayEquals(byteArrayOf(1, 2, 3), backend.output.toByteArray())
        dest.abort(); assertFalse(backend.deleted)
    }
    @Test fun failedOpenDeletesOnlyCreatedDocument() {
        val backend = Backend().apply { failOpen = true }
        val dest = GeckoDocumentTreeDestination(backend, "report.txt", "text/plain") { "1" }
        assertEquals(GeckoDownloadOutcome.Failed, transferGeckoDownload(ByteArrayInputStream(byteArrayOf(1)), dest, GeckoDownloadCancellation()))
        assertTrue(backend.deleted); assertNull(dest.publishedUri)
    }
    @Test fun cancellationDeletesOnlyCreatedDocument() {
        val backend = Backend(); val cancel = GeckoDownloadCancellation()
        val dest = GeckoDocumentTreeDestination(backend, "report", null) { "2" }
        val result = transferGeckoDownload(ByteArrayInputStream(ByteArray(100)), dest, cancel) { cancel.cancel() }
        assertEquals(GeckoDownloadOutcome.Cancelled, result); assertTrue(backend.deleted); assertNull(dest.publishedUri)
    }
    @Test fun cannotReuseDestination() {
        val backend = Backend(); val dest = GeckoDocumentTreeDestination(backend, "a", null) { "3" }
        dest.open().close(); dest.commit()
        try { dest.open(); fail("should not reopen") } catch (_: IllegalStateException) { }
    }
}
