package com.example.viewmodel

import android.app.Application
import android.content.ContentUris
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import com.example.model.AudioItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val _audioList = MutableStateFlow<List<AudioItem>>(emptyList())
    val audioList = _audioList.asStateFlow()

    private val _currentTrack = MutableStateFlow<AudioItem?>(null)
    val currentTrack = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs = _durationMs.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow("All")
    val selectedFilter = _selectedFilter.asStateFlow()

    private val _equalizerPreset = MutableStateFlow("Cinema 3D")
    val equalizerPreset = _equalizerPreset.asStateFlow()

    private val _activeSubtitle = MutableStateFlow<String?>(null)
    val activeSubtitle = _activeSubtitle.asStateFlow()

    private var lyricsSyncJob: Job? = null
    private var lyricsLines: List<Pair<Long, String>> = emptyList()
    private var loadTracksJob: Job? = null

    private val _matchingVideoState = MutableStateFlow<MatchingVideoState>(MatchingVideoState.Idle)
    val matchingVideoState = _matchingVideoState.asStateFlow()

    sealed class MatchingVideoState {
        object Idle : MatchingVideoState()
        object Searching : MatchingVideoState()
        data class Found(val uri: Uri, val title: String) : MatchingVideoState()
        object NotFound : MatchingVideoState()
    }

    /**
     * Searches the device's actual video library (MediaStore) for a video
     * whose filename closely matches the current track's title/artist.
     * Previously "Find Video" was a fake dialog listing hardcoded made-up
     * results and its "play" action did nothing at all.
     */
    fun findMatchingVideo(track: AudioItem) {
        _matchingVideoState.value = MatchingVideoState.Searching
        viewModelScope.launch(Dispatchers.IO) {
            val coreTitle = normalizeForMatch(track.title)
            val coreArtist = normalizeForMatch(track.artist)
            var bestUri: Uri? = null
            var bestTitle: String? = null
            var bestScore = 0

            try {
                val projection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME)
                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                } else {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }
                getApplication<Application>().contentResolver.query(
                    collection, projection, null, null, null
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                    while (cursor.moveToNext()) {
                        val displayName = cursor.getString(nameCol) ?: continue
                        val normalizedName = normalizeForMatch(displayName)
                        var score = 0
                        if (coreTitle.isNotBlank() && normalizedName.contains(coreTitle)) score += 2
                        if (coreArtist.isNotBlank() && normalizedName.contains(coreArtist)) score += 1
                        if (score > bestScore) {
                            bestScore = score
                            val id = cursor.getLong(idCol)
                            bestUri = ContentUris.withAppendedId(collection, id)
                            bestTitle = displayName
                        }
                    }
                }
            } catch (_: Exception) {
                // fall through to NotFound below
            }

            withContext(Dispatchers.Main) {
                _matchingVideoState.value = if (bestUri != null && bestScore > 0) {
                    MatchingVideoState.Found(bestUri!!, bestTitle ?: track.title)
                } else {
                    MatchingVideoState.NotFound
                }
            }
        }
    }

    fun resetMatchingVideoState() {
        _matchingVideoState.value = MatchingVideoState.Idle
    }

    private fun normalizeForMatch(text: String): String {
        return text.lowercase()
            .replace(Regex("\\.[a-z0-9]{2,4}$"), "") // strip file extension
            .replace(Regex("[\\[(].*?[\\])]"), "") // strip bracketed/parenthesized tags
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
    }

    /**
     * Fetches real, time-synced lyrics for the current track from lrclib.net
     * (a free, keyless public API) and syncs them to playback position -
     * replacing the previous fake feature that just set one static made-up
     * label string with no real timed content behind it.
     */
    private val _lyricsText = MutableStateFlow<String?>(null)
    val lyricsText = _lyricsText.asStateFlow()

    fun fetchAndSyncLyrics(track: AudioItem) {
        lyricsSyncJob?.cancel()
        _activeSubtitle.value = null
        _lyricsText.value = null
        lyricsLines = emptyList()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val titleQ = java.net.URLEncoder.encode(track.title, "UTF-8")
                val artistQ = java.net.URLEncoder.encode(track.artist, "UTF-8")

                // 1. Try an exact match first.
                var synced = ""
                var plain = ""
                val durationQ = (track.durationMs / 1000L).coerceIn(1L, 3600L)
                val exactUrl = java.net.URL("https://lrclib.net/api/get?track_name=$titleQ&artist_name=$artistQ&album_name=${java.net.URLEncoder.encode(track.album, "UTF-8")}&duration=$durationQ")
                val exactConn = exactUrl.openConnection() as java.net.HttpURLConnection
                exactConn.setRequestProperty("User-Agent", "HiPlayer/1.0 (https://github.com/hi033029-ai/hi-player)")
                exactConn.connectTimeout = 8000
                exactConn.readTimeout = 8000
                if (exactConn.responseCode in 200..299) {
                    val obj = org.json.JSONObject(exactConn.inputStream.bufferedReader().use { it.readText() })
                    synced = obj.optString("syncedLyrics", "")
                    plain = obj.optString("plainLyrics", "")
                }
                exactConn.disconnect()

                // 2. Exact match found nothing - fall back to lrclib's fuzzy
                // search endpoint, which tolerates messier real-world file
                // metadata (extra words, punctuation differences, etc.) far
                // better than the exact-match endpoint.
                if (synced.isBlank() && plain.isBlank()) {
                    val searchQ = java.net.URLEncoder.encode("${track.artist} ${track.title}", "UTF-8")
                    val searchUrl = java.net.URL("https://lrclib.net/api/search?q=$searchQ")
                    val searchConn = searchUrl.openConnection() as java.net.HttpURLConnection
                    searchConn.setRequestProperty("User-Agent", "HiPlayer/1.0 (https://github.com/hi033029-ai/hi-player)")
                    searchConn.connectTimeout = 8000
                    searchConn.readTimeout = 8000
                    if (searchConn.responseCode in 200..299) {
                        val text = searchConn.inputStream.bufferedReader().use { it.readText() }
                        val arr = org.json.JSONArray(text)
                        if (arr.length() > 0) {
                            val best = arr.getJSONObject(0)
                            synced = best.optString("syncedLyrics", "")
                            plain = best.optString("plainLyrics", "")
                        }
                    }
                    searchConn.disconnect()
                }

                // Plain-lyrics fallback for tracks not available in LRCLIB.
                if (synced.isBlank() && plain.isBlank()) {
                    val fallbackUrl = java.net.URL("https://api.lyrics.ovh/v1/$artistQ/$titleQ")
                    val fallbackConn = fallbackUrl.openConnection() as java.net.HttpURLConnection
                    fallbackConn.connectTimeout = 8000
                    fallbackConn.readTimeout = 8000
                    if (fallbackConn.responseCode in 200..299) {
                        val fallbackObj = org.json.JSONObject(fallbackConn.inputStream.bufferedReader().use { it.readText() })
                        plain = fallbackObj.optString("lyrics", "")
                    }
                    fallbackConn.disconnect()
                }

                when {
                    synced.isNotBlank() -> {
                        withContext(Dispatchers.Main) { _lyricsText.value = synced }
                        val lineRegex = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})](.*)""")
                        lyricsLines = synced.lines().mapNotNull { line ->
                            val match = lineRegex.find(line) ?: return@mapNotNull null
                            val minutes = match.groupValues[1].toLong()
                            val seconds = match.groupValues[2].toLong()
                            val fraction = match.groupValues[3].padEnd(3, '0').take(3).toLong()
                            val ms = (minutes * 60_000L) + (seconds * 1000L) + fraction
                            ms to match.groupValues[4].trim()
                        }.sortedBy { it.first }
                        withContext(Dispatchers.Main) { startLyricsSyncLoop() }
                    }
                    plain.isNotBlank() -> {
                        // No time-synced version available, but real lyrics
                        // text exists - show it as static (non-karaoke) text
                        // rather than treating "no sync data" as "no lyrics".
                        withContext(Dispatchers.Main) {
                            _lyricsText.value = plain
                            _activeSubtitle.value = plain.replace("\n", "  •  ").take(500)
                        }
                    }
                    else -> {
                        withContext(Dispatchers.Main) {
                            _activeSubtitle.value = null
                        }
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    _activeSubtitle.value = null
                }
            }
        }
    }

    private fun startLyricsSyncLoop() {
        lyricsSyncJob?.cancel()
        lyricsSyncJob = viewModelScope.launch {
            while (isActive) {
                val positionMs = _currentPositionMs.value
                val line = lyricsLines.lastOrNull { it.first <= positionMs }?.second
                _activeSubtitle.value = line?.takeIf { it.isNotBlank() } ?: _activeSubtitle.value
                delay(300)
            }
        }
    }

    fun clearLyrics() {
        lyricsSyncJob?.cancel()
        lyricsLines = emptyList()
        _lyricsText.value = null
        _activeSubtitle.value = null
    }

    fun downloadLyrics(context: android.content.Context, track: AudioItem) {
        val content = _lyricsText.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val downloads = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                )
                if (!downloads.exists()) downloads.mkdirs()
                val safeName = track.title.replace(Regex("[^a-zA-Z0-9._-]+"), "_").trim('_')
                val extension = if (Regex("""\[\d{2}:\d{2}\.""").containsMatchIn(content)) ".lrc" else ".txt"
                val output = java.io.File(downloads, "${safeName.ifBlank { "lyrics" }}$extension")
                output.writeText(content)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Lyrics saved to Downloads/${output.name}", android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Couldn't save lyrics", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private val _isFullScreenPlayerOpen = MutableStateFlow(false)
    val isFullScreenPlayerOpen = _isFullScreenPlayerOpen.asStateFlow()

    fun openFullScreenPlayer() {
        _isFullScreenPlayerOpen.value = true
    }

    fun closeFullScreenPlayer() {
        _isFullScreenPlayerOpen.value = false
    }

    private var exoPlayer: ExoPlayer? = null
    private var progressJob: Job? = null

    init {
        initPlayer()
        loadAudioTracks()
    }

    private fun initPlayer() {
        val renderersFactory = DefaultRenderersFactory(getApplication()).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            setEnableDecoderFallback(true)
        }
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        exoPlayer = ExoPlayer.Builder(getApplication(), renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    _isPlaying.value = playing
                    if (playing) {
                        startProgressUpdates()
                    } else {
                        progressJob?.cancel()
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        _durationMs.value = duration.coerceAtLeast(0L)
                    } else if (playbackState == Player.STATE_ENDED) {
                        playNext()
                    }
                }
            })
        }
    }

    fun loadAudioTracks() {
        loadTracksJob?.cancel()
        loadTracksJob = viewModelScope.launch {
            val tracks = withContext(Dispatchers.IO) {
                queryDeviceAudio()
            }
            _audioList.value = tracks
        }
    }

    private fun queryDeviceAudio(): List<AudioItem> {
        val tracks = mutableListOf<AudioItem>()
        val context = getApplication<Application>()

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
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Add built-in sample demo tracks if device audio is empty so user can experience music features immediately
        if (tracks.isEmpty()) {
            tracks.addAll(getDemoTracks())
        }

        return tracks
    }

    private fun getDemoTracks(): List<AudioItem> {
        return listOf(
            AudioItem(
                id = 901L,
                uri = Uri.parse("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"),
                title = "Cyberpunk Neon Nights (Hi-Res Lossless)",
                artist = "Hi Studio Master",
                album = "Cinema Acoustics Vol. 1",
                durationMs = 245000L,
                sizeBytes = 28500000L,
                path = "/storage/emulated/0/Music/Cyberpunk_Neon.mp3",
                mimeType = "audio/mp3",
                artworkUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600&auto=format&fit=crop&q=80"
            ),
            AudioItem(
                id = 902L,
                uri = Uri.parse("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3"),
                title = "Deep Bass Symphony 432Hz",
                artist = "Acoustic Dynamics",
                album = "Dolby Atmos Demonstrations",
                durationMs = 312000L,
                sizeBytes = 19400000L,
                path = "/storage/emulated/0/Music/Deep_Bass_Symphony.mp3",
                mimeType = "audio/mp3",
                artworkUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=600&auto=format&fit=crop&q=80"
            ),
            AudioItem(
                id = 903L,
                uri = Uri.parse("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3"),
                title = "Midnight Horizon (Acoustic Remaster)",
                artist = "Starlight Ensemble",
                album = "Audiophile Showcase",
                durationMs = 198000L,
                sizeBytes = 14200000L,
                path = "/storage/emulated/0/Music/Midnight_Horizon.mp3",
                mimeType = "audio/mp3",
                artworkUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&auto=format&fit=crop&q=80"
            ),
            AudioItem(
                id = 904L,
                uri = Uri.parse("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3"),
                title = "Epic Cinematic Score (Surround Sound)",
                artist = "Hans Orchestra",
                album = "Hi-Fi Movie Soundtracks",
                durationMs = 380000L,
                sizeBytes = 42000000L,
                path = "/storage/emulated/0/Music/Cinematic_Score.mp3",
                mimeType = "audio/mp3",
                artworkUrl = "https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=600&auto=format&fit=crop&q=80"
            )
        )
    }

    fun playTrack(track: AudioItem) {
        _currentTrack.value = track
        _isFullScreenPlayerOpen.value = true
        // Prepare lyrics silently in the background for the track now playing.
        // Results remain available to the existing Lyrics menu and download action.
        fetchAndSyncLyrics(track)
        exoPlayer?.let { player ->
            player.stop()
            player.clearMediaItems()
            val mediaItem = MediaItem.fromUri(track.uri)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
        }
    }

    fun togglePlayPause() {
        exoPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
            } else {
                if (_currentTrack.value == null && _audioList.value.isNotEmpty()) {
                    playTrack(_audioList.value.first())
                } else {
                    player.play()
                }
            }
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
        _currentPositionMs.value = positionMs
    }

    fun playNext() {
        val list = _audioList.value
        val current = _currentTrack.value ?: return
        val currentIndex = list.indexOfFirst { it.id == current.id }
        if (currentIndex != -1 && list.isNotEmpty()) {
            val nextIndex = (currentIndex + 1) % list.size
            playTrack(list[nextIndex])
        }
    }

    fun playPrevious() {
        val list = _audioList.value
        val current = _currentTrack.value ?: return
        val currentIndex = list.indexOfFirst { it.id == current.id }
        if (currentIndex > 0) {
            playTrack(list[currentIndex - 1])
        } else if (list.isNotEmpty()) {
            playTrack(list.last())
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: String) {
        _selectedFilter.value = filter
    }

    fun stopTrack() {
        exoPlayer?.let { player ->
            player.stop()
            player.clearMediaItems()
        }
        _currentTrack.value = null
        _isPlaying.value = false
        _currentPositionMs.value = 0L
        clearLyrics()
        _isFullScreenPlayerOpen.value = false
        progressJob?.cancel()
    }

    fun setEqualizerPreset(preset: String) {
        _equalizerPreset.value = preset
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                exoPlayer?.let { player ->
                    _currentPositionMs.value = player.currentPosition.coerceAtLeast(0L)
                    _durationMs.value = player.duration.coerceAtLeast(0L)
                }
                delay(500)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        progressJob?.cancel()
        exoPlayer?.release()
        exoPlayer = null
    }
}
