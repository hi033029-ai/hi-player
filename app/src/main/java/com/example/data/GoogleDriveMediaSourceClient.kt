package com.example.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.model.GoogleDriveConnection
import com.example.model.RemoteMediaMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Browses a Google Drive folder selected through Android's Storage Access
 * Framework. Authentication and account selection are handled by the Google
 * Drive document provider, so no OAuth token or custom Google client ID is
 * stored in Hi Player.
 */
object GoogleDriveMediaSourceClient {

    private const val MAX_FILES_TO_SCAN = 600
    private const val MAX_FOLDER_DEPTH = 4

    fun isGoogleDriveTree(uri: Uri): Boolean =
        uri.authority?.contains("docs.storage", ignoreCase = true) == true

    fun selectedFolderName(context: Context, treeUri: Uri): String =
        DocumentFile.fromTreeUri(context, treeUri)?.name?.takeIf { it.isNotBlank() }
            ?: "Google Drive folder"

    suspend fun verify(context: Context, connection: GoogleDriveConnection): Unit = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, Uri.parse(connection.documentTreeUri))
            ?: throw IllegalStateException("The selected Google Drive folder is unavailable.")
        if (!root.isDirectory) {
            throw IllegalStateException("Select a Google Drive folder, not an individual file.")
        }
        // Force an inexpensive provider read now, so permission problems are
        // reported while connecting rather than during Discover search.
        runCatching { root.listFiles() }
            .getOrElse { throw IllegalStateException("Hi Player cannot read this Google Drive folder.", it) }
    }

    suspend fun search(
        context: Context,
        connection: GoogleDriveConnection,
        query: String
    ): List<RemoteMediaMatch> = withContext(Dispatchers.IO) {
        if (!connection.isConfigured) return@withContext emptyList()
        val root = DocumentFile.fromTreeUri(context, Uri.parse(connection.documentTreeUri))
            ?: return@withContext emptyList()
        val cleanQuery = query.trim().lowercase()

        scanVideos(root)
            .asSequence()
            .filter { file -> cleanQuery.isBlank() || file.name.orEmpty().contains(cleanQuery, ignoreCase = true) }
            .map { file -> file.toRemoteMatch(connection) }
            .sortedBy { it.title.lowercase() }
            .toList()
    }

    private fun scanVideos(root: DocumentFile): List<DocumentFile> {
        val videos = mutableListOf<DocumentFile>()
        val pendingFolders = ArrayDeque<Pair<DocumentFile, Int>>()
        pendingFolders.add(root to 0)

        while (pendingFolders.isNotEmpty() && videos.size < MAX_FILES_TO_SCAN) {
            val (folder, depth) = pendingFolders.removeFirst()
            val children = runCatching { folder.listFiles().toList() }.getOrDefault(emptyList())
            for (child in children) {
                when {
                    child.isDirectory && depth < MAX_FOLDER_DEPTH -> pendingFolders.add(child to depth + 1)
                    child.isFile && isVideo(child) -> videos += child
                }
                if (videos.size >= MAX_FILES_TO_SCAN) break
            }
        }
        return videos
    }

    private fun isVideo(file: DocumentFile): Boolean {
        val mimeType = file.type.orEmpty()
        if (mimeType.startsWith("video/", ignoreCase = true)) return true
        return file.name.orEmpty().substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS
    }

    private fun DocumentFile.toRemoteMatch(connection: GoogleDriveConnection): RemoteMediaMatch {
        val fileName = name.orEmpty().ifBlank { "Google Drive video" }
        val mimeType = type?.takeIf { it.startsWith("video/", ignoreCase = true) }
            ?: mimeTypeForName(fileName)
        return RemoteMediaMatch(
            id = "gdrive-document:$uri",
            sourceName = connection.displayName,
            title = fileName,
            subtitle = "Google Drive",
            streamUri = uri,
            streamMimeType = mimeType,
            sizeBytes = length().coerceAtLeast(0L),
            codec = "Google Drive"
        )
    }

    private fun mimeTypeForName(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        "avi" -> "video/x-msvideo"
        "ts", "mts", "m2ts" -> "video/mp2t"
        "3gp", "3gpp" -> "video/3gpp"
        else -> "video/mp4"
    }

    private val VIDEO_EXTENSIONS = setOf(
        "mp4", "m4v", "mkv", "webm", "mov", "avi", "mpeg", "mpg",
        "ts", "mts", "m2ts", "3gp", "3gpp", "flv", "wmv", "vob"
    )
}
