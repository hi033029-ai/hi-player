package com.example.ui.screens

import com.example.model.AudioItem

internal data class AudioLibraryPresentation(
    val tracks: List<AudioItem> = emptyList(),
    val groups: Map<String, List<AudioItem>> = emptyMap(),
)

/** Pure transform. Caller runs this on Dispatchers.Default, never during list composition. */
internal fun buildAudioLibraryPresentation(
    tracks: List<AudioItem>, searchQuery: String, audioSort: String,
    libraryMode: String, selectedCollection: String?,
): AudioLibraryPresentation {
    val filtered =
        tracks.filter { track ->
            val matchesQuery = searchQuery.isBlank() ||
                    track.title.contains(searchQuery, ignoreCase = true) ||
                    track.artist.contains(searchQuery, ignoreCase = true) ||
                    track.album.contains(searchQuery, ignoreCase = true)

            val matchesLibrary = when (libraryMode) {
                "Playlists" -> track.isFavorite
                else -> true
            }
            val matchesCollection = selectedCollection?.let { collection ->
                when (libraryMode) {
                    "Folders" -> track.path.substringBeforeLast('/', "").substringAfterLast('/', "Music") == collection
                    "Albums" -> track.album.ifBlank { "Unknown album" } == collection
                    "Artists" -> track.artist.ifBlank { "Unknown artist" } == collection
                    else -> true
                }
            } ?: true
            matchesQuery && matchesLibrary && matchesCollection
        }.let { list ->
            when (audioSort) {
                "Custom" -> list
                "Artist" -> list.sortedBy { it.artist.lowercase() }
                "Duration" -> list.sortedByDescending { it.durationMs }
                "Newest" -> list.sortedByDescending { it.dateAdded }
                else -> when (libraryMode) {
                    "Folders" -> list.sortedWith(compareBy({ it.path.substringBeforeLast('/', "").lowercase() }, { it.title.lowercase() }))
                    "Albums" -> list.sortedWith(compareBy({ it.album.lowercase() }, { it.title.lowercase() }))
                    "Artists" -> list.sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))
                    else -> list.sortedBy { it.title.lowercase() }
                }
            }
        }
    val grouped =
        when (libraryMode) {
            "Folders" -> filtered.groupBy { it.path.substringBeforeLast('/', "").substringAfterLast('/', "Music") }
            "Albums" -> filtered.groupBy { it.album.ifBlank { "Unknown album" } }
            "Artists" -> filtered.groupBy { it.artist.ifBlank { "Unknown artist" } }
            else -> emptyMap()
        }.toSortedMap(String.CASE_INSENSITIVE_ORDER)
    return AudioLibraryPresentation(filtered, grouped)
}
