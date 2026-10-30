package com.example.downloads

internal enum class BrowserDownloadStatus { QUEUED, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED }

internal data class BrowserDownload(
    val id: Long,
    val fileName: String,
    val sourceUrl: String,
    val mimeType: String? = null,
    val userAgent: String = "",
    val cookie: String = "",
    val referer: String = "",
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val speedBytesPerSecond: Long = 0,
    val status: BrowserDownloadStatus = BrowserDownloadStatus.QUEUED,
    val etag: String? = null,
    val modified: String? = null,
    val savedUri: String? = null,
    val saveLocation: String = "Download",
    val error: String? = null,
    val retryCount: Int = 0,
    val formatChecked: Boolean = false,
    val durationMs: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val destinationTreeUri: String? = null,
    val customName: Boolean = false,
    val finalizing: Boolean = false,
) {
    val isCompletedAudio get() = status == BrowserDownloadStatus.COMPLETED && savedUri != null &&
        DownloadFileResolver.isAudioFile(fileName, mimeType)
    val isVideo get() = mimeType?.startsWith("video/") == true
    val isActive get() = status in setOf(BrowserDownloadStatus.QUEUED, BrowserDownloadStatus.RUNNING, BrowserDownloadStatus.PAUSED)
    val needsService get() = status == BrowserDownloadStatus.QUEUED || status == BrowserDownloadStatus.RUNNING
    val etaSeconds: Long? get() = if (totalBytes > downloadedBytes && speedBytesPerSecond > 0) (totalBytes - downloadedBytes) / speedBytesPerSecond else null
}
