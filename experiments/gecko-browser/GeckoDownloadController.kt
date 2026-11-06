package com.example.browser.gecko

import androidx.annotation.MainThread
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.downloads.DownloadFileResolver
import java.io.Closeable
import java.io.InputStream
import java.util.concurrent.Executor

/** An offer owns its response until approved or rejected; it must never be re-fetched. */
internal interface GeckoDownloadSource : Closeable {
    val mimeType: String?
    val contentDisposition: String?
    val contentLength: Long?
    val statusCode: Int
    fun takeBody(): InputStream?
}

internal sealed interface GeckoDownloadState {
    data object Idle : GeckoDownloadState
    data class Offer(val name: String, val length: Long?) : GeckoDownloadState
    data class Active(val name: String, val bytes: Long, val length: Long?) : GeckoDownloadState
    data class Finished(val name: String, val outcome: GeckoDownloadOutcome) : GeckoDownloadState
}

/**
 * One user-approved stream at a time. Calls and state updates are on the main thread; I/O is
 * on worker. A stream is non-resumable and must be cancelled when its owner is destroyed.
 * This controller intentionally does not persist authenticated URLs, headers or cookies.
 */
internal class GeckoDownloadController(
    private val worker: Executor,
    private val dispatchMain: (() -> Unit) -> Unit,
    private val destination: (String, String?) -> GeckoDownloadDestination,
) : AutoCloseable {
    var state: GeckoDownloadState by mutableStateOf(GeckoDownloadState.Idle)
        private set
    private var offer: GeckoDownloadSource? = null
    private var cancellation: GeckoDownloadCancellation? = null
    private var generation = 0L
    private var closed = false

    @MainThread fun offer(source: GeckoDownloadSource): Boolean {
        if (closed || offer != null || cancellation != null || source.statusCode !in 200..299) {
            runCatching { source.close() }
            return false
        }
        val name = DownloadFileResolver.safeName(DownloadFileResolver.dispositionName(source.contentDisposition) ?: "download")
        offer = source
        state = GeckoDownloadState.Offer(name, source.contentLength)
        return true
    }

    @MainThread fun reject() {
        offer?.let { runCatching { it.close() } }
        offer = null
        if (!closed && cancellation == null) state = GeckoDownloadState.Idle
    }

    @MainThread fun approve() {
        val source = offer ?: return
        val pending = state as? GeckoDownloadState.Offer ?: return
        offer = null
        val input = try { source.takeBody() } catch (_: Exception) { null }
        runCatching { source.close() }
        if (input == null) { state = GeckoDownloadState.Finished(pending.name, GeckoDownloadOutcome.Failed); return }
        val cancel = GeckoDownloadCancellation()
        cancellation = cancel
        val id = ++generation
        state = GeckoDownloadState.Active(pending.name, 0, pending.length)
        try {
            worker.execute {
                val outcome = try {
                    val target = destination(pending.name, source.mimeType)
                    var lastReported = 0L
                    transferGeckoDownload(input, target, cancel) { bytes ->
                        if (bytes - lastReported >= 256 * 1024) {
                            lastReported = bytes
                            dispatchMain {
                                if (!closed && generation == id && cancellation === cancel) {
                                    state = GeckoDownloadState.Active(pending.name, bytes, pending.length)
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                    runCatching { input.close() }
                    if (cancel.isCancelled()) GeckoDownloadOutcome.Cancelled else GeckoDownloadOutcome.Failed
                }
                dispatchMain {
                    if (!closed && generation == id && cancellation === cancel) {
                        cancellation = null
                        state = GeckoDownloadState.Finished(pending.name, outcome)
                    }
                }
            }
        } catch (_: Exception) {
            runCatching { input.close() }
            cancellation = null
            state = GeckoDownloadState.Finished(pending.name, GeckoDownloadOutcome.Failed)
        }
    }

    @MainThread fun cancel() { cancellation?.cancel() }
    @MainThread fun dismissResult() { if (state is GeckoDownloadState.Finished) state = GeckoDownloadState.Idle }
    @MainThread override fun close() {
        if (closed) return
        closed = true
        generation++
        reject()
        cancellation?.cancel()
        cancellation = null
        state = GeckoDownloadState.Idle
    }
}
