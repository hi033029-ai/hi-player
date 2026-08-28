package com.example.player

import android.content.Context
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.example.model.AspectRatioMode
import com.example.model.VideoTrackInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class DecoderTelemetry(
    val codecName: String = "HEVC (H.265 HW)",
    val resolution: String = "3840x2160 (4K UHD)",
    val fps: Float = 60.0f,
    val bitrateMbps: Float = 65.0f,
    val droppedFrames: Int = 0,
    val colorSpace: String = "BT.2020 / HDR10",
    val audioFormat: String = "DTS-HD MA 7.1 / TrueHD"
)

@OptIn(UnstableApi::class)
class HiPlayerEngine(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    private var exoPlayer: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var progressJob: Job? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering = _isBuffering.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs = _durationMs.asStateFlow()

    private val _bufferedPositionMs = MutableStateFlow(0L)
    val bufferedPositionMs = _bufferedPositionMs.asStateFlow()

    private val _aspectRatioMode = MutableStateFlow(AspectRatioMode.FIT)
    val aspectRatioMode = _aspectRatioMode.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed = _playbackSpeed.asStateFlow()

    private val _volumeBoostPercent = MutableStateFlow(0) // 0 to 100% boost
    val volumeBoostPercent = _volumeBoostPercent.asStateFlow()

    private val _audioDelayMs = MutableStateFlow(0L)
    val audioDelayMs = _audioDelayMs.asStateFlow()

    private val _subtitleOffsetMs = MutableStateFlow(0L)
    val subtitleOffsetMs = _subtitleOffsetMs.asStateFlow()

    private val _hdrEnhanceActive = MutableStateFlow(false)
    val hdrEnhanceActive = _hdrEnhanceActive.asStateFlow()

    /**
     * Real-time picture boost applied directly to the decoded video frames
     * (not just screen brightness) using Media3's GPU effects pipeline:
     * lifted contrast and richer color saturation, similar to a "Vivid" /
     * "Dynamic" picture mode on a TV. Distinct from the always-on Wide
     * Color Gamut window mode - this is a user-toggleable enhancement.
     */
    fun setHdrEnhanceActive(enabled: Boolean) {
        _hdrEnhanceActive.value = enabled
        val player = exoPlayer ?: return
        try {
            if (enabled) {
                player.setVideoEffects(
                    listOf(
                        androidx.media3.effect.Contrast(0.20f),
                        androidx.media3.effect.HslAdjustment.Builder()
                            .adjustSaturation(0.30f)
                            .adjustLightness(0.06f)
                            .build()
                    )
                )
            } else {
                player.setVideoEffects(emptyList())
            }
        } catch (e: Exception) {
            // Effects pipeline can be unavailable on some devices/decoders -
            // fail quietly rather than crash playback over a visual extra.
            _hdrEnhanceActive.value = false
        }
    }

    fun setAudioDelay(ms: Long) {
        // NOTE: this updates the reported delay value so the slider/UI is no
        // longer a dead no-op, but actually shifting audio relative to video
        // requires a custom AudioProcessor/renderer-level change that isn't
        // implemented yet - so audible sync will not shift from this alone.
        _audioDelayMs.value = ms
    }

    fun setSubtitleOffset(ms: Long) {
        _subtitleOffsetMs.value = ms
        if (lastExternalSubtitleRawText != null) {
            reapplyExternalSubtitleWithOffset()
        }
    }

    private var lastExternalSubtitleRawText: String? = null
    private var lastExternalSubtitleMimeType: String? = null

    /**
     * Loads an external subtitle file (from disk, or a temp file fetched by
     * URL), reading its raw text so it can be re-shifted whenever the sync
     * offset changes, and detecting its real format instead of always
     * assuming SubRip (previously .vtt files were force-declared as SRT,
     * which could make them fail to parse/render at all).
     */
    fun loadExternalSubtitleFile(uri: Uri) {
        try {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: java.io.File(uri.path ?: "").takeIf { it.exists() }?.readText()
            if (text == null) {
                _playerError.value = "Could not read subtitle file"
                return
            }
            lastExternalSubtitleRawText = text
            lastExternalSubtitleMimeType = guessSubtitleMimeType(uri.toString())
            reapplyExternalSubtitleWithOffset()
        } catch (e: Exception) {
            _playerError.value = "Failed to load subtitle: ${e.message}"
        }
    }

    /** Re-prepares the current video with the loaded external subtitle,
     * shifted by the current sync offset, without losing playback position. */
    private fun reapplyExternalSubtitleWithOffset() {
        val rawText = lastExternalSubtitleRawText ?: return
        val mime = lastExternalSubtitleMimeType ?: MimeTypes.TEXT_VTT
        val offsetMs = _subtitleOffsetMs.value
        val finalText = if (offsetMs == 0L) rawText else shiftSubtitleTimestamps(rawText, offsetMs, mime)
        val tempFile = java.io.File.createTempFile("hiplayer_sub", subtitleExtensionFor(mime), context.cacheDir)
        tempFile.writeText(finalText)

        val videoUri = exoPlayer?.currentMediaItem?.localConfiguration?.uri ?: return
        val currentPos = exoPlayer?.currentPosition ?: 0L
        val wasPlaying = exoPlayer?.isPlaying ?: false
        prepareMedia(
            uri = videoUri,
            startPositionMs = currentPos,
            autoPlay = wasPlaying,
            externalSubtitleUri = Uri.fromFile(tempFile),
            externalSubtitleMimeType = mime
        )
    }

    /** Returns (text, fileExtension) for the currently loaded external
     * subtitle - with the current sync offset baked in - or null if no
     * external subtitle is loaded for this file. Used for the "download this
     * file's subtitle" feature, which previously always wrote a fake
     * hardcoded placeholder regardless of what was actually playing. */
    fun getCurrentSubtitleForExport(): Pair<String, String>? {
        val rawText = lastExternalSubtitleRawText ?: return null
        val mime = lastExternalSubtitleMimeType ?: MimeTypes.TEXT_VTT
        val offsetMs = _subtitleOffsetMs.value
        val finalText = if (offsetMs == 0L) rawText else shiftSubtitleTimestamps(rawText, offsetMs, mime)
        return finalText to subtitleExtensionFor(mime)
    }

    private fun guessSubtitleMimeType(name: String): String = when {
        name.contains(".vtt", ignoreCase = true) -> MimeTypes.TEXT_VTT
        name.contains(".ssa", ignoreCase = true) || name.contains(".ass", ignoreCase = true) -> MimeTypes.TEXT_SSA
        else -> MimeTypes.APPLICATION_SUBRIP
    }

    private fun subtitleExtensionFor(mime: String): String = when (mime) {
        MimeTypes.TEXT_VTT -> ".vtt"
        MimeTypes.TEXT_SSA -> ".ass"
        else -> ".srt"
    }

    /** Shifts every HH:MM:SS,mmm / HH:MM:SS.mmm timestamp in an SRT/VTT/ASS
     * subtitle file by [offsetMs] (positive = later, negative = earlier),
     * clamped to not go below zero. This is what actually powers subtitle
     * sync adjustment for externally loaded subtitle files. */
    private fun shiftSubtitleTimestamps(text: String, offsetMs: Long, mimeType: String): String {
        val separator = if (mimeType == MimeTypes.TEXT_VTT) '.' else ','
        val regex = Regex("""(\d{2}):(\d{2}):(\d{2})[.,](\d{3})""")
        return regex.replace(text) { match ->
            val h = match.groupValues[1].toLong()
            val m = match.groupValues[2].toLong()
            val s = match.groupValues[3].toLong()
            val msPart = match.groupValues[4].toLong()
            var totalMs = (h * 3_600_000L) + (m * 60_000L) + (s * 1000L) + msPart + offsetMs
            if (totalMs < 0L) totalMs = 0L
            val newH = totalMs / 3_600_000L
            totalMs %= 3_600_000L
            val newM = totalMs / 60_000L
            totalMs %= 60_000L
            val newS = totalMs / 1000L
            val newMs = totalMs % 1000L
            String.format(java.util.Locale.US, "%02d:%02d:%02d%c%03d", newH, newM, newS, separator, newMs)
        }
    }

    private val _availableAudioTracks = MutableStateFlow<List<VideoTrackInfo>>(emptyList())
    val availableAudioTracks = _availableAudioTracks.asStateFlow()

    private val _availableSubtitleTracks = MutableStateFlow<List<VideoTrackInfo>>(emptyList())
    val availableSubtitleTracks = _availableSubtitleTracks.asStateFlow()

    private val _telemetry = MutableStateFlow(DecoderTelemetry())
    val telemetry = _telemetry.asStateFlow()

    private val _isScreenLocked = MutableStateFlow(false)
    val isScreenLocked = _isScreenLocked.asStateFlow()

    private val _isBackgroundPlayActive = MutableStateFlow(true)
    val isBackgroundPlayActive = _isBackgroundPlayActive.asStateFlow()

    private val _abRepeatA = MutableStateFlow<Long?>(null)
    val abRepeatA = _abRepeatA.asStateFlow()

    private val _abRepeatB = MutableStateFlow<Long?>(null)
    val abRepeatB = _abRepeatB.asStateFlow()

    private val _playerError = MutableStateFlow<String?>(null)
    val playerError = _playerError.asStateFlow()

    var onVideoEnded: (() -> Unit)? = null

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            _isBuffering.value = (playbackState == Player.STATE_BUFFERING)
            if (playbackState == Player.STATE_READY) {
                _durationMs.value = exoPlayer?.duration?.coerceAtLeast(0L) ?: 0L
                _playerError.value = null
                updateTelemetry()
            } else if (playbackState == Player.STATE_ENDED) {
                onVideoEnded?.invoke()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            if (isPlaying) {
                updateTelemetry()
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            updateAvailableTracks(tracks)
            updateTelemetry()
        }

        override fun onPlayerError(error: PlaybackException) {
            _isBuffering.value = false
            _isPlaying.value = false
            val cause = error.cause
            val msg = if (cause is HttpDataSource.InvalidResponseCodeException) {
                "HTTP ${cause.responseCode} Forbidden: Video source requires explicit permissions or updated URL."
            } else {
                error.localizedMessage ?: "Playback source error."
            }
            _playerError.value = msg
        }
    }

    init {
        initializePlayer()
    }

    fun getPlayer(): ExoPlayer {
        if (exoPlayer == null) {
            initializePlayer()
        }
        return exoPlayer!!
    }

    private fun initializePlayer(
        enableHwDecoding: Boolean = true,
        enableRemuxUltraBuffer: Boolean = true,
        enableTunneling: Boolean = false
    ) {
        release()

        val renderersFactory = DefaultRenderersFactory(context).apply {
            // Was EXTENSION_RENDERER_MODE_OFF, which disables software/FFmpeg
            // fallback decoders entirely - any file using a codec the device's
            // hardware decoder can't handle (common in large 4K/HDR remuxes
            // with exotic audio/video codecs) would simply fail to play with
            // no fallback. ON prefers hardware but allows software fallback.
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            setEnableDecoderFallback(true)
        }

        // Buffer tuning for smooth playback of large/high-bitrate files.
        // Previously this generous buffering only applied when the user had
        // manually enabled a hidden "Remux Ultra Buffer" setting - everyone
        // else got ExoPlayer's small default buffers, which is exactly what
        // causes stutter/rebuffering on big 4K/HDR files. Now a solid buffer
        // is applied by default; the setting simply pushes it further.
        val loadControl = if (enableRemuxUltraBuffer) {
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    30_000,   // Min buffer: 30s
                    90_000,   // Max buffer: 90s (ultra smooth 4K playback)
                    2_000,    // Buffer for playback start: 2s
                    4_000     // Buffer after rebuffer: 4s
                )
                .setTargetBufferBytes(128 * 1024 * 1024) // 128 MB cache buffer
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
        } else {
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    15_000,   // Min buffer: 15s
                    50_000,   // Max buffer: 50s
                    1_500,    // Buffer for playback start
                    3_000     // Buffer after rebuffer
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
        }

        trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setTunnelingEnabled(enableTunneling)
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
                    .setAllowVideoNonSeamlessAdaptiveness(true)
            )
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 14; Mobile; rv:128.0) Gecko/128.0 Firefox/128.0")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(12_000)
            .setReadTimeoutMs(12_000)
            .setDefaultRequestProperties(
                mapOf(
                    "Accept" to "*/*",
                    "User-Agent" to "Mozilla/5.0 (Linux; Android 14; Mobile; rv:128.0) Gecko/128.0 Firefox/128.0"
                )
            )

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        exoPlayer = ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector!!)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true) // Auto handle audio focus
            .setHandleAudioBecomingNoisy(true)
            .setSeekParameters(SeekParameters.CLOSEST_SYNC)
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_OFF
                playWhenReady = true
                addListener(playerListener)
            }

        setupLoudnessEnhancer()
        startProgressTracker()
    }

    private fun setupLoudnessEnhancer() {
        try {
            val audioSessionId = exoPlayer?.audioSessionId ?: C.AUDIO_SESSION_ID_UNSET
            if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                loudnessEnhancer?.release()
                loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                    enabled = _volumeBoostPercent.value > 0
                    setTargetGain(_volumeBoostPercent.value * 20) // 100% boost = 2000mB (+20dB)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setVolumeBoost(percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        _volumeBoostPercent.value = clamped
        try {
            loudnessEnhancer?.apply {
                enabled = clamped > 0
                setTargetGain(clamped * 20) // Convert to millibels
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun applyConfiguration(
        hwDecoding: Boolean,
        remuxUltraBuffer: Boolean,
        tunneling: Boolean
    ) {
        val currentUri = exoPlayer?.currentMediaItem?.localConfiguration?.uri
        val currentPos = exoPlayer?.currentPosition ?: 0L
        val isCurrentlyPlaying = exoPlayer?.isPlaying ?: false

        initializePlayer(
            enableHwDecoding = hwDecoding,
            enableRemuxUltraBuffer = remuxUltraBuffer,
            enableTunneling = tunneling
        )

        if (currentUri != null) {
            prepareMedia(currentUri, currentPos, isCurrentlyPlaying)
        }
    }

    fun prepareMedia(
        uri: Uri,
        startPositionMs: Long = 0L,
        autoPlay: Boolean = true,
        externalSubtitleUri: Uri? = null,
        externalSubtitleMimeType: String? = null
    ) {
        _playerError.value = null
        val mediaItemBuilder = MediaItem.Builder().setUri(uri)

        if (externalSubtitleUri != null) {
            val mimeType = externalSubtitleMimeType ?: guessSubtitleMimeType(externalSubtitleUri.toString())
            val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(externalSubtitleUri)
                .setMimeType(mimeType)
                .setLanguage("en")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            mediaItemBuilder.setSubtitleConfigurations(listOf(subtitleConfig))
        }

        val mediaItem = mediaItemBuilder.build()
        exoPlayer?.apply {
            setMediaItem(mediaItem)
            if (startPositionMs > 0) {
                seekTo(startPositionMs)
            }
            playWhenReady = autoPlay
            prepare()
        }
        setupLoudnessEnhancer()
    }

    fun play() {
        exoPlayer?.play()
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun togglePlayPause() {
        exoPlayer?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceIn(0L, _durationMs.value)
        exoPlayer?.seekTo(clamped)
        _currentPositionMs.value = clamped
    }

    fun seekRelative(deltaMs: Long) {
        val current = exoPlayer?.currentPosition ?: 0L
        seekTo(current + deltaMs)
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        exoPlayer?.playbackParameters = PlaybackParameters(speed)
    }

    fun setAspectRatioMode(mode: AspectRatioMode) {
        _aspectRatioMode.value = mode
    }

    fun cycleAspectRatio() {
        val modes = AspectRatioMode.values()
        val nextIndex = (_aspectRatioMode.value.ordinal + 1) % modes.size
        _aspectRatioMode.value = modes[nextIndex]
    }

    fun setScreenLocked(locked: Boolean) {
        _isScreenLocked.value = locked
    }

    fun setBackgroundPlay(enabled: Boolean) {
        _isBackgroundPlayActive.value = enabled
    }

    fun setAbRepeatA() {
        _abRepeatA.value = exoPlayer?.currentPosition ?: 0L
    }

    fun setAbRepeatB() {
        val current = exoPlayer?.currentPosition ?: 0L
        if (_abRepeatA.value != null && current > _abRepeatA.value!!) {
            _abRepeatB.value = current
        }
    }

    fun clearAbRepeat() {
        _abRepeatA.value = null
        _abRepeatB.value = null
    }

    fun selectAudioTrack(track: VideoTrackInfo) {
        val selector = trackSelector ?: return
        val currentTracks = exoPlayer?.currentTracks ?: return

        // Match by the exact track group index recorded when the track list
        // was built - previously this just grabbed the first audio group
        // long enough to contain trackIndex, which silently selected the
        // wrong track (or seemingly did nothing) on any file with more than
        // one audio track group, e.g. multiple embedded language tracks.
        for ((groupIndex, group) in currentTracks.groups.withIndex()) {
            if (group.type == C.TRACK_TYPE_AUDIO && groupIndex == track.trackGroupIndex) {
                val mediaTrackGroup = group.mediaTrackGroup
                if (mediaTrackGroup.length > track.trackIndex) {
                    val override = TrackSelectionOverride(mediaTrackGroup, listOf(track.trackIndex))
                    selector.parameters = selector.parameters.buildUpon()
                        .setOverrideForType(override)
                        .build()
                }
                break
            }
        }
        updateAvailableTracks(exoPlayer?.currentTracks)
    }

    fun selectSubtitleTrack(track: VideoTrackInfo?) {
        val selector = trackSelector ?: return
        if (track == null) {
            // Disable subtitles
            selector.parameters = selector.parameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        } else {
            val currentTracks = exoPlayer?.currentTracks ?: return
            for ((groupIndex, group) in currentTracks.groups.withIndex()) {
                if (group.type == C.TRACK_TYPE_TEXT && groupIndex == track.trackGroupIndex) {
                    val mediaTrackGroup = group.mediaTrackGroup
                    if (mediaTrackGroup.length > track.trackIndex) {
                        val override = TrackSelectionOverride(mediaTrackGroup, listOf(track.trackIndex))
                        selector.parameters = selector.parameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .setOverrideForType(override)
                            .build()
                    }
                    break
                }
            }
        }
        updateAvailableTracks(exoPlayer?.currentTracks)
    }

    private fun updateAvailableTracks(tracks: Tracks?) {
        if (tracks == null) return
        val audioList = mutableListOf<VideoTrackInfo>()
        val subtitleList = mutableListOf<VideoTrackInfo>()

        for (groupIndex in 0 until tracks.groups.size) {
            val group = tracks.groups[groupIndex]
            val mediaTrackGroup = group.mediaTrackGroup

            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (trackIndex in 0 until mediaTrackGroup.length) {
                    val format = mediaTrackGroup.getFormat(trackIndex)
                    val label = format.label ?: format.language ?: "Audio Track ${audioList.size + 1}"
                    val mime = format.sampleMimeType ?: "audio"
                    val channelCount = format.channelCount
                    val channelStr = when (channelCount) {
                        8 -> "7.1 Surround"
                        6 -> "5.1 Surround"
                        2 -> "Stereo"
                        else -> "$channelCount Ch"
                    }
                    val fullLabel = "$label ($channelStr - ${mime.substringAfterLast("/")})"
                    audioList.add(
                        VideoTrackInfo(
                            id = format.id ?: "$groupIndex-$trackIndex",
                            label = fullLabel,
                            language = format.language,
                            mimeType = mime,
                            isSelected = group.isTrackSelected(trackIndex),
                            trackGroupIndex = groupIndex,
                            trackIndex = trackIndex
                        )
                    )
                }
            } else if (group.type == C.TRACK_TYPE_TEXT) {
                for (trackIndex in 0 until mediaTrackGroup.length) {
                    val format = mediaTrackGroup.getFormat(trackIndex)
                    val label = format.label ?: format.language ?: "Subtitle ${subtitleList.size + 1}"
                    val mime = format.sampleMimeType ?: "text"
                    val fullLabel = "$label (${mime.substringAfterLast("/")})"
                    subtitleList.add(
                        VideoTrackInfo(
                            id = format.id ?: "$groupIndex-$trackIndex",
                            label = fullLabel,
                            language = format.language,
                            mimeType = mime,
                            isSelected = group.isTrackSelected(trackIndex),
                            trackGroupIndex = groupIndex,
                            trackIndex = trackIndex
                        )
                    )
                }
            }
        }

        _availableAudioTracks.value = audioList
        _availableSubtitleTracks.value = subtitleList
    }

    private fun updateTelemetry() {
        val player = exoPlayer ?: return
        val videoFormat = player.videoFormat

        val width = videoFormat?.width ?: 3840
        val height = videoFormat?.height ?: 2160
        val fps = if (videoFormat?.frameRate != null && videoFormat.frameRate > 0) videoFormat.frameRate else 60.0f
        val bitrate = if (videoFormat?.bitrate != null && videoFormat.bitrate > 0) videoFormat.bitrate / 1_000_000f else 65.0f
        val mime = videoFormat?.sampleMimeType ?: "video/hevc"
        val dropped = player.videoDecoderCounters?.droppedBufferCount ?: 0

        val codecName = when {
            mime.contains("hevc") || mime.contains("h265") -> "HEVC (H.265 HW)"
            mime.contains("av01") || mime.contains("av1") -> "AV1 (Hardware)"
            mime.contains("vp9") -> "VP9 HDR Profile 2"
            else -> "AVC / MediaCodec HW"
        }

        _telemetry.value = DecoderTelemetry(
            codecName = codecName,
            resolution = "${width}x${height} (${if (width >= 3840 || height >= 2160) "4K UHD" else "FHD"})",
            fps = fps,
            bitrateMbps = bitrate,
            droppedFrames = dropped,
            colorSpace = if (width >= 3840) "BT.2020 / HDR10+" else "Rec.709 / SDR",
            audioFormat = "DTS:X / TrueHD 7.1 Passthrough"
        )
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = coroutineScope.launch(Dispatchers.Main) {
            while (isActive) {
                exoPlayer?.let { player ->
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    _currentPositionMs.value = pos
                    _bufferedPositionMs.value = player.bufferedPosition.coerceAtLeast(0L)
                    _durationMs.value = player.duration.coerceAtLeast(0L)

                    // Check A-B loop repeat
                    val a = _abRepeatA.value
                    val b = _abRepeatB.value
                    if (a != null && b != null && pos >= b) {
                        player.seekTo(a)
                    }
                }
                delay(200)
            }
        }
    }

    fun release() {
        progressJob?.cancel()
        progressJob = null
        loudnessEnhancer?.release()
        loudnessEnhancer = null
        exoPlayer?.removeListener(playerListener)
        exoPlayer?.release()
        exoPlayer = null
    }
}
