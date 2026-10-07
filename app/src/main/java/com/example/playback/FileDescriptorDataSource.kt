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
 * Point 6: Direct file-descriptor access.
 *
 * Opens the content:// / file:// Uri exactly once via ContentResolver, keeps the raw
 * ParcelFileDescriptor open for the life of playback, and seeks/reads directly against
 * it. This avoids: (a) ExoPlayer's normal ContentDataSource re-resolving the Uri on every
 * seek, and (b) any intermediate copy of the file into app storage or a temp buffer —
 * critical for multi-GB 4K files where a copy would be slow and wasteful of storage.
 */
class FileDescriptorDataSource(
    private val context: Context,
    private val waitForGrowth: Boolean = false,
) : BaseDataSource(true) {

    private var pfd: ParcelFileDescriptor? = null
    private var input: FileInputStream? = null
    private var uri: Uri? = null
    private var growingDownloadKey: String? = null
    private var bytesRemaining: Long = 0
    private var openedAsGrowingFile = false
    private var keepWaitingUntilMs = 0L

    @Volatile
    private var closed = false

    override fun open(dataSpec: DataSpec): Long {
        closed = false
        uri = dataSpec.uri
        val opened = openDescriptor(dataSpec.uri)
        pfd = opened
        val stream = FileInputStream(opened.fileDescriptor)
        input = stream

        val fileLength = opened.statSize
        // Random-access seek directly on the fd — no re-read from the start.
        stream.channel.position(dataSpec.position)

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
        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else if (activeGrowingFile) {
            C.LENGTH_UNSET.toLong()
        } else {
            fileLength - dataSpec.position
        }
        if (bytesRemaining < 0) throw IOException("Requested position beyond file length")

        transferInitializing(dataSpec)
        transferStarted(dataSpec)
        return bytesRemaining
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
                read = input?.read(buffer, offset, length) ?: -1
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

    private fun waitForAppendedBytes(): Boolean {
        val activeUri = uri ?: return false
        val channel = input?.channel ?: return false
        val position = channel.position()
        val deadline = SystemClock.elapsedRealtime() + GROWTH_POLL_WINDOW_MS
        while (!closed && SystemClock.elapsedRealtime() < deadline) {
            if (availableLength(activeUri) > position && reopenAt(position)) {
                keepWaitingUntilMs = SystemClock.elapsedRealtime() + GROWING_IDLE_GRACE_MS
                return true
            }
            SystemClock.sleep(GROWTH_POLL_INTERVAL_MS)
        }
        return false
    }

    /**
     * A MediaStore provider can hand out a descriptor that has reached the
     * current file end while its owner keeps appending data. Reopening at the
     * same byte position guarantees that the next read sees the new range.
     */
    private fun reopenAt(position: Long): Boolean {
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
            previousInput?.close()
            previousDescriptor?.close()
            true
        }.getOrDefault(false)
    }

    /**
     * Do not turn a short scanner/network pause into a fatal player error.
     * Both temporary-suffix and final-name downloads keep the source alive for
     * a bounded grace period after their last byte growth. A fresh growth
     * observation extends that period naturally, while an abandoned download
     * eventually returns end-of-input instead of buffering forever.
     */
    private fun shouldKeepWaitingForGrowth(): Boolean {
        val key = growingDownloadKey.orEmpty()
        return DownloadingFilesRegistry.isActivelyGrowing(key) ||
            SystemClock.elapsedRealtime() < keepWaitingUntilMs
    }

    private fun openDescriptor(targetUri: Uri): ParcelFileDescriptor {
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

    private fun availableLength(uri: Uri): Long = when (uri.scheme) {
        "file" -> uri.path?.let(::File)?.length() ?: 0L
        else -> runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                descriptor.statSize.coerceAtLeast(0L)
            } ?: 0L
        }.getOrDefault(0L)
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        closed = true
        try {
            input?.close()
        } finally {
            input = null
            try {
                pfd?.close()
            } finally {
                pfd = null
                growingDownloadKey = null
                openedAsGrowingFile = false
                keepWaitingUntilMs = 0L
                transferEnded()
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
