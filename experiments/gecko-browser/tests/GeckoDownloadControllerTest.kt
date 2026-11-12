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
    private class Source(
        val data: ByteArray = byteArrayOf(1, 2, 3),
        override val statusCode: Int = 200,
        override val contentDisposition: String? = "attachment; filename=\"../report.txt\"",
        override val responseUrl: String? = null,
        override val mimeType: String? = "application/octet-stream",
    ) : GeckoDownloadSource {
        override val contentLength = data.size.toLong()
        var closed = false
        var inputClosed = false
        var takeCalls = 0
        private var taken = false
        override fun takeBody(): InputStream? {
            takeCalls++
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
        fun runOne() { queue.removeAt(0).run() }
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
        assertEquals(1, source.takeCalls)
        assertEquals(GeckoDownloadOutcome.Complete(3), (controller.state as GeckoDownloadState.Finished).outcome)
    }
    @Test fun contentDispositionTakesPrecedenceOverResponseUrl() {
        val source = Source(contentDisposition = "attachment; filename=\"my report.txt\"", responseUrl = "https://example.test/url-name.mp4")
        val controller = GeckoDownloadController(Tasks(), { it() }) { _, _ -> Target() }
        assertTrue(controller.offer(source))
        assertEquals("my report.txt", (controller.state as GeckoDownloadState.Offer).name)
        controller.reject()
    }
    @Test fun urlQueryAndBasenameFallbacksAreSanitized() {
        val controller = GeckoDownloadController(Tasks(), { it() }) { _, _ -> Target() }
        assertTrue(controller.offer(Source(contentDisposition = null, responseUrl = "https://example.test/download?filename=quarter%20report.pdf")))
        assertEquals("quarter report.pdf", (controller.state as GeckoDownloadState.Offer).name)
        controller.reject()
        assertTrue(controller.offer(Source(contentDisposition = null, responseUrl = "https://example.test/files/video%20clip.mp4?token=secret")))
        assertEquals("video clip.mp4", (controller.state as GeckoDownloadState.Offer).name)
        controller.reject()
    }
    @Test fun mimeTypeProvidesNameWhenDispositionAndUrlHaveNoFilename() {
        val controller = GeckoDownloadController(Tasks(), { it() }) { _, _ -> Target() }
        assertTrue(controller.offer(Source(contentDisposition = null, responseUrl = "https://example.test/download", mimeType = "application/pdf")))
        assertEquals("download.pdf", (controller.state as GeckoDownloadState.Offer).name)
        controller.reject()
    }
    @Test fun activeStateRetainsFilenameAndReportsDeterminateProgress() {
        val data = ByteArray(600 * 1024) { 7 }
        val worker = Tasks(); val ui = Tasks(); val source = Source(data, contentDisposition = "attachment; filename=\"archive.zip\"")
        val controller = GeckoDownloadController(worker, { ui.execute(Runnable(it)) }) { _, _ -> Target() }
        assertTrue(controller.offer(source))
        controller.approve()
        val initial = controller.state as GeckoDownloadState.Active
        assertEquals("archive.zip", initial.name)
        assertEquals(0L, initial.bytes)
        assertEquals(data.size.toLong(), initial.length)
        worker.run()
        ui.runOne()
        val progress = controller.state as GeckoDownloadState.Active
        assertEquals("archive.zip", progress.name)
        assertTrue(progress.bytes > 0)
        assertTrue(progress.speedBytesPerSecond >= 0)
        assertEquals(data.size.toLong(), progress.length)
        ui.run()
        assertEquals(GeckoDownloadOutcome.Complete(data.size.toLong()), (controller.state as GeckoDownloadState.Finished).outcome)
        assertEquals(1, source.takeCalls)
    }
    @Test fun lifecycleObserverSeesActiveProgressAndCompletionMetadata() {
        val worker = Tasks(); val ui = Tasks(); val events = mutableListOf<GeckoDownloadState>()
        val controller = GeckoDownloadController(worker, { ui.execute(Runnable(it)) }) { _, _ -> Target() }
        controller.onStateChanged = events::add
        assertTrue(controller.offer(Source(mimeType = "video/mp4"), recordInHistory = true))
        controller.approve(location = "selected folder")
        worker.run(); ui.run()

        val active = events.filterIsInstance<GeckoDownloadState.Active>()
        assertTrue(active.isNotEmpty())
        assertTrue(active.all { it.recordInHistory })
        assertTrue(active.all { it.mimeType == "video/mp4" && it.location == "selected folder" })
        assertTrue(active.any { it.bytes > 0 })
        val finished = events.filterIsInstance<GeckoDownloadState.Finished>().last()
        assertEquals(GeckoDownloadOutcome.Complete(3), finished.outcome)
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
        assertEquals(1, source.takeCalls)
        assertEquals(GeckoDownloadOutcome.Cancelled, (controller.state as GeckoDownloadState.Finished).outcome)
    }
    @Test fun closeCancelsAndIgnoresLateCallbacks() {
        val worker = Tasks(); val ui = Tasks(); val target = Target(); val source = Source()
        val controller = GeckoDownloadController(worker, { ui.execute(Runnable(it)) }) { _, _ -> target }
        controller.offer(source); controller.approve(); controller.close(); worker.run(); ui.run()
        assertTrue(target.aborted); assertFalse(target.committed); assertEquals(GeckoDownloadState.Idle, controller.state)
    }
    @Test fun customDestinationIsOnlyCreatedAfterApprovalAndNameIsSanitized() {
        val worker = Tasks(); val target = Target(); var factoryCalls = 0
        val controller = GeckoDownloadController(worker, { it() }) { _, _ -> error("default destination used") }
        controller.offer(Source())
        assertEquals(0, factoryCalls)
        controller.approve("../chosen.txt", "selected folder", destinationOverride = { factoryCalls++; target })
        assertEquals(0, factoryCalls)
        worker.run()
        assertEquals(1, factoryCalls); assertTrue(target.committed)
        val done = controller.state as GeckoDownloadState.Finished
        assertFalse(done.name.contains('/')); assertEquals("selected folder", done.location)
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
