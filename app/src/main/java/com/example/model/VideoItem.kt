package com.example.model

import android.net.Uri

enum class VideoResolutionBadge {
    UHD_4K,
    QHD_2K,
    FHD_1080P,
    HD_720P,
    SD
}

enum class AspectRatioMode(val displayName: String, val targetRatio: Float? = null) {
    FIT("Fit to Screen"),
    IMAX_DIGITAL("IMAX Digital (1.90:1)", 1.90f),
    IMAX_ORIGINAL("IMAX Original (1.43:1)", 1.43f),
    FILL_CROP("Crop / Fill (16:9)"),
    CINEMA_21_9("Cinema (21:9)", 21f / 9f),
    ORIGINAL("Original"),
    STRETCH("Stretch")
}

data class VideoTrackInfo(
    val id: String,
    val label: String,
    val language: String?,
    val mimeType: String?,
    /** Number of audio channels reported by Media3; zero for non-audio tracks. */
    val channelCount: Int = 0,
    val isSelected: Boolean,
    /** Whether Media3 reports this embedded track as playable on this device. */
    val isSupported: Boolean = true,
    /** Media3 track type (for example, text or image captions). */
    val trackType: Int,
    /** Index in Player.currentTracks.groups. */
    val trackGroupIndex: Int,
    /** Index inside [trackGroupIndex]. */
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
    val thumbnailUrl: String? = null,
    val isPartialDownload: Boolean = false,
    /** Bytes currently observed on disk for a growing download. */
    val downloadedBytes: Long = 0L,
    /** Expected final size when the downloader reports one; zero means unknown. */
    val downloadTotalBytes: Long = 0L,
    /** Measured from real file-size changes, never simulated. */
    val downloadSpeedBytesPerSecond: Long = 0L,
    val isNewVideo: Boolean = false
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

    val downloadProgressFraction: Float?
        get() = if (downloadTotalBytes > 0L) {
            (downloadedBytes.toDouble() / downloadTotalBytes.toDouble())
                .toFloat()
                .coerceIn(0f, 1f)
        } else {
            null
        }

    val formattedDownloadSpeed: String
        get() = when {
            downloadSpeedBytesPerSecond >= 1_048_576L ->
                String.format("%.1f MB/s", downloadSpeedBytesPerSecond / 1_048_576.0)
            downloadSpeedBytesPerSecond >= 1_024L ->
                String.format("%.0f KB/s", downloadSpeedBytesPerSecond / 1_024.0)
            downloadSpeedBytesPerSecond > 0L -> "$downloadSpeedBytesPerSecond B/s"
            else -> "Waiting for data"
        }

    val formattedDownloadStatus: String
        get() = if (downloadTotalBytes > 0L) {
            "${formattedSize} / ${formatBytes(downloadTotalBytes)} • $formattedDownloadSpeed"
        } else {
            "${formattedSize} • $formattedDownloadSpeed"
        }

    private fun formatBytes(bytes: Long): String {
        val kb = bytes / 1024.0
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
