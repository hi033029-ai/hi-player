package com.example.model

import android.net.Uri

enum class DiscoverMediaType {
    MOVIE,
    TV
}

data class DiscoverTitle(
    val tmdbId: Long,
    val mediaType: DiscoverMediaType,
    val title: String,
    val overview: String,
    val releaseYear: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val rating: Double? = null,
    val language: String? = null
)

data class DiscoverSeason(
    val seasonNumber: Int,
    val name: String,
    val episodeCount: Int = 0,
    val posterUrl: String? = null
)

data class DiscoverDetails(
    val title: DiscoverTitle,
    val runtimeMinutes: Int? = null,
    val genres: List<String> = emptyList(),
    val seasons: List<DiscoverSeason> = emptyList()
)

data class JellyfinConnection(
    val baseUrl: String,
    val username: String,
    val password: String,
    val accessToken: String,
    val userId: String,
    val serverName: String = "Jellyfin"
) {
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && accessToken.isNotBlank() && userId.isNotBlank()
}

data class GoogleDriveConnection(
    val accountName: String,
    val accessToken: String,
    val folderId: String = "root",
    val displayName: String = "Google Drive"
) {
    val isConfigured: Boolean
        get() = accessToken.isNotBlank()
}

data class WebDavConnection(
    val folderUrl: String,
    val username: String,
    val password: String,
    val displayName: String = "WebDAV"
) {
    val isConfigured: Boolean
        get() = folderUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()
}

data class MediaSourceSettings(
    val jellyfin: JellyfinConnection? = null,
    val webDav: WebDavConnection? = null,
    val googleDrive: GoogleDriveConnection? = null
) {
    val hasAnySource: Boolean
        get() = jellyfin?.isConfigured == true || webDav?.isConfigured == true || googleDrive?.isConfigured == true
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
    val artworkUrl: String? = null
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
        thumbnailUrl = artworkUrl
    )
}

data class DiscoverUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val results: List<DiscoverTitle> = emptyList(),
    val selectedDetails: DiscoverDetails? = null,
    val sourceMatches: List<RemoteMediaMatch> = emptyList(),
    val isSearchingSources: Boolean = false,
    val errorMessage: String? = null,
    val sourceMessage: String? = null
)
