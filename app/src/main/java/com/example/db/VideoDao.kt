package com.example.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query("SELECT * FROM video_records WHERE uriString = :uriString LIMIT 1")
    suspend fun getVideoRecord(uriString: String): VideoEntity?

    @Query("SELECT * FROM video_records WHERE uriString = :uriString LIMIT 1")
    fun observeVideoRecord(uriString: String): Flow<VideoEntity?>

    @Query("SELECT * FROM video_records ORDER BY lastWatchedTimestamp DESC")
    fun getRecentHistory(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM video_records WHERE isFavorite = 1 ORDER BY lastWatchedTimestamp DESC")
    fun getFavorites(): Flow<List<VideoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(video: VideoEntity)

    @Query("UPDATE video_records SET lastPositionMs = :positionMs, durationMs = :durationMs, lastWatchedTimestamp = :timestamp WHERE uriString = :uriString")
    suspend fun updateProgress(uriString: String, positionMs: Long, durationMs: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE video_records SET isFavorite = :isFavorite WHERE uriString = :uriString")
    suspend fun updateFavorite(uriString: String, isFavorite: Boolean)

    @Query("DELETE FROM video_records WHERE uriString = :uriString")
    suspend fun deleteRecord(uriString: String)

    @Query("DELETE FROM video_records")
    suspend fun clearHistory()

    // Playlists
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistItem(item: PlaylistItemEntity)

    @Query("SELECT videoUriString FROM playlist_items WHERE playlistId = :playlistId ORDER BY addedAt ASC")
    fun getPlaylistVideos(playlistId: Long): Flow<List<String>>

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND videoUriString = :uriString")
    suspend fun removeVideoFromPlaylist(playlistId: Long, uriString: String)
}
