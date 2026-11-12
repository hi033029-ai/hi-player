package com.example.browser.gecko

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import org.junit.Assert.*
import org.junit.Test

class GeckoDownloadTest {
    private class Destination : GeckoDownloadDestination {
        val output = ByteArrayOutputStream()
        var committed = false
        var aborted = false
        var failCommit = false
        override fun open(): OutputStream = output
        override fun commit() { if (failCommit) throw IOException(); committed = true }
        override fun abort() { aborted = true }
    }
    private class ClosingStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var closed = false
        override fun close() { closed = true; super.close() }
    }

    @Test fun approvedDownloadCopiesExactlyAndCommits() {
        val source = ClosingStream(ByteArray(100_000) { it.toByte() })
        val target = Destination()
        val updates = mutableListOf<Long>()
        val result = transferGeckoDownload(source, target, GeckoDownloadCancellation()) { updates += it }
        assertEquals(GeckoDownloadOutcome.Complete(100_000), result)
        assertTrue(source.closed)
        assertTrue(target.committed)
        assertFalse(target.aborted)
        assertEquals(100_000, target.output.size())
        assertEquals(100_000L, updates.last())
    }
    @Test fun shortResponseIsAbortedRatherThanPublishedAsComplete() {
        val target = Destination()
        val result = transferGeckoDownload(ByteArrayInputStream(byteArrayOf(1, 2, 3)), target,
            GeckoDownloadCancellation(), expectedLength = 10)
        assertEquals(GeckoDownloadOutcome.Failed, result)
        assertTrue(target.aborted)
        assertFalse(target.committed)
    }
    @Test fun responseWithMatchingLengthCommits() {
        val target = Destination()
        val result = transferGeckoDownload(ByteArrayInputStream(byteArrayOf(1, 2, 3)), target,
            GeckoDownloadCancellation(), expectedLength = 3)
        assertEquals(GeckoDownloadOutcome.Complete(3), result)
        assertTrue(target.committed)
    }
    @Test fun cancellationBeforeStartClosesSourceAndAborts() {
        val source = ClosingStream(byteArrayOf(1))
        val target = Destination()
        val cancel = GeckoDownloadCancellation().apply { cancel() }
        assertEquals(GeckoDownloadOutcome.Cancelled, transferGeckoDownload(source, target, cancel))
        assertTrue(source.closed)
        assertTrue(target.aborted)
        assertFalse(target.committed)
    }
    @Test fun cancellationDuringTransferDeletesPartialOutput() {
        val source = ClosingStream(ByteArray(100_000))
        val target = Destination()
        val cancel = GeckoDownloadCancellation()
        assertEquals(GeckoDownloadOutcome.Cancelled, transferGeckoDownload(source, target, cancel) { cancel.cancel() })
        assertTrue(source.closed)
        assertTrue(target.aborted)
        assertFalse(target.committed)
    }
    @Test fun readOrCommitFailureAbortsInsteadOfPublishingPartialFile() {
        val broken = object : InputStream() { override fun read(): Int = throw IOException() }
        val first = Destination()
        assertEquals(GeckoDownloadOutcome.Failed, transferGeckoDownload(broken, first, GeckoDownloadCancellation()))
        assertTrue(first.aborted)
        val second = Destination().apply { failCommit = true }
        assertEquals(GeckoDownloadOutcome.Failed, transferGeckoDownload(ByteArrayInputStream(byteArrayOf(1)), second, GeckoDownloadCancellation()))
        assertTrue(second.aborted)
        assertFalse(second.committed)
    }
    @Test fun cancellationClosesBlockedInputAndRemovesPartialOutput() {
        val started = java.util.concurrent.CountDownLatch(1)
        val unblocked = java.util.concurrent.CountDownLatch(1)
        val source = object : InputStream() {
            override fun read(): Int { started.countDown(); unblocked.await(); throw IOException("closed") }
            override fun close() { unblocked.countDown() }
        }
        val target = Destination()
        val cancel = GeckoDownloadCancellation()
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            val result = executor.submit<GeckoDownloadOutcome> { transferGeckoDownload(source, target, cancel) }
            assertTrue(started.await(5, java.util.concurrent.TimeUnit.SECONDS))
            cancel.cancel()
            assertEquals(GeckoDownloadOutcome.Cancelled, result.get(5, java.util.concurrent.TimeUnit.SECONDS))
            assertTrue(target.aborted)
            assertFalse(target.committed)
        } finally { cancel.cancel(); executor.shutdownNow() }
    }
    @Test fun cancellationAfterCommitDoesNotChangeCompletedResult() {
        val cancel = GeckoDownloadCancellation()
        val target = Destination()
        assertEquals(GeckoDownloadOutcome.Complete(1), transferGeckoDownload(ByteArrayInputStream(byteArrayOf(1)), target, cancel))
        cancel.cancel()
        assertFalse(cancel.isCancelled())
        assertTrue(target.committed)
    }
}
