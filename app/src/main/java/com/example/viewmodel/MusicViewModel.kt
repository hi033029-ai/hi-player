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
import kotlinx.coroutines.flow.stateIn
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
    val audioList = kotlinx.coroutines.flow.combine(
        _audioList, com.example.data.RecycleBinVisibility.hiddenPaths
    ) { tracks, hidden ->
        tracks.filterNot { com.example.data.RecycleBinVisibility.isHidden(it.path, hidden) }
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())

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

    private val musicEqualizer = com.example.audio.MusicEqualizer()
    private val _equalizerPreset = MutableStateFlow(musicPrefs.getString("eq_preset_v1", "Flat")
        ?.takeIf { it in com.example.audio.MusicEqualizer.presets || it == "Custom" } ?: "Flat")
    private val _equalizerBands = MutableStateFlow(List(5) { musicPrefs.getFloat("eq_band_$it", 0f).takeIf { v -> v.isFinite() }?.coerceIn(-12f, 12f) ?: 0f })
    val equalizerBands = _equalizerBands.asStateFlow()
    private val _equalizerStatus = MutableStateFlow("Start playback to activate the equalizer.")
    val equalizerStatus = _equalizerStatus.asStateFlow()
    val equalizerPreset = _equalizerPreset.asStateFlow()

    private val _activeSubtitle = MutableStateFlow<String?>(null)
    val activeSubtitle = _activeSubtitle.asStateFlow()

    private var lyricsSyncJob: Job? = null
    private var lyricsLines: List<Pair<Long, String>> = emptyList()
    private var loadTracksJob: Job? = null

    private var lyricsFetchJob: Job? = null
    private var lyricsGeneration = 0L
    private val lyricsRepository = com.example.lyrics.LyricsRepository(application)
    private val _lyricsText = MutableStateFlow<String?>(null)
    val lyricsText = _lyricsText.asStateFlow()
    private val _lyricsEnabled = MutableStateFlow(false)
    val lyricsEnabled = _lyricsEnabled.asStateFlow()
    private val _currentLyricLineIndex = MutableStateFlow(-1)
    val currentLyricLineIndex = _currentLyricLineIndex.asStateFlow()
    private val _lyricsMessage = MutableStateFlow<String?>(null)
    val lyricsMessage = _lyricsMessage.asStateFlow()
    private val _lyricsLoading = MutableStateFlow(false)
    val lyricsLoading = _lyricsLoading.asStateFlow()
    private val _lyricsRetryable = MutableStateFlow(false)
    val lyricsRetryable = _lyricsRetryable.asStateFlow()

    fun setLyricsEnabled(enabled: Boolean) {
        _lyricsEnabled.value = enabled
        if (!enabled) {
            lyricsGeneration++
            lyricsFetchJob?.cancel()
            lyricsSyncJob?.cancel()
            _lyricsLoading.value = false
            _activeSubtitle.value = null
            _currentLyricLineIndex.value = -1
        } else if (!_lyricsText.value.isNullOrBlank()) {
            startLyricsSyncLoop()
        } else {
            _currentTrack.value?.let(::fetchAndSyncLyrics)
        }
    }

    fun fetchAndSyncLyrics(track: AudioItem) {
        lyricsFetchJob?.cancel()
        lyricsSyncJob?.cancel()
        val generation = ++lyricsGeneration
        _activeSubtitle.value = null
        _lyricsText.value = null
        _currentLyricLineIndex.value = -1
        lyricsLines = emptyList()
        _lyricsLoading.value = true
        _lyricsRetryable.value = false
        _lyricsMessage.value = "Searching online lyrics…"
        lyricsFetchJob = viewModelScope.launch {
            try {
                val result = lyricsRepository.find(track)
                // A cancelled/slow old lookup must never replace the newly playing song.
                if (generation != lyricsGeneration || _currentTrack.value?.uri != track.uri) return@launch
                _lyricsText.value = result.text
                lyricsLines = result.text?.let { com.example.lyrics.LyricsText.parse(it).timed }.orEmpty()
                _lyricsMessage.value = result.message.takeIf { it.isNotBlank() }
                _lyricsRetryable.value = result.retryable
                if (_lyricsEnabled.value && !result.text.isNullOrBlank()) startLyricsSyncLoop()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == lyricsGeneration) {
                    _lyricsMessage.value = "Could not load lyrics. Check your connection and retry."
                    _lyricsRetryable.value = true
                }
            } finally {
                if (generation == lyricsGeneration) _lyricsLoading.value = false
            }
        }
    }

    private fun startLyricsSyncLoop() {
        lyricsSyncJob?.cancel()
        if (lyricsLines.isEmpty()) {
            _activeSubtitle.value = _lyricsText.value?.let { com.example.lyrics.LyricsText.parse(it).plain.firstOrNull() }
            return
        }
        lyricsSyncJob = viewModelScope.launch {
            while (isActive && _lyricsEnabled.value) {
                val positionMs = _currentPositionMs.value
                val lineIndex = lyricsLines.indexOfLast { it.first <= positionMs }
                _currentLyricLineIndex.value = lineIndex
                _activeSubtitle.value = lyricsLines.getOrNull(lineIndex)?.second?.takeIf { it.isNotBlank() }
                delay(250)
            }
        }
    }

    fun clearLyrics() {
        lyricsGeneration++
        lyricsFetchJob?.cancel()
        lyricsSyncJob?.cancel()
        lyricsLines = emptyList()
        _lyricsLoading.value = false
        _lyricsMessage.value = null
        _lyricsRetryable.value = false
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
                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    applyMusicEqualizer(audioSessionId)
                }
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val track = playbackQueue.getOrNull(currentMediaItemIndex) ?: return
                    _currentTrack.value = track
                    _currentPositionMs.value = 0L
                    _durationMs.value = track.durationMs
                    clearLyrics()
                    if (_lyricsEnabled.value) fetchAndSyncLyrics(track)
                }

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

    private var playbackQueue: List<AudioItem> = emptyList()

    fun playTrack(track: AudioItem, queue: List<AudioItem> = audioList.value) {
        playbackQueue = com.example.player.PlaybackQueue.snapshot(queue) { it.uri.toString() }
            .takeIf { items -> items.any { it.uri == track.uri } } ?: listOf(track)
        _currentTrack.value = track
        _isFullScreenPlayerOpen.value = true
        clearLyrics()
        exoPlayer?.let { player ->
            player.stop()
            val items = playbackQueue.map { item ->
                MediaItem.Builder().setUri(item.uri).setMediaId(item.uri.toString())
                    .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(item.title).setArtist(item.artist).setAlbumTitle(item.album).build()).build()
            }
            player.setMediaItems(items, playbackQueue.indexOfFirst { it.uri == track.uri }, 0L)
            player.repeatMode = Player.REPEAT_MODE_OFF
            player.shuffleModeEnabled = false
            player.prepare()
            player.play()
            com.example.player.HiMediaSessionService.startPlayback(getApplication(), player)
        }
    }

    fun togglePlayPause() {
        exoPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
            } else {
                if (_currentTrack.value == null && audioList.value.isNotEmpty()) {
                    playTrack(audioList.value.first())
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
        exoPlayer?.let { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }
    }

    fun playPrevious() {
        exoPlayer?.let { if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem() }
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
        val bands = com.example.audio.MusicEqualizer.presets[preset] ?: return
        _equalizerPreset.value = preset
        _equalizerBands.value = bands
        persistMusicEqualizer()
        applyMusicEqualizer(exoPlayer?.audioSessionId ?: 0)
    }

    fun setEqualizerBand(index: Int, gain: Float) {
        if (index !in 0..4 || !gain.isFinite()) return
        _equalizerPreset.value = "Custom"
        _equalizerBands.value = _equalizerBands.value.mapIndexed { i, old -> if (i == index) gain.coerceIn(-12f, 12f) else old }
        persistMusicEqualizer()
        applyMusicEqualizer(exoPlayer?.audioSessionId ?: 0)
    }

    private fun persistMusicEqualizer() {
        musicPrefs.edit().apply {
            putString("eq_preset_v1", _equalizerPreset.value)
            _equalizerBands.value.forEachIndexed { i, value -> putFloat("eq_band_$i", value) }
        }.apply()
    }

    private fun applyMusicEqualizer(session: Int) {
        _equalizerStatus.value = if (musicEqualizer.apply(session, _equalizerBands.value)) {
            if (_equalizerBands.value.all { it == 0f }) "Flat · processing bypassed" else "Equalizer active on the current audio session"
        } else if (session <= 0) "Start playback to activate the equalizer."
        else "Equalizer unavailable for this audio output. Playback is unchanged; try another output."
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
        musicEqualizer.close()
        com.example.player.HiMediaSessionService.detachPlayer(exoPlayer)
        exoPlayer?.release()
        exoPlayer = null
    }
}
