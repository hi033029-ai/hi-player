package com.example.downloads

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ActiveDownloadLibraryTest {
    @Test fun partialVideoIsDiscoverableWhileRunningAndPausedWithoutOpeningTheBrowser() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val executor = Executors.newCachedThreadPool()
        server.executor = executor
        server.createContext("/movie.mkv") { x ->
            x.responseHeaders.add("Content-Type", "video/x-matroska")
            x.sendResponseHeaders(200, 1024 * 1024L)
            x.responseBody.use { out -> runCatching { repeat(512) { out.write(ByteArray(2048)); out.flush(); Thread.sleep(10) } } }
        }
        server.start()
        var id = -1L
        fun command(action: String) {
            val latch = CountDownLatch(1)
            BrowserDownloads.execute(context, Intent().setAction(action).putExtra("id", id)) { latch.countDown() }
            assertTrue(latch.await(10, TimeUnit.SECONDS))
        }
        try {
            val url = "http://127.0.0.1:${server.address.port}/movie.mkv"
            val latch = CountDownLatch(1)
            BrowserDownloads.execute(context, Intent().setAction(BrowserDownloads.ACTION_ADD).putExtra("url", url).putExtra("name", "movie.mkv").putExtra("mime", "video/x-matroska")) { latch.countDown() }
            assertTrue(latch.await(10, TimeUnit.SECONDS))
            val deadline = System.currentTimeMillis() + 8000
            while (System.currentTimeMillis() < deadline && BrowserDownloads.downloads.value.none { it.sourceUrl == url && it.downloadedBytes > 0 }) Thread.sleep(50)
            val record = BrowserDownloads.downloads.value.first { it.sourceUrl == url }
            id = record.id
            val item = BrowserDownloads.libraryVideoItems(context).first { it.id == id }
            assertTrue(item.isPartialDownload)
            assertEquals("movie.mkv", item.title)
            assertEquals("file", item.uri.scheme)
            assertTrue(item.downloadedBytes > 0)
            command(BrowserDownloads.ACTION_PAUSE)
            assertTrue(BrowserDownloads.libraryVideoItems(context).any { it.id == id && it.isPartialDownload })
            command(BrowserDownloads.ACTION_CANCEL)
            assertFalse(BrowserDownloads.libraryVideoItems(context).any { it.id == id })
        } finally {
            if (id > 0) command(BrowserDownloads.ACTION_CANCEL)
            server.stop(0); executor.shutdownNow()
        }
    }
}
