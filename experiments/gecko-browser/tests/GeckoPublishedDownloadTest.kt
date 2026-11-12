package com.example.browser.gecko

import android.net.Uri
import com.example.player.DownloadingFilesRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
class GeckoPublishedDownloadTest {
    private fun source() = object : GeckoDownloadSource {
        override val mimeType = "video/mp4"
        override val contentDisposition = "attachment; filename=\"movie.mp4\""
        override val contentLength = 3L
        override val statusCode = 200
        override fun takeBody() = ByteArrayInputStream(byteArrayOf(1, 2, 3))
        override fun close() = Unit
    }
    private class Published : GeckoPublishedDestination {
        override var publishedUri: Uri? = null
        override val treeUri: Uri? = null
        override fun open() = ByteArrayOutputStream()
        override fun commit() { publishedUri = Uri.parse("content://test/item") }
        override fun abort() = Unit
    }
    @Test fun recordsOnlyAfterSuccessfulPublication() {
        var calls = 0
        val target = Published()
        val controller = GeckoDownloadController(Executor { it.run() }, { it() }, onPublished = { destination, name, mime, bytes, location ->
            calls++; assertEquals("content://test/item", destination.publishedUri.toString())
            assertEquals("movie.mp4", name); assertEquals("video/mp4", mime); assertEquals(3L, bytes); assertEquals("Downloads", location); true
        }) { _, _ -> target }
        controller.offer(source()); assertEquals(0, calls); controller.approve()
        assertEquals(1, calls); assertEquals(true, (controller.state as GeckoDownloadState.Finished).recorded)
    }
    @Test fun privateTabDownloadIsNotRecordedInHistory() {
        var calls = 0
        val controller = GeckoDownloadController(Executor { it.run() }, { it() }, onPublished = { _, _, _, _, _ -> calls++; true }) { _, _ -> Published() }
        controller.offer(source(), recordInHistory = false); controller.approve()
        assertEquals(0, calls)
        val result = controller.state as GeckoDownloadState.Finished
        assertEquals(GeckoDownloadOutcome.Complete(3), result.outcome); assertNull(result.recorded)
    }
    @Test fun historyFailureDoesNotPretendTheSavedFileFailed() {
        val controller = GeckoDownloadController(Executor { it.run() }, { it() }, onPublished = { _, _, _, _, _ -> false }) { _, _ -> Published() }
        controller.offer(source()); controller.approve()
        val result = controller.state as GeckoDownloadState.Finished
        assertEquals(GeckoDownloadOutcome.Complete(3), result.outcome); assertEquals(false, result.recorded)
    }
    @Test fun localGrowingUriIsOfferedOnceAndClearedAfterPublish() {
        val uri = Uri.parse("content://test/growing-item")
        val target = object : GeckoPublishedDestination {
            override val treeUri: Uri? = null
            override var publishedUri: Uri? = null
            override val writingUri: Uri get() = uri
            override fun open() = ByteArrayOutputStream()
            override fun commit() { publishedUri = uri }
            override fun abort() = Unit
        }
        val source = object : GeckoDownloadSource {
            override val mimeType = "video/mp4"
            override val contentDisposition = "attachment; filename=movie.mp4"
            override val contentLength = 3L * 1024 * 1024
            override val statusCode = 200
            override fun takeBody() = ByteArrayInputStream(ByteArray(contentLength.toInt()))
            override fun close() = Unit
        }
        var calls = 0
        var sawGrowing = false
        val controller = GeckoDownloadController(Executor { it.run() }, { it() }) { _, _ -> target }
        controller.offer(source)
        controller.approve(onPlayable = { local ->
            calls++
            assertEquals(uri.toString(), local)
            sawGrowing = DownloadingFilesRegistry.isDownloading(local)
        })
        assertEquals(1, calls)
        assertTrue(sawGrowing)
        assertFalse(DownloadingFilesRegistry.isDownloading(uri.toString()))
        assertEquals(uri.toString(), (controller.state as GeckoDownloadState.Finished).publishedUri)
    }
    @Test fun smallFilePlaysAfterPublication() {
        val controller = GeckoDownloadController(Executor { it.run() }, { it() }) { _, _ -> Published() }
        var local: String? = null
        controller.offer(source())
        controller.approve(onPlayable = { local = it })
        assertEquals("content://test/item", local)
    }

}
