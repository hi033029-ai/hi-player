package com.example.browser.gecko

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

class GeckoDownloadControllerTest {
    private class Source(val data: ByteArray = byteArrayOf(1, 2, 3), override val statusCode: Int = 200) : GeckoDownloadSource {
        override val mimeType = "application/octet-stream"
        override val contentDisposition = "attachment; filename=\"../report.txt\""
        override val contentLength = data.size.toLong()
        var closed = false
        var inputClosed = false
        private var taken = false
        override fun takeBody(): InputStream? {
            if (taken) return null
            taken = true
            return object : ByteArrayInputStream(data) { override fun close() { inputClosed = true; super.close() } }
        }
        override fun close() { closed = true }
    }
    private class Target : GeckoDownloadDestination {
        val output = ByteArrayOutputStream()
        var committed = false
        var aborted = false
        override fun open(): OutputStream = output
        override fun commit() { committed = true }
        override fun abort() { aborted = true }
    }
    private class Tasks : Executor {
        val queue = mutableListOf<Runnable>()
        override fun execute(command: Runnable) { queue.add(command) }
        fun run() { while (queue.isNotEmpty()) queue.removeAt(0).run() }
    }
    @Test fun approvalCopiesAndCommitsWithoutPersistingSource() {
        val worker = Tasks(); val ui = Tasks(); val target = Target(); val source = Source()
        val controller = GeckoDownloadController(worker, { ui.execute(Runnable(it)) }) { _, _ -> target }
        assertTrue(controller.offer(source))
        val name = (controller.state as GeckoDownloadState.Offer).name
        assertFalse(name.contains('/'))
        assertFalse(target.committed)
        controller.approve()
        assertTrue(source.closed)
        worker.run(); ui.run()
        assertTrue(target.committed); assertArrayEquals(source.data, target.output.toByteArray()); assertTrue(source.inputClosed)
        assertEquals(GeckoDownloadOutcome.Complete(3), (controller.state as GeckoDownloadState.Finished).outcome)
    }
    @Test fun rejectAndBusyOffersCloseUnconsumedResponses() {
        val worker = Tasks(); val controller = GeckoDownloadController(worker, { it() }) { _, _ -> Target() }
        val first = Source(); val second = Source()
        assertTrue(controller.offer(first)); assertFalse(controller.offer(second)); assertTrue(second.closed)
        controller.reject(); assertTrue(first.closed); assertEquals(GeckoDownloadState.Idle, controller.state)
        val bad = Source(statusCode = 403); assertFalse(controller.offer(bad)); assertTrue(bad.closed)
    }
    @Test fun cancellationBeforeWorkerDoesNotPublish() {
        val worker = Tasks(); val target = Target(); val source = Source()
        val controller = GeckoDownloadController(worker, { it() }) { _, _ -> target }
        controller.offer(source); controller.approve(); controller.cancel(); worker.run()
        assertFalse(target.committed); assertTrue(target.aborted); assertTrue(source.inputClosed)
        assertEquals(GeckoDownloadOutcome.Cancelled, (controller.state as GeckoDownloadState.Finished).outcome)
    }
    @Test fun closeCancelsAndIgnoresLateCallbacks() {
        val worker = Tasks(); val ui = Tasks(); val target = Target(); val source = Source()
        val controller = GeckoDownloadController(worker, { ui.execute(Runnable(it)) }) { _, _ -> target }
        controller.offer(source); controller.approve(); controller.close(); worker.run(); ui.run()
        assertTrue(target.aborted); assertFalse(target.committed); assertEquals(GeckoDownloadState.Idle, controller.state)
    }
    @Test fun rejectedWorkerAndDestinationFailureCloseInput() {
        val source = Source()
        val controller = GeckoDownloadController(Executor { throw RejectedExecutionException() }, { it() }) { _, _ -> Target() }
        controller.offer(source); controller.approve(); assertTrue(source.inputClosed)
        assertEquals(GeckoDownloadOutcome.Failed, (controller.state as GeckoDownloadState.Finished).outcome)
        val source2 = Source(); val second = GeckoDownloadController(Executor { it.run() }, { it() }) { _, _ -> error("failed") }
        second.offer(source2); second.approve(); assertTrue(source2.inputClosed)
        assertEquals(GeckoDownloadOutcome.Failed, (second.state as GeckoDownloadState.Finished).outcome)
    }
}
