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

/**
 * A seekable local/content source that can keep an incomplete download open while
 * its writer appends bytes. It never copies media into app storage.
 *
 * Progressive extractors can legitimately request a position just beyond the
 * bytes written so far (for example, while looking for Matroska metadata). For
 * a registered growing download, this source waits for that position instead of
 * converting the temporary condition into a fatal source error.
 */
class FileDescriptorDataSource(
    private val context: Context,
    private val waitForGrowth: Boolean = false,
) : BaseDataSource(true) {

    private data class SourceSnapshot(
        val length: Long,
        val modifiedAtMs: Long,
    )

    private var pfd: ParcelFileDescriptor? = null
    private var input: FileInputStream? = null
    private var uri: Uri? = null
    private var growingDownloadKey: String? = null
    private var bytesRemaining: Long = 0L
    private var openedAsGrowingFile = false
    private var keepWaitingUntilMs = 0L
    private var lastObservedLength = 0L
    private var lastObservedModifiedMs = 0L

    @Volatile
    private var closed = false

    override fun open(dataSpec: DataSpec): Long {
        closed = false
        uri = dataSpec.uri
        val downloadKey = downloadKey(dataSpec.uri)
        val activeGrowingFile = waitForGrowth && (
            DownloadingFilesRegistry.isDownloading(downloadKey) ||
                PendingDownloadFormat.isPending(dataSpec.uri.lastPathSegment.orEmpty())
            )
        growingDownloadKey = downloadKey.takeIf { activeGrowingFile }
        openedAsGrowingFile = activeGrowingFile
        keepWaitingUntilMs = if (activeGrowingFile) {
            SystemClock.elapsedRealtime() + GROWING_IDLE_GRACE_MS
        } else {
            0L
        }

        transferInitializing(dataSpec)
        try {
            // An extractor may seek to a metadata position that has not arrived
            // yet. Wait before creating the stream, rather than calling
            // FileChannel.position past EOF and surfacing a source failure.
            if (dataSpec.position > snapshotFor(dataSpec.uri).length && activeGrowingFile) {
                waitForRequiredPosition(dataSpec.uri, dataSpec.position)
            }

            val descriptor = openDescriptor(dataSpec.uri)
            val stream = FileInputStream(descriptor.fileDescriptor)
            val snapshot = snapshotFor(dataSpec.uri, descriptor.statSize)
            if (dataSpec.position > snapshot.length) {
                stream.close()
                descriptor.close()
                throw IOException("Requested position beyond available download data")
            }

            try {
                stream.channel.position(dataSpec.position)
            } catch (error: Exception) {
                stream.close()
                descriptor.close()
                throw error
            }

            pfd = descriptor
            input = stream
            lastObservedLength = snapshot.length
            lastObservedModifiedMs = snapshot.modifiedAtMs

            // A stale explicit length is unsafe for a file still being appended.
            // Advertising an unknown length makes Media3 reopen/read through the
            // growing source instead of treating the current EOF as final.
            bytesRemaining = when {
                activeGrowingFile -> C.LENGTH_UNSET.toLong()
                dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
                else -> snapshot.length - dataSpec.position
            }
            if (bytesRemaining != C.LENGTH_UNSET.toLong() && bytesRemaining < 0) {
                throw IOException("Requested position beyond file length")
            }

            transferStarted(dataSpec)
            return bytesRemaining
        } catch (error: Exception) {
            clearDescriptors()
            throw error
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val bytesToRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            length
        } else {
            minOf(length.toLong(), bytesRemaining).toInt()
        }

        var read = input?.read(buffer, offset, bytesToRead) ?: -1
        while (read == -1 && waitForGrowth && openedAsGrowingFile && !closed) {
            if (waitForAppendedBytes()) {
                read = input?.read(buffer, offset, bytesToRead) ?: -1
            } else if (!shouldKeepWaitingForGrowth()) {
                break
            }
        }
        if (read == -1) {
            if (bytesRemaining != C.LENGTH_UNSET.toLong() && bytesRemaining > 0) {
                throw IOException("Unexpected end of stream, $bytesRemaining bytes remaining")
            }
            return C.RESULT_END_OF_INPUT
        }

        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    /** Waits until an extractor's requested offset has actually become readable. */
    private fun waitForRequiredPosition(targetUri: Uri, requiredPosition: Long) {
        val deadline = SystemClock.elapsedRealtime() + GROWTH_POLL_WINDOW_MS
        while (!closed && SystemClock.elapsedRealtime() < deadline) {
            if (snapshotFor(targetUri).length >= requiredPosition) return
            if (!shouldKeepWaitingForGrowth()) break
            sleepForGrowthPoll()
        }
    }

    /**
     * Reopens only after the source has genuinely changed. This avoids a tight
     * retry loop when a download manager preallocates a file but has not yet
     * written the next media bytes into it.
     */
    private fun waitForAppendedBytes(): Boolean {
        val activeUri = uri ?: return false
        val position = input?.channel?.position() ?: return false
        val deadline = SystemClock.elapsedRealtime() + GROWTH_POLL_WINDOW_MS
        while (!closed && SystemClock.elapsedRealtime() < deadline) {
            val snapshot = snapshotFor(activeUri)
            val hasNewLength = snapshot.length > lastObservedLength && snapshot.length > position
            // Some download managers preallocate the final filename. For file:
            // sources, an updated mtime is the available signal that new bytes
            // may have been written even when File.length() did not change.
            val hasNewFileWrite = activeUri.scheme == "file" &&
                snapshot.modifiedAtMs > lastObservedModifiedMs && snapshot.length > position
            if ((hasNewLength || hasNewFileWrite) && reopenAt(position, snapshot)) {
                return true
            }
            if (!shouldKeepWaitingForGrowth()) return false
            sleepForGrowthPoll()
        }
        return false
    }

    /**
     * A MediaStore provider can hand out a descriptor that has reached the
     * current file end while its owner keeps appending data. Reopening at the
     * same byte position exposes the newly written range atomically.
     */
    private fun reopenAt(position: Long, snapshot: SourceSnapshot): Boolean {
        val activeUri = uri ?: return false
        return runCatching {
            val descriptor = openDescriptor(activeUri)
            val stream = FileInputStream(descriptor.fileDescriptor)
            try {
                stream.channel.position(position)
            } catch (error: Exception) {
                stream.close()
                descriptor.close()
                throw error
            }
            val previousInput = input
            val previousDescriptor = pfd
            pfd = descriptor
            input = stream
            lastObservedLength = snapshot.length
            lastObservedModifiedMs = snapshot.modifiedAtMs
            keepWaitingUntilMs = SystemClock.elapsedRealtime() + GROWING_IDLE_GRACE_MS
            previousInput?.close()
            previousDescriptor?.close()
            true
        }.getOrDefault(false)
    }

    /**
     * Do not turn a short scanner/network pause into a fatal player error. A
     * fresh growth observation extends the wait window, while an abandoned
     * download eventually returns EOF rather than buffering indefinitely.
     */
    private fun shouldKeepWaitingForGrowth(): Boolean {
        val key = growingDownloadKey.orEmpty()
        return DownloadingFilesRegistry.isActivelyGrowing(key) ||
            SystemClock.elapsedRealtime() < keepWaitingUntilMs
    }

    private fun sleepForGrowthPoll() {
        try {
            Thread.sleep(GROWTH_POLL_INTERVAL_MS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun resolvedUri(targetUri: Uri): Uri = DownloadingFilesRegistry.completedUri(downloadKey(targetUri))?.let(Uri::parse) ?: targetUri

    private fun openDescriptor(originalUri: Uri): ParcelFileDescriptor {
        val targetUri = resolvedUri(originalUri)
        return if (targetUri.scheme == "file") {
            val path = targetUri.path ?: throw IOException("File URI has no path: $targetUri")
            ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
        } else {
            context.contentResolver.openFileDescriptor(targetUri, "r")
                ?: throw IOException("Could not open file descriptor for $targetUri")
        }
    }

    private fun downloadKey(uri: Uri): String =
        if (uri.scheme == "file") uri.path.orEmpty() else uri.toString()

    private fun snapshotFor(originalUri: Uri, descriptorLength: Long? = null): SourceSnapshot {
        val targetUri = resolvedUri(originalUri)
        return when (targetUri.scheme) {
        "file" -> targetUri.path?.let(::File)?.let { file ->
            SourceSnapshot(
                length = file.length().coerceAtLeast(0L),
                modifiedAtMs = file.lastModified().coerceAtLeast(0L),
            )
        } ?: SourceSnapshot(0L, 0L)
        else -> {
            val length = descriptorLength ?: runCatching {
                context.contentResolver.openFileDescriptor(targetUri, "r")?.use { descriptor ->
                    descriptor.statSize.coerceAtLeast(0L)
                } ?: 0L
            }.getOrDefault(0L)
            SourceSnapshot(length.coerceAtLeast(0L), 0L)
        }
    }

    }

    override fun getUri(): Uri? = uri

    override fun close() {
        closed = true
        try {
            clearDescriptors()
        } finally {
            growingDownloadKey = null
            openedAsGrowingFile = false
            keepWaitingUntilMs = 0L
            lastObservedLength = 0L
            lastObservedModifiedMs = 0L
            transferEnded()
        }
    }

    private fun clearDescriptors() {
        try {
            input?.close()
        } finally {
            input = null
            try {
                pfd?.close()
            } finally {
                pfd = null
            }
        }
    }

    class Factory(
        private val context: Context,
        private val waitForGrowth: Boolean = false,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            FileDescriptorDataSource(context, waitForGrowth)
    }

    private companion object {
        const val GROWTH_POLL_INTERVAL_MS = 200L
        const val GROWTH_POLL_WINDOW_MS = 30_000L
        const val GROWING_IDLE_GRACE_MS = 120_000L
    }
}
