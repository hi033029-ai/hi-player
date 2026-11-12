package com.example.browser.gecko

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.downloads.DownloadFileResolver
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

/**
 * Creates a new uniquely named file in a user-selected SAF tree. A random suffix avoids
 * overwriting a file the user already has. A failed transfer removes only the document created
 * by this instance; a provider or process crash can still leave a partial file.
 */
internal class GeckoDocumentTreeDestination(
    private val backend: GeckoDocumentTreeBackend,
    fileName: String,
    mimeType: String?,
    private val uniqueId: () -> String = { UUID.randomUUID().toString() },
    override val treeUri: Uri? = null,
) : GeckoPublishedDestination {
    constructor(context: Context, treeUri: Uri, fileName: String, mimeType: String?) :
        this(AndroidGeckoDocumentTreeBackend(context.applicationContext, treeUri), fileName, mimeType, treeUri = treeUri)

    private val safeName = DownloadFileResolver.safeName(fileName)
    private val mime = DownloadFileResolver.normalizeMime(mimeType) ?: "application/octet-stream"
    private var created: Uri? = null
    private var started = false
    override var publishedUri: Uri? = null
        private set
    override val writingUri: Uri? get() = created ?: publishedUri

    override fun open(): OutputStream {
        check(!started) { "Destination already used" }
        started = true
        val suffix = uniqueId().filter { it.isLetterOrDigit() }.take(32)
        if (suffix.isEmpty()) throw IOException("Cannot create unique download name")
        val ext = safeName.substringAfterLast('.', "").takeIf { it.isNotBlank() && it.length <= 8 }
        val stem = if (ext == null) safeName else safeName.dropLast(ext.length + 1)
        val candidate = DownloadFileResolver.safeName("${stem.take(75)}-$suffix${ext?.let { ".$it" }.orEmpty()}")
        val uri = backend.create(candidate, mime)
        created = uri
        return backend.open(uri) ?: throw IOException("Could not open selected destination")
    }
    override fun commit() {
        val uri = created ?: throw IOException("No download to publish")
        publishedUri = uri
        created = null
    }
    override fun abort() {
        val uri = created ?: return
        backend.delete(uri)
        created = null
    }
}

internal interface GeckoDocumentTreeBackend {
    fun create(name: String, mime: String): Uri
    fun open(uri: Uri): OutputStream?
    fun delete(uri: Uri)
}

private class AndroidGeckoDocumentTreeBackend(private val context: Context, private val tree: Uri) : GeckoDocumentTreeBackend {
    override fun create(name: String, mime: String): Uri {
        val folder = DocumentFile.fromTreeUri(context, tree) ?: throw IOException("Selected folder is unavailable")
        if (!folder.canWrite() || folder.findFile(name) != null) throw IOException("Cannot create a new file in selected folder")
        return folder.createFile(mime, name)?.uri ?: throw IOException("Could not create download")
    }
    override fun open(uri: Uri) = context.contentResolver.openOutputStream(uri, "w")
    override fun delete(uri: Uri) {
        if (!android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri)) throw IOException("Could not remove partial download")
    }
}
