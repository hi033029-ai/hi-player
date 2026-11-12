package com.example.browser.gecko

import androidx.annotation.WorkerThread
import org.mozilla.geckoview.WebResponse
import android.net.Uri
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream

/**
 * Owns Gecko's authenticated response stream, not a URL to be re-fetched through another
 * client's cookie jar. Keep this in memory, never persist request URLs or response headers.
 * Until a user confirms a destination, call discard() if the prompt is closed.
 */
internal class GeckoDownloadOffer(response: WebResponse) : GeckoDownloadSource {
    private var body: InputStream? = response.body
    override val mimeType: String? = response.headers["content-type"]?.substringBefore(';')?.trim()
    override val contentDisposition: String? = response.headers["content-disposition"]
    override val responseUrl: String? = response.uri
    override val contentLength: Long? = response.headers["content-length"]?.toLongOrNull()?.takeIf { it >= 0 }
    override val statusCode: Int = response.statusCode

    @Synchronized override fun takeBody(): InputStream? = body.also { body = null }
    @Synchronized override fun close() {
        val stream = body
        body = null
        stream?.close()
    }
}

/** Implement with a newly-created temporary destination; never overwrite an existing user file. */
internal interface GeckoDownloadDestination {
    fun open(): OutputStream
    fun commit()
    fun abort()
}

/** Metadata for a completed destination; never contains a website URL or credentials. */
internal interface GeckoPublishedDestination : GeckoDownloadDestination {
    val publishedUri: Uri?
    val treeUri: Uri?
    /** The app-readable document currently being written, used only for local growing playback. */
    val writingUri: Uri? get() = null
}

internal sealed interface GeckoDownloadOutcome {
    data class Complete(val bytes: Long) : GeckoDownloadOutcome
    data object Cancelled : GeckoDownloadOutcome
    data object Failed : GeckoDownloadOutcome
}

/** Closing the stream on cancel helps unblock an in-flight network read. */
internal class GeckoDownloadCancellation {
    private val lock = Any()
    private var cancelled = false
    private var committed = false
    private var input: InputStream? = null
    fun cancel() {
        val stream = synchronized(lock) { if (committed) null else { cancelled = true; input.also { input = null } } }
        runCatching { stream?.close() }
    }
    fun isCancelled(): Boolean = synchronized(lock) { cancelled }
    internal fun register(stream: InputStream): Boolean = synchronized(lock) {
        if (cancelled) false else { input = stream; true }
    }
    internal fun clear() = synchronized(lock) { input = null }
    internal fun commitIfActive(commit: () -> Unit): Boolean = synchronized(lock) {
        if (cancelled) false else { commit(); committed = true; true }
    }
}

/**
 * Call on an I/O worker only, after user approval. The destination owns any temporary output;
 * commit must publish only the completed file and abort must remove the temporary file.
 * Progress reports actual bytes copied, never an invented percentage.
 */
@WorkerThread
internal fun transferGeckoDownload(
    input: InputStream?,
    destination: GeckoDownloadDestination,
    cancellation: GeckoDownloadCancellation,
    onProgress: (Long) -> Unit = {},
): GeckoDownloadOutcome {
    if (input == null) return GeckoDownloadOutcome.Failed
    var committed = false
    try {
        var bytes = 0L
        input.use { source ->
            if (!cancellation.register(source)) return GeckoDownloadOutcome.Cancelled
            try {
                destination.open().use { output ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        if (cancellation.isCancelled()) return GeckoDownloadOutcome.Cancelled
                        val read = source.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        output.write(buffer, 0, read)
                        bytes += read
                        onProgress(bytes)
                    }
                    output.flush()
                }
            } finally { cancellation.clear() }
        }
        if (!cancellation.commitIfActive { destination.commit() }) return GeckoDownloadOutcome.Cancelled
        committed = true
        return GeckoDownloadOutcome.Complete(bytes)
    } catch (_: Exception) {
        return if (cancellation.isCancelled()) GeckoDownloadOutcome.Cancelled else GeckoDownloadOutcome.Failed
    } finally {
        if (!committed) runCatching { destination.abort() }
    }
}
