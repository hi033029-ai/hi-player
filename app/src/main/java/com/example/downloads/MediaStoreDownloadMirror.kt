package com.example.downloads

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/** Streams into an app-owned pending Downloads row while the private spool remains resumable truth. */
internal class MediaStoreDownloadMirror(
    context: Context,
    private val uri: Uri,
    private val onFailure: (Uri) -> Unit,
) : DownloadByteMirror {
    private val resolver: ContentResolver = context.applicationContext.contentResolver
    private var descriptor: ParcelFileDescriptor? = null
    private var output: FileOutputStream? = null
    private var channel: FileChannel? = null
    private var healthy = true
    private var published = false

    override val isHealthy: Boolean get() = healthy

    init {
        try {
            descriptor = resolver.openFileDescriptor(uri, "rw") ?: throw IOException("MediaStore destination is unavailable")
            output = FileOutputStream(descriptor!!.fileDescriptor)
            channel = output!!.channel
            if (channel?.isOpen != true) throw IOException("MediaStore destination is not seekable")
        } catch (_: Exception) {
            fail()
        }
    }

    override fun reset() = guarded {
        val file = channel ?: throw IOException("No seekable destination")
        file.truncate(0)
        file.position(0)
    }

    override fun alignTo(spool: File) = guarded {
        val file = channel ?: throw IOException("No seekable destination")
        val length = spool.length()
        val mirrored = file.size()
        if (mirrored > length) file.truncate(length)
        if (mirrored < length) {
            file.position(mirrored.coerceAtMost(length))
            FileInputStream(spool).use { input ->
                input.skipFully(mirrored.coerceAtMost(length))
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    writeFully(file, buffer, count)
                }
            }
        }
        file.position(length)
    }

    override fun write(offset: Long, bytes: ByteArray, count: Int) = guarded {
        require(offset >= 0 && count in 0..bytes.size)
        val file = channel ?: throw IOException("No seekable destination")
        file.position(offset)
        writeFully(file, bytes, count)
    }

    override fun updateMetadata(fileName: String, mimeType: String?) = guarded {
        if (fileName.isBlank() && mimeType.isNullOrBlank()) return@guarded
        val values = ContentValues().apply {
            if (fileName.isNotBlank()) put(MediaStore.MediaColumns.DISPLAY_NAME, DownloadFileResolver.safeName(fileName))
            mimeType?.takeIf(String::isNotBlank)?.let { put(MediaStore.MediaColumns.MIME_TYPE, DownloadFileResolver.normalizeMime(it) ?: it) }
        }
        resolver.update(uri, values, null, null)
    }

    override fun publish(): Uri? {
        if (!healthy) return null
        return try {
            closeHandles()
            val rows = resolver.update(uri, ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }, null, null)
            if (rows != 1) throw IOException("Could not publish mirrored download")
            published = true
            uri
        } catch (_: Exception) {
            fail()
            null
        }
    }

    override fun close() = closeHandles()

    private inline fun guarded(block: () -> Unit) {
        if (!healthy) return
        try { block() } catch (_: Exception) { fail() }
    }

    private fun fail() {
        if (!healthy) return
        healthy = false
        closeHandles()
        if (!published) runCatching { onFailure(uri) }
    }

    private fun closeHandles() {
        runCatching { channel?.close() }
        runCatching { output?.close() }
        runCatching { descriptor?.close() }
        channel = null
        output = null
        descriptor = null
    }

    private fun writeFully(file: FileChannel, bytes: ByteArray, count: Int) {
        val buffer = ByteBuffer.wrap(bytes, 0, count)
        while (buffer.hasRemaining()) file.write(buffer)
    }

    private fun java.io.InputStream.skipFully(count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = skip(remaining)
            if (skipped > 0) remaining -= skipped
            else if (read() < 0) throw IOException("Could not align destination with download spool")
            else remaining--
        }
    }
}
