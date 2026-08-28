package com.example.model

import android.net.Uri

enum class VideoResolutionBadge {
    UHD_4K,
    QHD_2K,
    FHD_1080P,
    HD_720P,
    SD
}

enum class AspectRatioMode(val displayName: String) {
    FIT("Fit to Screen"),
    FILL_CROP("Crop / Fill (16:9)"),
    CINEMA_21_9("Cinema (21:9)"),
    ORIGINAL("Original (1:1)"),
    STRETCH("Stretch")
}

data class VideoTrackInfo(
    val id: String,
    val label: String,
    val language: String?,
    val mimeType: String?,
    val isSelected: Boolean,
    val trackGroupIndex: Int,
    val trackIndex: Int
)

data class VideoItem(
    val id: Long = 0L,
    val uri: Uri = Uri.EMPTY,
    val title: String = "",
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val width: Int = 1920,
    val height: Int = 1080,
    val mimeType: String = "video/mp4",
    val dateAdded: Long = 0L,
    val folderName: String = "Videos",
    val path: String = "",
    val isHdr: Boolean = false,
    val hdrFormat: String? = null,
    val codec: String = "HEVC/H.264",
    val bitrate: Long = 0L,
    val frameRate: Float = 0f,
    val audioChannels: Int = 2,
    val lastPositionMs: Long = 0L,
    val isFavorite: Boolean = false,
    val thumbnailUrl: String? = null
) {
    val resolutionBadge: VideoResolutionBadge
        get() {
            val maxDim = maxOf(width, height)
            return when {
                maxDim >= 3840 || minOf(width, height) >= 2160 -> VideoResolutionBadge.UHD_4K
                maxDim >= 2560 || minOf(width, height) >= 1440 -> VideoResolutionBadge.QHD_2K
                maxDim >= 1920 || minOf(width, height) >= 1080 -> VideoResolutionBadge.FHD_1080P
                maxDim >= 1280 || minOf(width, height) >= 720 -> VideoResolutionBadge.HD_720P
                else -> VideoResolutionBadge.SD
            }
        }

    val formattedDuration: String
        get() {
            val totalSeconds = durationMs / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }

    val formattedSize: String
        get() {
            val kb = sizeBytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> String.format("%.2f GB", gb)
                mb >= 1.0 -> String.format("%.1f MB", mb)
                else -> String.format("%.0f KB", kb)
            }
        }

    val resolutionString: String
        get() = if (width > 0 && height > 0) "${width}x${height}" else "4K UHD"
}
