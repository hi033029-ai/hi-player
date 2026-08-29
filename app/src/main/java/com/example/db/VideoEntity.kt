package com.example.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "video_records")
data class VideoEntity(
    @PrimaryKey val uriString: String,
    val title: String,
    val lastPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val lastWatchedTimestamp: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val audioDelayMs: Long = 0L,
    val subtitleOffsetMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val selectedAudioTrackIndex: Int = -1,
    val selectedSubtitleTrackIndex: Int = -1,
    val customAspectRatio: String = "FIT"
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val videoCount: Int = 0
)

@Entity(tableName = "playlist_items", primaryKeys = ["playlistId", "videoUriString"])
data class PlaylistItemEntity(
    val playlistId: Long,
    val videoUriString: String,
    val addedAt: Long = System.currentTimeMillis()
)
