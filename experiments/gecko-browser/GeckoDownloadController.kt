package com.example.browser.gecko

import androidx.annotation.MainThread
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.downloads.DownloadFileResolver
import java.io.Closeable
import java.io.InputStream
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal interface GeckoDownloadSource : Closeable {
    val mimeType: String?
    val contentDisposition: String?
    val contentLength: Long?
    val statusCode: Int
    fun takeBody(): InputStream?
}
internal sealed interface GeckoDownloadState {
    data object Idle : GeckoDownloadState
    data class Offer(val name: String, val length: Long?, val mimeType: String? = null, val recordInHistory: Boolean = true) : GeckoDownloadState
    data class Active(val name: String, val bytes: Long, val length: Long?) : GeckoDownloadState
    data class Finished(val name: String, val outcome: GeckoDownloadOutcome, val location: String = "Downloads", val recorded: Boolean? = null) : GeckoDownloadState
}
/** Approved response ownership only; no refetch, persisted credentials or fake progress. */
internal class GeckoDownloadController(
    private val worker: Executor,
    private val dispatchMain: (() -> Unit) -> Unit,
    private val onPublished: ((GeckoPublishedDestination, String, String?, Long, String) -> Boolean)? = null,
    private val destination: (String, String?) -> GeckoDownloadDestination,
) : AutoCloseable {
    var state: GeckoDownloadState by mutableStateOf(GeckoDownloadState.Idle)
        private set
    var onTransferStarted: ((() -> Unit) -> AutoCloseable)? = null
    private val activeLease = AtomicReference<AutoCloseable?>(null)
    private var offer: GeckoDownloadSource? = null
    private var cancellation: GeckoDownloadCancellation? = null
    private var generation = 0L
    private var closed = false
    @MainThread fun offer(source: GeckoDownloadSource, recordInHistory: Boolean = true): Boolean {
        if (closed || offer != null || cancellation != null || source.statusCode !in 200..299) { runCatching { source.close() }; return false }
        val name = DownloadFileResolver.safeName(DownloadFileResolver.dispositionName(source.contentDisposition) ?: "download")
        offer = source
        state = GeckoDownloadState.Offer(name, source.contentLength, source.mimeType, recordInHistory)
        return true
    }
    @MainThread fun reject() {
        offer?.let { runCatching { it.close() } }; offer = null
        if (!closed && cancellation == null) state = GeckoDownloadState.Idle
    }
    @MainThread fun approve(fileName: String? = null, location: String = "Downloads", destinationOverride: (() -> GeckoDownloadDestination)? = null) {
        val source = offer ?: return
        val pending = state as? GeckoDownloadState.Offer ?: return
        offer = null
        val name = DownloadFileResolver.safeName(fileName ?: pending.name)
        val input = try { source.takeBody() } catch (_: Exception) { null }
        runCatching { source.close() }
        if (input == null) { state = GeckoDownloadState.Finished(name, GeckoDownloadOutcome.Failed, location); return }
        val cancel = GeckoDownloadCancellation()
        cancel.register(input)
        cancellation = cancel
        val id = ++generation
        state = GeckoDownloadState.Active(name, 0, pending.length)
        val rawLease = try { onTransferStarted?.invoke { cancel.cancel() } } catch (_: Exception) {
            cancel.cancel(); cancellation = null
            state = GeckoDownloadState.Finished(name, GeckoDownloadOutcome.Failed, location); return
        }
        val once = AtomicBoolean(false)
        val lease = rawLease?.let { value -> AutoCloseable { if (once.compareAndSet(false, true)) value.close() } }
        activeLease.set(lease)
        try {
            worker.execute {
                var recorded: Boolean? = null
                val outcome = try {
                    val target = destinationOverride?.invoke() ?: destination(name, source.mimeType)
                    var lastReported = 0L
                    val transferred = transferGeckoDownload(input, target, cancel) { bytes ->
                        if (bytes - lastReported >= 256 * 1024) {
                            lastReported = bytes
                            dispatchMain { if (!closed && generation == id && cancellation === cancel) state = GeckoDownloadState.Active(name, bytes, pending.length) }
                        }
                    }
                    val recorder = onPublished
                    if (pending.recordInHistory && transferred is GeckoDownloadOutcome.Complete && target is GeckoPublishedDestination && recorder != null) recorded = runCatching { recorder(target, name, source.mimeType, transferred.bytes, location) }.getOrDefault(false)
                    transferred
                } catch (_: Exception) {
                    runCatching { input.close() }
                    if (cancel.isCancelled()) GeckoDownloadOutcome.Cancelled else GeckoDownloadOutcome.Failed
                } finally { runCatching { lease?.close() }; activeLease.compareAndSet(lease, null) }
                dispatchMain { if (!closed && generation == id && cancellation === cancel) { cancellation = null; state = GeckoDownloadState.Finished(name, outcome, location, recorded) } }
            }
        } catch (_: Exception) {
            cancel.cancel(); runCatching { activeLease.getAndSet(null)?.close() }; cancellation = null
            state = GeckoDownloadState.Finished(name, GeckoDownloadOutcome.Failed, location)
        }
    }
    @MainThread fun cancel() { cancellation?.cancel(); runCatching { activeLease.getAndSet(null)?.close() } }
    @MainThread fun dismissResult() { if (state is GeckoDownloadState.Finished) state = GeckoDownloadState.Idle }
    @MainThread override fun close() {
        if (closed) return
        closed = true; generation++; reject(); cancel(); cancellation = null; state = GeckoDownloadState.Idle
    }
}
