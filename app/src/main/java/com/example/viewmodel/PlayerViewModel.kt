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
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

enum class ActiveSheet {
    NONE,
    AUDIO_SETTINGS,
    EQUALIZER,
    VIDEO_SETTINGS,
    SUBTITLE_SETTINGS,
    DECODER_TELEMETRY,
    SUBTITLE_CUSTOMIZATION,
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

    private val _showStartOverPrompt = MutableStateFlow(false)
    val showStartOverPrompt = _showStartOverPrompt.asStateFlow()

    /** Whether SDR playback may request a wide-gamut output surface. */
    val wideColorGamutEnabled = preferencesRepo.settingsFlow
        .map { it.enableWideColorGamut }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _activeSheet = MutableStateFlow(ActiveSheet.NONE)
    val activeSheet = _activeSheet.asStateFlow()

    // Video Zoom & Scale
    private val _videoScale = MutableStateFlow(1.0f)
    val videoScale = _videoScale.asStateFlow()

    // Subtitle customization and player-window brightness are app preferences,
    // not one-screen Compose state. Keeping them here lets a new player screen
    // start exactly where the user left it.
    private val _subtitleStyle = MutableStateFlow(SubtitleStyleConfig())
    val subtitleStyle = _subtitleStyle.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted = _isMuted.asStateFlow()
    private var muteSaveJob: Job? = null
    private val _playerBrightness = MutableStateFlow<Float?>(null)
    val playerBrightness = _playerBrightness.asStateFlow()

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
    private var scrubOriginMs = 0L

    private val _sleepTimerMinutesLeft = MutableStateFlow<Int?>(null)
    val sleepTimerMinutesLeft = _sleepTimerMinutesLeft.asStateFlow()

    private var sleepTimerJob: Job? = null
    private var progressSyncJob: Job? = null
    private var hudDismissJob: Job? = null
    private var pendingBrightnessPercent: Int? = null
    private var languageSaveJob: Job? = null
    private var captionSaveJob: Job? = null
    private var brightnessSaveJob: Job? = null
    private var volumeSaveJob: Job? = null
    // Settings and resume reads are asynchronous. Keep the job so navigation
    // can cancel it before a closed player screen is prepared again.
    private var playRequestJob: Job? = null

    init {
        engine.onVideoEnded = { playAdjacentVideo(1) }
        startProgressSync()

        // The app-level HDR setting previously only changed DataStore. Observe
        // it here so a change takes effect immediately while a video is open.
        viewModelScope.launch {
            preferencesRepo.settingsFlow
                .map { it.enableHdrEnhance }
                .distinctUntilChanged()
                .collect { enabled -> engine.setHdrEnhanceActive(enabled) }
        }

        // Keep every global player control in sync with DataStore. Per-video
        // state such as resume position and aspect ratio is intentionally kept
        // in Room, while these controls apply to every playback session.
        viewModelScope.launch {
            preferencesRepo.settingsFlow.collect { settings ->
                _subtitleStyle.value = SubtitleStyleConfig(
                    fontSizeSp = settings.subtitleTextSizeSp,
                    fontFamily = settings.subtitleFontFamily,
                    textColorHex = settings.subtitleTextColorArgb,
                    bgOpacity = settings.subtitleBackgroundOpacityPercent / 100f,
                    verticalOffsetDp = settings.subtitleVerticalOffsetDp,
                )
                applyRememberedBrightness(settings.playerBrightnessPercent)
                engine.setVolumeBoost(settings.volumeBoostPercent)
                engine.setPlaybackSpeed(settings.playbackSpeedPercent / 100f)
                engine.setAudioDelay(settings.audioDelayMs)
                engine.setSubtitleOffset(settings.subtitleOffsetMs)
                engine.setEqualizerPreset(settings.equalizerPreset)
                if (settings.equalizerPreset == "Custom") {
                    settings.equalizerBandsDb.forEachIndexed { index, gainDb ->
                        engine.setCustomEqualizerBand(index, gainDb)
                    }
                }
            }
        }
    }

    private var playbackQueue: List<VideoItem> = emptyList()
    private var pendingPlaybackQueue: List<VideoItem>? = null

    fun setPlaybackQueue(items: List<VideoItem>) {
        pendingPlaybackQueue = com.example.player.PlaybackQueue.snapshot(items) { it.uri.toString() }
    }

    fun playVideo(video: VideoItem, startPositionOverride: Long? = null,
                  asAudio: Boolean = false, continueQueue: Boolean = false) {
        saveCurrentProgress()
        if (_currentVideo.value != null) com.example.data.PlaybackLoudness.capture(getApplication())
        engine.pause()
        if (!continueQueue) {
            playbackQueue = pendingPlaybackQueue?.takeIf { items -> items.any { it.uri == video.uri } }
                ?: listOf(video)
        }
        pendingPlaybackQueue = null
        engine.setBackgroundPlay(asAudio)
        playRequestJob?.cancel()
        _currentVideo.value = video
        _showStartOverPrompt.value = false
        _screenOrientation.value = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        playRequestJob = viewModelScope.launch {
            // These are independent local reads. Fetch them together so player
            // preparation is not delayed by settings I/O followed by DAO I/O.
            brightnessSaveJob?.join()
            volumeSaveJob?.join()
            languageSaveJob?.join()
            captionSaveJob?.join()
            muteSaveJob?.join()
            val settingsDeferred = async { preferencesRepo.settingsFlow.first() }
            val recordDeferred = async { videoDao.getVideoRecord(video.uri.toString()) }
            val settings = settingsDeferred.await()
            engine.applyConfiguration(
                hwDecoding = settings.hardwareDecoding,
                remuxUltraBuffer = settings.remuxUltraBufferMode,
                tunneling = settings.enableTunneling
            )
            restorePlayerControls(settings)
            // applyConfiguration creates a fresh ExoPlayer, so reapply the
            // persisted picture mode to the replacement instance.
            engine.setHdrEnhanceActive(settings.enableHdrEnhance)

            val record = recordDeferred.await()
            val startPos = startPositionOverride ?: record?.lastPositionMs ?: 0L
            _showStartOverPrompt.value = !engine.isBackgroundPlayActive.value && startPositionOverride == null && startPos >= 10_000L

            // Restore the saved ratio before preparing the new item. Previously
            // this happened after prepareMedia, so a quick tap on resize could
            // be overwritten by the Continue Watching restore callback.
            val savedAspect = record?.customAspectRatio?.let {
                runCatching { AspectRatioMode.valueOf(it) }.getOrNull()
            } ?: runCatching { AspectRatioMode.valueOf(settings.defaultAspectRatio) }.getOrDefault(AspectRatioMode.FIT)
            engine.setAspectRatioMode(savedAspect)

            engine.prepareMedia(
                uri = video.uri,
                startPositionMs = startPos,
                mediaMimeType = video.mimeType,
                mediaTitle = video.title,
            )
            engine.getPlayer().let { player ->
                HiMediaSessionService.startPlayback(getApplication(), player,
                    onNext = { playAdjacentVideo(1) }, onPrevious = { playAdjacentVideo(-1) })
            }

        }
    }

    /**
     * Leaves the player without allowing a late settings/record read to prepare
     * and start audio after the UI has returned to the library. PiP and explicit
     * Background Play deliberately bypass this method.
     */
    fun stopForNavigation() {
        playRequestJob?.cancel()
        playRequestJob = null
        _showStartOverPrompt.value = false
        closeSheet()
        engine.stopForNavigation()
        // The library has already persisted the resume point before this path
        // is called. Clearing the selected item prevents a disposed player
        // surface from retaining its title, controls, or growing-download HUD
        // while the main screen is recomposed.
        _currentVideo.value = null
    }

    /** Keeps a selected stream intact when the app is temporarily backgrounded. */
    fun pauseForAppBackground() {
        playRequestJob?.cancel()
        playRequestJob = null
        engine.pauseForAppBackground()
    }

    /** Resumes only a stream that was actively paused by the app lifecycle. */
    fun resumeAfterAppForeground() {
        engine.resumeAfterAppForeground()
    }

    fun playCurrentVideoFromStart() {
        _showStartOverPrompt.value = false
        _currentVideo.value?.let { playVideo(it, startPositionOverride = 0L, continueQueue = true) }
    }

    fun dismissStartOverPrompt() {
        _showStartOverPrompt.value = false
    }

    fun playAdjacentVideo(step: Int) {
        val current = _currentVideo.value ?: return
        val next = com.example.player.PlaybackQueue.adjacent(current, playbackQueue, step) { it.uri.toString() }
            ?: return
        playVideo(next, startPositionOverride = 0L, asAudio = engine.isBackgroundPlayActive.value, continueQueue = true)
    }

    fun playAsAudio() {
        closeSheet()
        _showStartOverPrompt.value = false
        engine.setBackgroundPlay(true)
        // Keep the existing position and decoder; this is not a new navigation.
        if (playRequestJob?.isActive == true) return
        engine.getPlayer().let { player ->
            player.play()
            HiMediaSessionService.startPlayback(getApplication(), player,
                onNext = { playAdjacentVideo(1) }, onPrevious = { playAdjacentVideo(-1) })
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

    fun setVideoScale(scale: Float) {
        _videoScale.value = scale.coerceIn(0.75f, 3.0f)
    }

    fun resetZoom() {
        _videoScale.value = 1.0f
    }

    // Subtitle Customization
    fun updateSubtitleStyle(config: SubtitleStyleConfig) {
        val normalized = config.copy(
            fontSizeSp = config.fontSizeSp.coerceIn(12, 48),
            fontFamily = config.fontFamily.ifBlank { "SansSerif" },
            bgOpacity = config.bgOpacity.coerceIn(0f, 1f),
            verticalOffsetDp = config.verticalOffsetDp.coerceIn(0, 240),
        )
        _subtitleStyle.value = normalized
        viewModelScope.launch {
            preferencesRepo.setSubtitleStyle(
                fontSizeSp = normalized.fontSizeSp,
                fontFamily = normalized.fontFamily,
                textColorArgb = normalized.textColorHex,
                backgroundOpacityPercent = (normalized.bgOpacity * 100f).roundToInt(),
                verticalOffsetDp = normalized.verticalOffsetDp,
            )
        }
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
                    android.widget.Toast.makeText(context, com.example.ui.feedback.UserFacingErrors.report("subtitle_save", e, "Couldn't save subtitles. Check storage access and free space, then try again."), android.widget.Toast.LENGTH_LONG).show()
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
        viewModelScope.launch { preferencesRepo.setDefaultAspectRatio(mode.name) }
        // Aspect-ratio selection is a screen-fit operation. Do not carry a
        // previous manual zoom into Fill, otherwise the surface can remain
        // oversized while the transport row stays correctly anchored.
        if (mode == AspectRatioMode.FILL_CROP || mode == AspectRatioMode.FIT) {
            _videoScale.value = 1.0f
        }
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
        val modes = AspectRatioMode.entries
        setAspectRatio(modes[(engine.aspectRatioMode.value.ordinal + 1) % modes.size])
    }

    fun toggleMute() {
        val muted = !_isMuted.value
        _isMuted.value = muted
        engine.setMuted(muted)
        muteSaveJob?.cancel()
        muteSaveJob = viewModelScope.launch { preferencesRepo.setPlayerMuted(muted) }
    }

    fun setPlaybackSpeed(speed: Float) {
        val normalized = speed.coerceIn(0.25f, 4f)
        engine.setPlaybackSpeed(normalized)
        viewModelScope.launch {
            preferencesRepo.setPlaybackSpeedPercent((normalized * 100f).roundToInt())
        }
    }

    fun setVolumeBoost(percent: Int) {
        val normalized = percent.coerceIn(0, 100)
        engine.setVolumeBoost(normalized)
        viewModelScope.launch {
            preferencesRepo.setVolumeBoostPercent(normalized)
        }
    }

    fun setEqualizerPreset(preset: String) {
        engine.setEqualizerPreset(preset)
        viewModelScope.launch {
            preferencesRepo.setEqualizer(engine.equalizerPreset.value, engine.equalizerBandsDb.value)
        }
    }

    fun setCustomEqualizerBand(index: Int, gainDb: Float) {
        engine.setCustomEqualizerBand(index, gainDb)
        viewModelScope.launch {
            preferencesRepo.setEqualizer(engine.equalizerPreset.value, engine.equalizerBandsDb.value)
        }
    }

    fun selectAudioTrack(track: VideoTrackInfo) {
        if (!track.isSupported) return
        engine.selectAudioTrack(track)
        languageSaveJob?.cancel()
        languageSaveJob = viewModelScope.launch { preferencesRepo.setPreferredAudioLanguage(track.language) }
    }

    fun selectSubtitleTrack(track: VideoTrackInfo?) {
        if (track != null && !track.isSupported) return
        engine.selectSubtitleTrack(track)
        captionSaveJob?.cancel()
        captionSaveJob = viewModelScope.launch { preferencesRepo.setPreferredSubtitleTrack(track?.language, track != null) }
    }

    fun refreshCaptionTracks() {
        engine.refreshAvailableTracks()
    }

    fun setAudioDelay(ms: Long) {
        val normalized = ms.coerceIn(-10_000L, 10_000L)
        engine.setAudioDelay(normalized)
        viewModelScope.launch {
            preferencesRepo.setAudioDelayMs(normalized)
        }
    }

    fun setSubtitleOffset(ms: Long) {
        val normalized = ms.coerceIn(-10_000L, 10_000L)
        engine.setSubtitleOffset(normalized)
        viewModelScope.launch {
            preferencesRepo.setSubtitleOffsetMs(normalized)
        }
    }

    val hdrEnhanceActive = engine.hdrEnhanceActive

    fun toggleHdrEnhance() {
        val enabled = !engine.hdrEnhanceActive.value
        engine.setHdrEnhanceActive(enabled)
        // Keep the in-player HDR control and the app Settings switch in sync.
        // A later player recreation must not silently revert the user's choice.
        viewModelScope.launch {
            preferencesRepo.setHdrEnhance(enabled)
        }
    }

    fun disableHdrEnhance() {
        engine.setHdrEnhanceActive(false)
        viewModelScope.launch {
            preferencesRepo.setHdrEnhance(false)
        }
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
        val current = when {
            windowBrightness >= 0f -> windowBrightness
            _playerBrightness.value != null -> _playerBrightness.value!!
            else -> 0.5f
        }
        val newLevel = (current + delta).coerceIn(0.01f, 1.0f)
        _brightnessLevel.value = newLevel
        persistPlayerBrightness(newLevel)
        scheduleHudDismiss()
        return newLevel
    }

    fun onBrightnessGestureLevel(level: Float): Float {
        val newLevel = level.coerceIn(0.01f, 1.0f)
        _brightnessLevel.value = newLevel
        persistPlayerBrightness(newLevel)
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
            persistVolumeBoost(newBoost)
            _volumeLevel.value = 1.0f + (newBoost / 100f)
        } else if (delta < 0 && engine.volumeBoostPercent.value > 0) {
            // Decrease boost first
            val currentBoost = engine.volumeBoostPercent.value
            val newBoost = (currentBoost + (delta * 100).toInt()).coerceIn(0, 100)
            engine.setVolumeBoost(newBoost)
            persistVolumeBoost(newBoost)
            _volumeLevel.value = 1.0f + (newBoost / 100f)
        } else {
            // Adjust system volume
            val step = (delta * maxVolume).toInt()
            val target = (currentVolume + step).coerceIn(0, maxVolume)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
            persistPlayerVolume(target, maxVolume)
            _volumeLevel.value = target.toFloat() / maxVolume.toFloat()
        }
        scheduleHudDismiss()
    }

    /** Applies the complete gesture target without rereading a moving system level. */
    fun onVolumeGestureLevel(level: Float) {
        val target = level.coerceIn(0f, 2f)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val systemRatio = target.coerceAtMost(1f)
        val targetSystem = (systemRatio * maxVolume).roundToInt().coerceIn(0, maxVolume)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetSystem, 0)
        val boost = ((target - 1f).coerceAtLeast(0f) * 100f).roundToInt().coerceIn(0, 100)
        engine.setVolumeBoost(boost)
        persistPlayerVolume(targetSystem, maxVolume)
        persistVolumeBoost(boost)
        _volumeLevel.value = target
        scheduleHudDismiss()
    }

    private fun persistPlayerBrightness(level: Float) {
        _playerBrightness.value = level
        pendingBrightnessPercent = (level * 100f).roundToInt().coerceIn(1, 100)
        brightnessSaveJob?.cancel()
        brightnessSaveJob = viewModelScope.launch {
            preferencesRepo.setPlayerBrightnessPercent((level * 100f).roundToInt())
        }
    }

    private fun persistPlayerVolume(volume: Int, maxVolume: Int) {
        if (maxVolume <= 0) return
        val percent = (volume.toFloat() / maxVolume * 100f).roundToInt()
        com.example.data.PlaybackLoudness.remember(getApplication(), percent)
        volumeSaveJob?.cancel()
        volumeSaveJob = viewModelScope.launch {
            preferencesRepo.setPlayerVolumePercent(percent)
        }
    }

    private fun persistVolumeBoost(percent: Int) {
        viewModelScope.launch {
            preferencesRepo.setVolumeBoostPercent(percent)
        }
    }

    fun onScrubStart() {
        scrubOriginMs = engine.currentPositionMs.value
        _scrubTimeMs.value = scrubOriginMs
        _scrubDeltaMs.value = 0L
    }

    fun onScrubMove(deltaMs: Long) {
        val total = engine.durationMs.value
        val target = (scrubOriginMs + deltaMs).coerceIn(0L, total)
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

    private fun applyRememberedBrightness(savedPercent: Int) {
        val pending = pendingBrightnessPercent
        if (pending != null && savedPercent != pending) return
        if (pending == savedPercent) pendingBrightnessPercent = null
        _playerBrightness.value = savedPercent.takeIf { it >= 1 }?.div(100f)
    }

    private fun restorePlayerControls(settings: com.example.data.AppPlayerSettings) {
        _subtitleStyle.value = SubtitleStyleConfig(
            fontSizeSp = settings.subtitleTextSizeSp,
            fontFamily = settings.subtitleFontFamily,
            textColorHex = settings.subtitleTextColorArgb,
            bgOpacity = settings.subtitleBackgroundOpacityPercent / 100f,
            verticalOffsetDp = settings.subtitleVerticalOffsetDp,
        )
        applyRememberedBrightness(settings.playerBrightnessPercent)
        engine.setRememberedTrackPreferences(settings.preferredAudioLanguage, settings.preferredSubtitleLanguage, settings.subtitlesEnabled)
        _isMuted.value = settings.playerMuted
        engine.setMuted(settings.playerMuted)

        engine.setVolumeBoost(settings.volumeBoostPercent)
        engine.setPlaybackSpeed(settings.playbackSpeedPercent / 100f)
        engine.setAudioDelay(settings.audioDelayMs)
        engine.setSubtitleOffset(settings.subtitleOffsetMs)
        engine.setEqualizerPreset(settings.equalizerPreset)
        if (settings.equalizerPreset == "Custom") {
            settings.equalizerBandsDb.forEachIndexed { index, gainDb ->
                engine.setCustomEqualizerBand(index, gainDb)
            }
        }

        // This is deliberately scoped to playback start. The app never adjusts
        // the device's media volume merely because a user opens Hi Player.
        com.example.data.PlaybackLoudness.restoreForPlayback(getApplication(), settings.playerVolumePercent)
    }

    private var lastProgressWriteMs = 0L
    private fun startProgressSync() {
        progressSyncJob?.cancel()
        progressSyncJob = viewModelScope.launch {
            while (isActive) {
                delay(500)
                if (engine.isPlaying.value) {
                    com.example.data.PlaybackLoudness.capture(getApplication())
                    if (System.currentTimeMillis() - lastProgressWriteMs >= 5_000L) {
                        lastProgressWriteMs = System.currentTimeMillis()
                        saveCurrentProgress()
                    }
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
        playRequestJob?.cancel()
        saveCurrentProgress()
        engine.release()
        super.onCleared()
    }

}
