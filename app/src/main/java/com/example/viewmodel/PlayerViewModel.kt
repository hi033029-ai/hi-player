package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.PlayerPreferencesRepository
import com.example.db.HiPlayerDatabase
import com.example.db.VideoEntity
import com.example.model.AspectRatioMode
import com.example.model.VideoItem
import com.example.model.VideoTrackInfo
import com.example.player.HiMediaSessionService
import com.example.player.HiPlayerEngine
import com.example.util.MediaRating
import com.example.util.TmdbRatingHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class ActiveSheet {
    NONE,
    AUDIO_SETTINGS,
    VIDEO_SETTINGS,
    SUBTITLE_SETTINGS,
    DECODER_TELEMETRY,
    PLAYLIST_CHOOSER,
    SUBTITLE_CUSTOMIZATION,
    TMDB_KEY_DIALOG,
    FETCH_SUBTITLE_URL_DIALOG
}

data class SubtitleStyleConfig(
    val fontSizeSp: Int = 20,
    val fontFamily: String = "SansSerif",
    val textColorHex: Long = 0xFFFFFFFF,
    val bgOpacity: Float = 0.6f,
    val verticalOffsetDp: Int = 24
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val database = HiPlayerDatabase.getInstance(application)
    private val videoDao = database.videoDao()
    private val preferencesRepo = PlayerPreferencesRepository(application)
    private val audioManager = application.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    val engine = HiPlayerEngine(application, viewModelScope)

    private val _currentVideo = MutableStateFlow<VideoItem?>(null)
    val currentVideo = _currentVideo.asStateFlow()

    private val _currentRating = MutableStateFlow<MediaRating?>(null)
    val currentRating = _currentRating.asStateFlow()
    private var ratingFetchJob: Job? = null

    val tmdbApiKey = preferencesRepo.settingsFlow
        .map { it.tmdbApiKey }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val _activeSheet = MutableStateFlow(ActiveSheet.NONE)
    val activeSheet = _activeSheet.asStateFlow()

    // Video Zoom & Scale
    private val _videoScale = MutableStateFlow(1.0f)
    val videoScale = _videoScale.asStateFlow()

    // Subtitle Customization
    private val _subtitleStyle = MutableStateFlow(SubtitleStyleConfig())
    val subtitleStyle = _subtitleStyle.asStateFlow()

    // Orientation
    private val _screenOrientation = MutableStateFlow(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR)
    val screenOrientation = _screenOrientation.asStateFlow()

    // HUD overlays
    private val _brightnessLevel = MutableStateFlow<Float?>(null) // 0.0f to 1.0f or null (hidden)
    val brightnessLevel = _brightnessLevel.asStateFlow()

    private val _volumeLevel = MutableStateFlow<Float?>(null) // 0.0f to 2.0f or null (hidden)
    val volumeLevel = _volumeLevel.asStateFlow()

    private val _scrubTimeMs = MutableStateFlow<Long?>(null) // null if not scrubbing
    val scrubTimeMs = _scrubTimeMs.asStateFlow()

    private val _scrubDeltaMs = MutableStateFlow<Long>(0L)
    val scrubDeltaMs = _scrubDeltaMs.asStateFlow()

    private val _sleepTimerMinutesLeft = MutableStateFlow<Int?>(null)
    val sleepTimerMinutesLeft = _sleepTimerMinutesLeft.asStateFlow()

    private var sleepTimerJob: Job? = null
    private var progressSyncJob: Job? = null
    private var hudDismissJob: Job? = null

    init {
        HiMediaSessionService.sharedPlayer = engine.getPlayer()
        startProgressSync()
    }

    fun playVideo(video: VideoItem, startPositionOverride: Long? = null) {
        _currentVideo.value = video
        _screenOrientation.value = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        fetchRatingFor(video)
        viewModelScope.launch {
            val settings = preferencesRepo.settingsFlow.first()
            engine.applyConfiguration(
                hwDecoding = settings.hardwareDecoding,
                remuxUltraBuffer = settings.remuxUltraBufferMode,
                tunneling = settings.enableTunneling
            )

            val record = videoDao.getVideoRecord(video.uri.toString())
            val startPos = startPositionOverride ?: record?.lastPositionMs ?: 0L

            engine.prepareMedia(video.uri, startPos)

            // Setup aspect ratio if saved
            record?.customAspectRatio?.let {
                try {
                    engine.setAspectRatioMode(AspectRatioMode.valueOf(it))
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    // Zoom Controls
    fun zoomIn() {
        val current = _videoScale.value
        if (current < 3.0f) {
            _videoScale.value = (current + 0.25f).coerceAtMost(3.0f)
        }
    }

    fun zoomOut() {
        val current = _videoScale.value
        if (current > 0.75f) {
            _videoScale.value = (current - 0.25f).coerceAtLeast(0.75f)
        }
    }

    fun resetZoom() {
        _videoScale.value = 1.0f
    }

    // Subtitle Customization
    fun updateSubtitleStyle(config: SubtitleStyleConfig) {
        _subtitleStyle.value = config
    }

    // Orientation Switcher
    fun cycleScreenOrientation() {
        _screenOrientation.value = when (_screenOrientation.value) {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR,
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }
    }

    // Fetch Subtitle from URL
    fun fetchSubtitleFromUrl(url: String) {
        if (url.isBlank()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                if (connection.responseCode == 200) {
                    val content = connection.inputStream.bufferedReader().use { it.readText() }
                    val tempFile = java.io.File.createTempFile("fetched_sub", ".vtt", getApplication<Application>().cacheDir)
                    tempFile.writeText(content)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        loadExternalSubtitle(Uri.fromFile(tempFile))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Download Current Subtitle - exports whatever subtitle is actually
    // loaded/active for this file (with the current sync offset applied),
    // instead of always writing a fake hardcoded placeholder file.
    fun downloadCurrentSubtitle(context: Context) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val video = _currentVideo.value ?: return@launch
                val exported = engine.getCurrentSubtitleForExport()
                if (exported == null) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            context,
                            "No subtitle is currently loaded for this file - load or fetch one first",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                    return@launch
                }
                val (text, extension) = exported
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val subFile = java.io.File(downloadsDir, "${video.title.substringBeforeLast(".")}_subtitles$extension")
                subFile.writeText(text)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Saved subtitle to Downloads/${subFile.name}", android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Couldn't save subtitle: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun togglePlayPause() {
        engine.togglePlayPause()
        saveCurrentProgress()
    }

    fun seekTo(positionMs: Long) {
        engine.seekTo(positionMs)
        saveCurrentProgress()
    }

    fun seekRelative(deltaMs: Long) {
        engine.seekRelative(deltaMs)
        saveCurrentProgress()
    }

    fun setAspectRatio(mode: AspectRatioMode) {
        engine.setAspectRatioMode(mode)
        viewModelScope.launch {
            _currentVideo.value?.let { video ->
                val record = videoDao.getVideoRecord(video.uri.toString())
                if (record != null) {
                    videoDao.insertOrUpdate(record.copy(customAspectRatio = mode.name))
                }
            }
        }
    }

    fun cycleAspectRatio() {
        engine.cycleAspectRatio()
    }

    fun setPlaybackSpeed(speed: Float) {
        engine.setPlaybackSpeed(speed)
    }

    fun setVolumeBoost(percent: Int) {
        engine.setVolumeBoost(percent)
    }

    fun selectAudioTrack(track: VideoTrackInfo) {
        engine.selectAudioTrack(track)
    }

    fun selectSubtitleTrack(track: VideoTrackInfo?) {
        engine.selectSubtitleTrack(track)
    }

    fun setAudioDelay(ms: Long) {
        engine.setAudioDelay(ms)
    }

    fun setSubtitleOffset(ms: Long) {
        engine.setSubtitleOffset(ms)
    }

    val hdrEnhanceActive = engine.hdrEnhanceActive

    fun toggleHdrEnhance() {
        engine.setHdrEnhanceActive(!engine.hdrEnhanceActive.value)
    }

    fun loadExternalSubtitle(uri: Uri) {
        engine.loadExternalSubtitleFile(uri)
    }

    fun openSheet(sheet: ActiveSheet) {
        _activeSheet.value = sheet
    }

    fun closeSheet() {
        _activeSheet.value = ActiveSheet.NONE
    }

    // Gesture Handlers
    fun onBrightnessGesture(delta: Float, windowBrightness: Float): Float {
        val current = if (windowBrightness < 0f) 0.5f else windowBrightness
        val newLevel = (current + delta).coerceIn(0.01f, 1.0f)
        _brightnessLevel.value = newLevel
        scheduleHudDismiss()
        return newLevel
    }

    fun onVolumeGesture(delta: Float) {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val currentRatio = currentVolume.toFloat() / maxVolume.toFloat()

        if (delta > 0 && currentRatio >= 1.0f) {
            // Adjust volume boost (100% to 200%)
            val currentBoost = engine.volumeBoostPercent.value
            val newBoost = (currentBoost + (delta * 100).toInt()).coerceIn(0, 100)
            engine.setVolumeBoost(newBoost)
            _volumeLevel.value = 1.0f + (newBoost / 100f)
        } else if (delta < 0 && engine.volumeBoostPercent.value > 0) {
            // Decrease boost first
            val currentBoost = engine.volumeBoostPercent.value
            val newBoost = (currentBoost + (delta * 100).toInt()).coerceIn(0, 100)
            engine.setVolumeBoost(newBoost)
            _volumeLevel.value = 1.0f + (newBoost / 100f)
        } else {
            // Adjust system volume
            val step = (delta * maxVolume).toInt()
            val target = (currentVolume + step).coerceIn(0, maxVolume)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
            _volumeLevel.value = target.toFloat() / maxVolume.toFloat()
        }
        scheduleHudDismiss()
    }

    fun onScrubStart() {
        _scrubTimeMs.value = engine.currentPositionMs.value
        _scrubDeltaMs.value = 0L
    }

    fun onScrubMove(deltaMs: Long) {
        val base = _scrubTimeMs.value ?: engine.currentPositionMs.value
        val total = engine.durationMs.value
        val target = (base + deltaMs).coerceIn(0L, total)
        _scrubDeltaMs.value = deltaMs
        _scrubTimeMs.value = target
    }

    fun onScrubEnd() {
        _scrubTimeMs.value?.let { target ->
            engine.seekTo(target)
            saveCurrentProgress()
        }
        _scrubTimeMs.value = null
        _scrubDeltaMs.value = 0L
    }

    private fun scheduleHudDismiss() {
        hudDismissJob?.cancel()
        hudDismissJob = viewModelScope.launch {
            delay(1500)
            _brightnessLevel.value = null
            _volumeLevel.value = null
        }
    }

    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        _sleepTimerMinutesLeft.value = minutes

        if (minutes != null && minutes > 0) {
            sleepTimerJob = viewModelScope.launch {
                var remaining = minutes
                while (remaining > 0 && isActive) {
                    delay(60_000)
                    remaining--
                    _sleepTimerMinutesLeft.value = remaining
                }
                engine.pause()
                _sleepTimerMinutesLeft.value = null
            }
        }
    }

    private fun startProgressSync() {
        progressSyncJob?.cancel()
        progressSyncJob = viewModelScope.launch {
            while (isActive) {
                delay(5000)
                if (engine.isPlaying.value) {
                    saveCurrentProgress()
                }
            }
        }
    }

    fun saveCurrentProgress() {
        val video = _currentVideo.value ?: return
        val pos = engine.currentPositionMs.value
        val dur = engine.durationMs.value
        if (pos > 0 || dur > 0) {
            viewModelScope.launch {
                videoDao.insertOrUpdate(
                    VideoEntity(
                        uriString = video.uri.toString(),
                        title = video.title,
                        lastPositionMs = pos,
                        durationMs = dur,
                        lastWatchedTimestamp = System.currentTimeMillis(),
                        customAspectRatio = engine.aspectRatioMode.value.name
                    )
                )
            }
        }
    }

    override fun onCleared() {
        saveCurrentProgress()
        engine.release()
        super.onCleared()
    }

    /** Cleans the video's filename and looks up a public TMDB rating for it. */
    private fun fetchRatingFor(video: VideoItem) {
        _currentRating.value = null
        ratingFetchJob?.cancel()
        ratingFetchJob = viewModelScope.launch {
            val apiKey = preferencesRepo.settingsFlow.first().tmdbApiKey
            if (apiKey.isBlank()) return@launch
            val parsed = TmdbRatingHelper.parseFilename(video.title)
            val rating = TmdbRatingHelper.fetchRating(apiKey, parsed.cleanTitle, parsed.year)
            if (isActive) {
                _currentRating.value = rating
            }
        }
    }

    fun setTmdbApiKey(key: String) {
        viewModelScope.launch {
            preferencesRepo.setTmdbApiKey(key)
            // Re-fetch immediately for the video currently playing so the
            // badge appears without needing to reopen the file.
            _currentVideo.value?.let { fetchRatingFor(it) }
        }
    }
}
