package com.example.model

import android.net.Uri

data class JellyfinConnection(
    val baseUrl: String,
    val username: String,
    val password: String,
    val accessToken: String,
    val userId: String,
    val serverName: String = "Jellyfin",
    val deviceId: String = "",
) {
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && accessToken.isNotBlank() && userId.isNotBlank()
}

data class GoogleDriveConnection(
    val accountName: String = "Google Drive",
    val documentTreeUri: String,
    val displayName: String = "Google Drive",
) {
    val isConfigured: Boolean
        get() = documentTreeUri.isNotBlank()
}

data class WebDavConnection(
    val folderUrl: String,
    val username: String,
    val password: String,
    val displayName: String = "WebDAV",
) {
    val isConfigured: Boolean
        get() = folderUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()
}

data class MediaSourceSettings(
    val jellyfin: JellyfinConnection? = null,
    val webDav: WebDavConnection? = null,
    val googleDrive: GoogleDriveConnection? = null,
) {
    val hasAnySource: Boolean
        get() = jellyfin?.isConfigured == true || webDav?.isConfigured == true
}

data class RemoteMediaMatch(
    val id: String,
    val sourceName: String,
    val title: String,
    val subtitle: String? = null,
    val streamUri: Uri,
    val streamMimeType: String? = null,
    val requestHeaders: Map<String, String> = emptyMap(),
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val isHdr: Boolean = false,
    val hdrFormat: String? = null,
    val codec: String = "Remote stream",
    val audioLabels: List<String> = emptyList(),
    val artworkUrl: String? = null,
) {
    val is4k: Boolean
        get() = width >= 3840 || height >= 2160

    fun toVideoItem(): VideoItem = VideoItem(
        id = id.hashCode().toLong(),
        uri = streamUri,
        title = title,
        durationMs = durationMs,
        sizeBytes = sizeBytes,
        width = width,
        height = height,
        mimeType = streamMimeType ?: "video/*",
        folderName = sourceName,
        path = "remote:$id",
        isHdr = isHdr,
        hdrFormat = hdrFormat,
        codec = codec,
        audioChannels = 0,
        thumbnailUrl = artworkUrl,
    )
}
