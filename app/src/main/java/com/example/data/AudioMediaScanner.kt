package com.example.data
import android.content.ContentUris
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.example.model.AudioItem
import java.io.File
import java.util.Locale

internal object AudioMediaScanner {
    data class Snapshot(val tracks: List<AudioItem>, val complete: Boolean)
    fun scan(context: android.content.Context): Snapshot {
        val tracks = mutableListOf<AudioItem>()
        var complete = false

        try {
            val queryUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            // Include all indexed audio: downloads/recordings may have IS_MUSIC=0
            // or zero/short duration until Android finishes extracting metadata.
            context.contentResolver.query(queryUri, null, null, null, "${MediaStore.Audio.Media.DATE_ADDED} DESC")?.use { cursor ->
                fun text(column: String, fallback: String = ""): String {
                    val index = cursor.getColumnIndex(column)
                    return if (index < 0 || cursor.isNull(index)) fallback else cursor.getString(index).orEmpty().ifBlank { fallback }
                }
                fun number(column: String, fallback: Long = 0): Long {
                    val index = cursor.getColumnIndex(column)
                    return if (index < 0 || cursor.isNull(index)) fallback else cursor.getLong(index)
                }
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val albumId = number(MediaStore.Audio.Media.ALBUM_ID, -1)
                    val path = text(MediaStore.Audio.Media.DATA)
                    val displayName = text(MediaStore.Audio.Media.DISPLAY_NAME, File(path).nameWithoutExtension.ifBlank { "Audio Track $id" })
                    tracks += AudioItem(
                        id = id, uri = ContentUris.withAppendedId(queryUri, id),
                        title = text(MediaStore.Audio.Media.TITLE, displayName),
                        artist = text(MediaStore.Audio.Media.ARTIST, "Unknown Artist").replace("<unknown>", "Unknown Artist", true),
                        album = text(MediaStore.Audio.Media.ALBUM, "Local Audio").replace("<unknown>", "Local Audio", true),
                        durationMs = number(MediaStore.Audio.Media.DURATION), sizeBytes = number(MediaStore.Audio.Media.SIZE),
                        path = path, mimeType = text(MediaStore.Audio.Media.MIME_TYPE, "audio/*"),
                        dateAdded = number(MediaStore.Audio.Media.DATE_ADDED), albumId = albumId,
                        albumArtUri = if (albumId >= 0) ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId) else null,
                    )
                }
                complete = true
            }
        } catch (e: Exception) {
            android.util.Log.w("HiLibrary", "Audio provider unavailable; preserving cached music", e)
        }

        // WhatsApp voice notes and audio files are sometimes not indexed as
        // Music by MediaStore. Include only its well-known shared-media
        // directories that Android lets us read, then deduplicate by path.
        tracks += scanKnownMessengerAudio(tracks.map { it.path }.filter(String::isNotBlank).toSet())
        val uniqueTracks = tracks.distinctBy { track -> track.path.ifBlank { track.uri.toString() } }

        return Snapshot(uniqueTracks, complete)
    }

    private fun scanKnownMessengerAudio(indexedPaths: Set<String>): List<AudioItem> {
        val storageRoot = Environment.getExternalStorageDirectory()
        val candidates = listOf(
            File(storageRoot, "Music"),
            File(storageRoot, "Download"),
            File(storageRoot, "Downloads"),
            File(storageRoot, "Recordings"),
            File(storageRoot, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio"),
            File(storageRoot, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes"),
            File(storageRoot, "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Audio"),
            File(storageRoot, "WhatsApp/Media/WhatsApp Audio"),
            File(storageRoot, "WhatsApp/Media/WhatsApp Voice Notes"),
            File(storageRoot, "WhatsApp Business/Media/WhatsApp Business Audio"),
        ).filter { it.isDirectory && it.canRead() }
            .map { directory -> runCatching { directory.canonicalFile }.getOrDefault(directory) }
            .distinctBy(File::getAbsolutePath)

        val audioExtensions = setOf("mp3", "flac", "wav", "m4a", "aac", "ogg", "opus", "wma", "alac", "aiff", "aif", "amr", "3ga")
        return candidates.asSequence()
            .flatMap { directory -> directory.walkTopDown().maxDepth(4) }
            .filter { it.isFile && it.canRead() && it.length() > 0 }
            .filter { file -> file.extension.lowercase(Locale.ROOT) in audioExtensions }
            .filterNot { file -> file.absolutePath in indexedPaths }
            .mapNotNull(::toMessengerAudioItem)
            .distinctBy { it.path }
            .toList()
    }

    private val metadataCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Pair<Long, Long>, AudioItem>>()
    private fun toMessengerAudioItem(file: File): AudioItem {
        val fingerprint = file.length() to file.lastModified()
        metadataCache[file.absolutePath]?.takeIf { it.first == fingerprint }?.let { return it.second }
        // An unreadable embedded duration must not hide a legitimate WhatsApp
        // file. Media3 can still attempt playback from its readable file URI.
        val duration = runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?: 0L
            } finally {
                retriever.release()
            }
        }.getOrDefault(0L)
        val item = AudioItem(
            id = file.absolutePath.hashCode().toLong(),
            uri = Uri.fromFile(file),
            title = file.nameWithoutExtension,
            artist = if (file.absolutePath.contains("WhatsApp", true)) "WhatsApp" else "Unknown Artist",
            album = file.parentFile?.name ?: "WhatsApp Audio",
            durationMs = duration,
            sizeBytes = file.length(),
            path = file.absolutePath,
            mimeType = when (file.extension.lowercase(Locale.ROOT)) {
                "opus" -> "audio/opus"
                "ogg" -> "audio/ogg"
                "m4a" -> "audio/mp4"
                else -> "audio/*"
            },
            dateAdded = file.lastModified() / 1000L,
        )
        if (metadataCache.size > 8000) metadataCache.clear()
        metadataCache[file.absolutePath] = fingerprint to item
        return item
    }

}
