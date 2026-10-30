package com.example.viewmodel

import android.app.Application
import android.content.ContentUris
import android.media.MediaMetadataRetriever
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Build
import android.os.Environment
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val musicPrefs = application.getSharedPreferences("music_preferences", android.content.Context.MODE_PRIVATE)

    private fun trackKey(track: AudioItem): String = track.path.ifBlank { track.uri.toString() }

    private fun applySavedTrackOrder(tracks: List<AudioItem>): List<AudioItem> {
        val positions = musicPrefs.getString("track_display_order", "")
            .orEmpty()
            .split("\u0001")
            .filter(String::isNotBlank)
            .withIndex()
            .associate { it.value to it.index }
        if (positions.isEmpty()) return tracks
        return tracks.sortedWith(
            compareBy<AudioItem> { positions[trackKey(it)] ?: Int.MAX_VALUE }
                .thenBy { it.title.lowercase() }
        )
    }

    fun moveTrackInDisplayOrder(fromKey: String, toKey: String) {
        val current = _audioList.value.toMutableList()
        val from = current.indexOfFirst { trackKey(it) == fromKey }
        val to = current.indexOfFirst { trackKey(it) == toKey }
        if (from < 0 || to < 0 || from == to) return
        val item = current.removeAt(from)
        current.add(to, item)
        _audioList.value = current
        musicPrefs.edit()
            .putString("track_display_order", current.joinToString("\u0001", transform = ::trackKey))
            .apply()
    }

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

    private fun normalizeForMatch(text: String): String {
        return text.lowercase()
            .replace(Regex("\\.[a-z0-9]{2,4}$"), "")
            .replace(Regex("[\\[(].*?[\\])]"), "")
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

    private val _lyricsEnabled = MutableStateFlow(false)
    val lyricsEnabled = _lyricsEnabled.asStateFlow()

    fun setLyricsEnabled(enabled: Boolean) {
        _lyricsEnabled.value = enabled
        if (!enabled) clearLyrics()
    }

    private val _currentLyricLineIndex = MutableStateFlow(-1)
    val currentLyricLineIndex = _currentLyricLineIndex.asStateFlow()

    fun fetchAndSyncLyrics(track: AudioItem) {
        lyricsSyncJob?.cancel()
        _activeSubtitle.value = null
        _lyricsText.value = null
        _currentLyricLineIndex.value = -1
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

                // Broaden the search for real-world files whose MediaStore metadata
                // is missing or whose title still contains filename tags.
                if (synced.isBlank() && plain.isBlank()) {
                    val filenameTitle = track.path.substringAfterLast('/').substringBeforeLast('.')
                    val candidates = linkedSetOf(
                        track.title to track.artist,
                        filenameTitle to track.artist
                    ).flatMap { (candidateTitle, candidateArtist) ->
                        val cleanedTitle = normalizeForMatch(candidateTitle)
                        val cleanedArtist = normalizeForMatch(candidateArtist)
                        val split = candidateTitle.split(" - ", " — ", limit = 2)
                        buildList {
                            if (cleanedTitle.isNotBlank() && cleanedArtist.isNotBlank()) {
                                add(cleanedArtist to cleanedTitle)
                            }
                            if (split.size == 2) {
                                add(normalizeForMatch(split[0]) to normalizeForMatch(split[1]))
                            }
                        }
                    }.distinct()

                    for ((candidateArtist, candidateTitle) in candidates) {
                        if (synced.isNotBlank() || plain.isNotBlank()) break
                        val candidateQ = java.net.URLEncoder.encode("$candidateArtist $candidateTitle", "UTF-8")
                        val candidateUrl = java.net.URL("https://lrclib.net/api/search?q=$candidateQ")
                        val candidateConn = candidateUrl.openConnection() as java.net.HttpURLConnection
                        candidateConn.setRequestProperty("User-Agent", "HiPlayer/1.0")
                        candidateConn.connectTimeout = 8000
                        candidateConn.readTimeout = 8000
                        try {
                            if (candidateConn.responseCode in 200..299) {
                                val arr = org.json.JSONArray(candidateConn.inputStream.bufferedReader().use { it.readText() })
                                if (arr.length() > 0) {
                                    val best = arr.getJSONObject(0)
                                    synced = best.optString("syncedLyrics", "")
                                    plain = best.optString("plainLyrics", "")
                                }
                            }
                        } finally {
                            candidateConn.disconnect()
                        }
                    }
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
                val lineIndex = lyricsLines.indexOfLast { it.first <= positionMs }
                _currentLyricLineIndex.value = lineIndex
                _activeSubtitle.value = lyricsLines.getOrNull(lineIndex)?.second?.takeIf { it.isNotBlank() }
                delay(300)
            }
        }
    }

    fun clearLyrics() {
        lyricsSyncJob?.cancel()
        lyricsLines = emptyList()
        _currentLyricLineIndex.value = -1
        _lyricsText.value = null
        _activeSubtitle.value = null
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

    private val cacheReady = kotlinx.coroutines.CompletableDeferred<Unit>()
    private val refreshCoordinator = CoalescingRefresh(viewModelScope) {
        cacheReady.await()
        val result = try {
            withContext(Dispatchers.IO) { com.example.data.AudioMediaScanner.scan(getApplication()) }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
        } catch (error: Exception) {
            android.util.Log.w("HiLibrary", "Audio refresh unavailable; keeping library", error)
            return@CoalescingRefresh
        }
        if (result.complete) {
            _audioList.value = withContext(Dispatchers.Default) { applySavedTrackOrder(result.tracks) }
            withContext(Dispatchers.IO) { com.example.data.MediaLibraryCache.saveAudio(getApplication(), result.tracks) }
        } else if (result.tracks.isNotEmpty()) {
            _audioList.value = withContext(Dispatchers.Default) { applySavedTrackOrder((result.tracks + _audioList.value).distinctBy(::trackKey)) }
        }
    }
    private val audioObserver = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { loadAudioTracks() }
    }
    fun loadAudioTracks() { refreshCoordinator.request() }

    init {
        initPlayer()
        viewModelScope.launch {
            try {
                val cached = withContext(Dispatchers.IO) { com.example.data.MediaLibraryCache.readAudio(application) }
                if (cached != null) _audioList.value = withContext(Dispatchers.Default) { applySavedTrackOrder(cached) }
            } finally { cacheReady.complete(Unit) }
            loadAudioTracks()
        }
        application.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, audioObserver)
        if (Build.VERSION.SDK_INT >= 29) {
            application.contentResolver.registerContentObserver(MediaStore.Downloads.EXTERNAL_CONTENT_URI, true, audioObserver)
        }
        com.example.downloads.BrowserDownloads.restore(application)
        viewModelScope.launch {
            com.example.downloads.BrowserDownloads.downloads.map { entries ->
                entries.filter { it.isCompletedAudio }.map { listOf(it.id, it.savedUri, it.fileName, it.mimeType, it.durationMs) }
            }.distinctUntilChanged().collect { loadAudioTracks() }
        }

        viewModelScope.launch { while (isActive) { delay(60_000); loadAudioTracks() } }
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
        _lyricsEnabled.value = false
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
        refreshCoordinator.close()
        getApplication<Application>().contentResolver.unregisterContentObserver(audioObserver)
        progressJob?.cancel()
        exoPlayer?.release()
        exoPlayer = null
    }
}
