package com.example.model

import android.net.Uri

data class AudioItem(
    val id: Long = 0L,
    val uri: Uri = Uri.EMPTY,
    val title: String = "Unknown Track",
    val artist: String = "Unknown Artist",
    val album: String = "Unknown Album",
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val path: String = "",
    val mimeType: String = "audio/mpeg",
    val dateAdded: Long = 0L,
    val isFavorite: Boolean = false,
    val albumId: Long = -1L,
    val albumArtUri: Uri? = null,
    val artworkUrl: String? = null
) {
    val durationFormatted: String
        get() {
            val totalSeconds = durationMs / 1000
            val hr = totalSeconds / 3600
            val min = (totalSeconds % 3600) / 60
            val sec = totalSeconds % 60
            return if (hr > 0) {
                String.format(java.util.Locale.US, "%d:%02d:%02d", hr, min, sec)
            } else {
                String.format(java.util.Locale.US, "%02d:%02d", min, sec)
            }
        }

    val audioFormat: String
        get() {
            return when {
                path.endsWith(".flac", ignoreCase = true) -> "FLAC Hi-Res"
                path.endsWith(".wav", ignoreCase = true) -> "WAV Lossless"
                path.endsWith(".m4a", ignoreCase = true) || path.endsWith(".aac", ignoreCase = true) -> "AAC"
                path.endsWith(".ogg", ignoreCase = true) || path.endsWith(".opus", ignoreCase = true) -> "OGG"
                else -> "MP3 320k"
            }
        }
}
