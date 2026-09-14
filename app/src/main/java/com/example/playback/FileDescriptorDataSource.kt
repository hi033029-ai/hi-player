package com.example.playback

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
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
class FileDescriptorDataSource(private val context: Context) : BaseDataSource(true) {

    private var pfd: ParcelFileDescriptor? = null
    private var input: FileInputStream? = null
    private var uri: Uri? = null
    private var bytesRemaining: Long = 0

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        val resolver = context.contentResolver
        val opened = resolver.openFileDescriptor(dataSpec.uri, "r")
            ?: throw IOException("Could not open file descriptor for ${dataSpec.uri}")
        pfd = opened
        val stream = FileInputStream(opened.fileDescriptor)
        input = stream

        val fileLength = opened.statSize
        // Random-access seek directly on the fd — no re-read from the start.
        stream.channel.position(dataSpec.position)

        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
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

        val read = input?.read(buffer, offset, bytesToRead) ?: -1
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

    override fun getUri(): Uri? = uri

    override fun close() {
        try {
            input?.close()
        } finally {
            input = null
            try {
                pfd?.close()
            } finally {
                pfd = null
                transferEnded()
            }
        }
    }

    class Factory(private val context: Context) : DataSource.Factory {
        override fun createDataSource(): DataSource = FileDescriptorDataSource(context)
    }
}
