package com.example.browser.gecko

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.example.downloads.DownloadFileResolver
import java.io.IOException
import java.io.OutputStream

/**
 * Creates a new pending Downloads entry only after the user has approved the transfer.
 * The response stream stays inside Gecko; cookies and authentication headers are not copied.
 * A failed, cancelled, or incomplete transfer deletes its pending entry.
 */
@RequiresApi(Build.VERSION_CODES.Q)
internal class GeckoMediaStoreDestination(
    private val backend: PendingMediaStore,
    fileName: String,
    mimeType: String?,
) : GeckoPublishedDestination {
    constructor(context: Context, fileName: String, mimeType: String?) :
        this(AndroidPendingMediaStore(context.applicationContext.contentResolver), fileName, mimeType)

    private val name = DownloadFileResolver.safeName(fileName)
    private val mime = DownloadFileResolver.normalizeMime(mimeType) ?: "application/octet-stream"
    private var pending: Uri? = null
    override val treeUri: Uri? = null
    override var publishedUri: Uri? = null
        private set
    private var started = false

    override fun open(): OutputStream {
        check(!started) { "Destination has already been used" }
        started = true
        val uri = backend.create(name, mime)
        pending = uri
        return backend.open(uri) ?: throw IOException("Could not open destination")
    }

    override fun commit() {
        val uri = pending ?: throw IOException("No pending download")
        if (!backend.publish(uri)) throw IOException("Could not publish download")
        publishedUri = uri
        pending = null
    }

    override fun abort() {
        val uri = pending ?: return
        // Keep the reference if deletion fails so a caller may retry cleanup.
        backend.delete(uri)
        pending = null
    }
}

internal interface PendingMediaStore {
    fun create(name: String, mime: String): Uri
    fun open(uri: Uri): OutputStream?
    fun publish(uri: Uri): Boolean
    fun delete(uri: Uri)
}

@RequiresApi(Build.VERSION_CODES.Q)
private class AndroidPendingMediaStore(private val resolver: ContentResolver) : PendingMediaStore {
    override fun create(name: String, mime: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        return resolver.insert(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
            ?: throw IOException("Could not create destination")
    }
    override fun open(uri: Uri): OutputStream? = resolver.openOutputStream(uri, "w")
    override fun publish(uri: Uri): Boolean = resolver.update(uri, ContentValues().apply {
        put(MediaStore.Downloads.IS_PENDING, 0)
    }, null, null) == 1
    override fun delete(uri: Uri) {
        if (resolver.delete(uri, null, null) != 1) throw IOException("Could not remove pending destination")
    }
}
