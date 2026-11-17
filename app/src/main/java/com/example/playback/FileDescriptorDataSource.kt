package com.example.playback

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.example.model.PendingDownloadFormat
import com.example.player.DownloadingFilesRegistry
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InterruptedIOException

/** A loader-thread reader. One descriptor survives append, EOF and atomic rename. */
class FileDescriptorDataSource(
    private val context: Context,
    private val waitForGrowth: Boolean = false,
) : BaseDataSource(false) {
    private var input: FileInputStream? = null
    private var uri: Uri? = null
    private var key = ""
    private var remaining = 0L
    private var growing = false
    private var started = false
    private var optionalTailProbe = false
    private var idleDeadline = 0L
    private var observedLength = 0L
    @Volatile private var closed = false

    override fun open(dataSpec: DataSpec): Long {
        closed = false
        optionalTailProbe = false
        uri = dataSpec.uri
        key = if (dataSpec.uri.scheme == "file" || dataSpec.uri.scheme == null) dataSpec.uri.path.orEmpty() else dataSpec.uri.toString()
        val published = DownloadingFilesRegistry.completedUri(key)
        growing = waitForGrowth && published == null &&
            (DownloadingFilesRegistry.isDownloading(key) || PendingDownloadFormat.isPending(dataSpec.uri.lastPathSegment.orEmpty()))
        idleDeadline = SystemClock.elapsedRealtime() + IDLE_TIMEOUT_MS
        transferInitializing(dataSpec)
        try {
            val target = published?.let(Uri::parse) ?: dataSpec.uri
            val descriptor = if (target.scheme == "file" || target.scheme == null) {
                ParcelFileDescriptor.open(File(target.path ?: throw IOException("Missing file path")), ParcelFileDescriptor.MODE_READ_ONLY)
            } else context.contentResolver.openFileDescriptor(target, "r") ?: throw IOException("No readable descriptor")
            // AutoCloseInputStream is the sole owner; do not double-close a raw fd.
            val stream = ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            input = stream
            observedLength = stream.channel.size()
            if (growing && dataSpec.position - observedLength > MAX_SEQUENTIAL_GAP) {
                // An optional SeekHead tail probe, not a playback seek success.
                optionalTailProbe = true
            } else {
                while (dataSpec.position > stream.channel.size() && growing && !closed) {
                    awaitGrowthPoll()
                }
                if (closed) throw InterruptedIOException("Source closed")
                if (dataSpec.position > stream.channel.size()) throw IOException("Position is not downloaded")
                stream.channel.position(dataSpec.position)
            }
            remaining = when {
                dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
                growing -> C.LENGTH_UNSET.toLong()
                else -> (stream.channel.size() - dataSpec.position).coerceAtLeast(0)
            }
            transferStarted(dataSpec)
            started = true
            return remaining
        } catch (error: Exception) {
            input?.close(); input = null
            throw error
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (closed) throw InterruptedIOException("Source closed")
        if (remaining == 0L || optionalTailProbe) return C.RESULT_END_OF_INPUT
        val count = if (remaining == C.LENGTH_UNSET.toLong()) length else minOf(length.toLong(), remaining).toInt()
        val stream = input ?: throw IOException("Source is not open")
        while (true) {
            val read = stream.read(buffer, offset, count)
            if (read > 0) {
                if (remaining != C.LENGTH_UNSET.toLong()) remaining -= read
                observedLength = maxOf(observedLength, stream.channel.position())
                idleDeadline = SystemClock.elapsedRealtime() + IDLE_TIMEOUT_MS
                bytesTransferred(read)
                return read
            }
            if (!growing || DownloadingFilesRegistry.completedUri(key) != null) {
                if (remaining > 0) throw IOException("Incomplete stream")
                return C.RESULT_END_OF_INPUT
            }
            // Never expose temporary EOF to MatroskaExtractor. Its EBML reader
            // can be in the middle of a size vint, laced block or codec packet.
            // Retrying the SAME read preserves parser/decoder/track state.
            awaitGrowthPoll()
            val size = stream.channel.size()
            if (size > observedLength) {
                observedLength = size
                idleDeadline = SystemClock.elapsedRealtime() + IDLE_TIMEOUT_MS
            }
            if (!DownloadingFilesRegistry.isDownloading(key) &&
                !PendingDownloadFormat.isPending(uri?.lastPathSegment.orEmpty()) &&
                SystemClock.elapsedRealtime() >= idleDeadline) return C.RESULT_END_OF_INPUT
        }
    }

    private fun awaitGrowthPoll() {
        if (closed || Thread.currentThread().isInterrupted) throw InterruptedIOException("Read cancelled")
        if (SystemClock.elapsedRealtime() >= idleDeadline) throw IOException("Download stopped supplying data")
        try { Thread.sleep(POLL_MS) }
        catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException("Read cancelled")
        }
        if (closed) throw InterruptedIOException("Read cancelled")
    }

    override fun getUri(): Uri? = uri
    override fun close() {
        closed = true
        val stream = input
        input = null
        try { stream?.close() }
        finally {
            if (started) { started = false; transferEnded() }
        }
    }
    class Factory(private val context: Context, private val waitForGrowth: Boolean = false) : DataSource.Factory {
        override fun createDataSource(): DataSource = FileDescriptorDataSource(context, waitForGrowth)
    }
    private companion object {
        const val POLL_MS = 100L
        const val IDLE_TIMEOUT_MS = 120_000L
        const val MAX_SEQUENTIAL_GAP = 32L * 1024 * 1024
    }
}
