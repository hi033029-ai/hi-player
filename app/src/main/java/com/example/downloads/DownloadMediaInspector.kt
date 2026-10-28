package com.example.downloads

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import java.io.InputStream

/** Looks at the actual saved bytes, including servers that return octet-stream for video. */
internal object DownloadMediaInspector {
    data class Info(val resolved: DownloadFileResolver.Result, val durationMs: Long = 0, val width: Int = 0, val height: Int = 0)
    fun inspect(file: File, entry: BrowserDownload): Info = inspect(entry, file.inputStream().use(::prefix)) { setDataSource(file.absolutePath) }
    fun inspect(context: Context, uri: Uri, entry: BrowserDownload): Info {
        val bytes = context.contentResolver.openInputStream(uri)?.use(::prefix) ?: byteArrayOf()
        return inspect(entry, bytes) { setDataSource(context, uri) }
    }
    private fun prefix(input: InputStream): ByteArray {
        val data = ByteArray(65536)
        var length = 0
        while (length < data.size) { val n = input.read(data, length, data.size - length); if (n <= 0) break; length += n }
        return data.copyOf(length)
    }
    private fun inspect(entry: BrowserDownload, prefix: ByteArray, source: MediaMetadataRetriever.() -> Unit): Info {
        var mime: String? = null
        var duration = 0L; var width = 0; var height = 0
        val retriever = MediaMetadataRetriever()
        try {
            retriever.source()
            mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
            val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
            if (!hasVideo && hasAudio && mime == "video/mp4") mime = "audio/mp4"
            if (!hasVideo && hasAudio && mime == "video/webm") mime = "audio/webm"
        } catch (_: Exception) {
            // The platform may lack a parser for MKV/AV1; container signatures still work.
        } finally { runCatching { retriever.release() } }
        return Info(DownloadFileResolver.resolve(entry.fileName, entry.sourceUrl, entry.mimeType, prefix = prefix, detectedMime = mime), duration, width, height)
    }
}
