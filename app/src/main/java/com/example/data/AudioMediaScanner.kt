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

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_ADDED
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.DURATION} > 10000"
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        try {
            val queryUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            context.contentResolver.query(
                queryUri,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(queryUri, id)
                    val title = cursor.getString(titleCol) ?: "Audio Track $id"
                    val artist = cursor.getString(artistCol) ?: "Unknown Artist"
                    val album = cursor.getString(albumCol) ?: "Unknown Album"
                    val albumId = if (albumIdCol != -1) cursor.getLong(albumIdCol) else -1L
                    val albumArtUri = if (albumId != -1L) {
                        ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
                    } else null
                    val duration = cursor.getLong(durCol)
                    val size = cursor.getLong(sizeCol)
                    val path = cursor.getString(dataCol) ?: ""
                    val mime = cursor.getString(mimeCol) ?: "audio/*"
                    val dateAdded = cursor.getLong(dateCol)

                    tracks.add(
                        AudioItem(
                            id = id,
                            uri = contentUri,
                            title = title,
                            artist = if (artist.contains("<unknown>", true)) "Hi Audio" else artist,
                            album = if (album.contains("<unknown>", true)) "Local Audio" else album,
                            durationMs = duration,
                            sizeBytes = size,
                            path = path,
                            mimeType = mime,
                            dateAdded = dateAdded,
                            albumId = albumId,
                            albumArtUri = albumArtUri
                        )
                    )
                }
                complete = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
            File(storageRoot, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio"),
            File(storageRoot, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes"),
            File(storageRoot, "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Audio"),
            File(storageRoot, "WhatsApp/Media/WhatsApp Audio"),
            File(storageRoot, "WhatsApp/Media/WhatsApp Voice Notes"),
            File(storageRoot, "WhatsApp Business/Media/WhatsApp Business Audio"),
        ).filter { it.isDirectory && it.canRead() }
            .map { directory -> runCatching { directory.canonicalFile }.getOrDefault(directory) }
            .distinctBy(File::getAbsolutePath)

        val audioExtensions = setOf("mp3", "flac", "wav", "m4a", "aac", "ogg", "opus", "wma", "alac")
        return candidates.asSequence()
            .flatMap { directory -> directory.walkTopDown().maxDepth(4) }
            .filter(File::isFile)
            .filter { file -> file.extension.lowercase(Locale.ROOT) in audioExtensions }
            .filterNot { file -> file.absolutePath in indexedPaths }
            .mapNotNull(::toMessengerAudioItem)
            .distinctBy { it.path }
            .toList()
    }

    private fun toMessengerAudioItem(file: File): AudioItem {
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
        return AudioItem(
            id = file.absolutePath.hashCode().toLong(),
            uri = Uri.fromFile(file),
            title = file.nameWithoutExtension,
            artist = "WhatsApp",
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
    }

}
