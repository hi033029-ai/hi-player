package com.example.downloads

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.example.model.AudioItem
import java.io.File

/** Builds playable entries from completed, still-accessible download URIs, not private .part files. */
internal object CompletedAudioLibrary {
    fun read(context: Context, entry: BrowserDownload): AudioItem? {
        if (!entry.isCompletedAudio) return null
        return runCatching {
            val uri = Uri.parse(entry.savedUri ?: return null)
            if (uri.scheme !in listOf("content", "file")) return null
            var name = entry.fileName
            var size = entry.downloadedBytes
            var path = ""
            if (uri.scheme == "file") {
                val file = File(uri.path ?: return null)
                if (!file.isFile || !file.canRead()) return null
                name = file.name
                path = file.absolutePath
                size = file.length()
            } else {
                // Query only the universal columns: SAF providers may reject MediaStore.DATA.
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (!cursor.moveToFirst()) return null
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex).orEmpty().ifBlank { name }
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                } ?: return null
                path = runCatching {
                    context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
                    }
                }.getOrNull().orEmpty()
            }
            if (size <= 0) return null
            val resolved = DownloadFileResolver.resolve(name, "", entry.mimeType)
            if (!resolved.isAudio) return null
            AudioItem(
                id = -entry.id, uri = uri, title = resolved.fileName.substringBeforeLast('.', resolved.fileName),
                artist = "Unknown Artist", album = entry.saveLocation.ifBlank { "Download" },
                durationMs = entry.durationMs, sizeBytes = size,
                path = path.ifBlank { uri.toString() }, mimeType = resolved.mimeType ?: "audio/mpeg",
                dateAdded = entry.id / 1000L, // Download IDs are monotonic epoch-millisecond values.
            )
        }.getOrNull()
    }
}
