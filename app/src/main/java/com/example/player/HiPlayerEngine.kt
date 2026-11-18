package com.example.player

import android.content.Context
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
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
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.example.model.AspectRatioMode
import com.example.model.PendingDownloadFormat
import com.example.model.VideoTrackInfo
import com.example.playback.GrowingMkvExtractorsFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

data class DecoderTelemetry(
    val codecName: String = "Unknown decoder",
    val resolution: String = "Unknown",
    val fps: Float = 0f,
    val bitrateMbps: Float = 0f,
    val droppedFrames: Int = 0,
    val colorSpace: String = "Unknown",
    val audioFormat: String = "Unknown audio"
)

private fun displayTrackLanguage(language: String?, formatLabel: String?, fallback: String): String {
    val normalized = language?.trim()?.takeUnless { it.isEmpty() || it.equals("und", ignoreCase = true) }
    if (normalized != null) {
        val displayName = Locale.forLanguageTag(normalized).getDisplayLanguage(Locale.ENGLISH)
        if (displayName.isNotBlank()) return displayName.replaceFirstChar { it.uppercase(Locale.ENGLISH) }
    }
    return formatLabel
        ?.substringBefore("(")
        ?.substringBefore(" - ")
        ?.trim()
        ?.takeUnless { it.isNullOrBlank() }
        ?: fallback
}

internal object PlaybackBackendPolicy {
    fun shouldUseVlc(scheme: String?, displayName: String?, mimeType: String?, growing: Boolean = false, colorManaged: Boolean = false): Boolean {
        if (growing || colorManaged) return false
        if (scheme?.lowercase() !in setOf("file", "content")) return false
        val name = PendingDownloadFormat.originalName(displayName?.lowercase().orEmpty())
        return name.endsWith(".mkv") || name.endsWith(".mk3d") ||
            name.endsWith(".mka") || name.endsWith(".webm") ||
            mimeType?.contains("matroska", ignoreCase = true) == true
    }
}

@OptIn(UnstableApi::class)
class HiPlayerEngine(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    private companion object {
        const val GROWING_INITIAL_GRACE_MS = 120_000L
        const val BACK_BUFFER_MS = 15_000
        const val MAX_TARGET_BUFFER_BYTES = 192L * 1024 * 1024
        const val MIN_TARGET_BUFFER_BYTES = 32L * 1024 * 1024
    }

    /**
     * Media buffers live on the Java heap. A fixed 192 MB target could exceed
     * what the heap allows on many phones and crash playback with an
     * OutOfMemoryError, so the cap scales with the heap the system grants.
     */
    private fun safeTargetBufferBytes(): Int {
        val heap = Runtime.getRuntime().maxMemory().takeIf { it in 1 until Long.MAX_VALUE } ?: MAX_TARGET_BUFFER_BYTES * 3
        return (heap / 3).coerceIn(MIN_TARGET_BUFFER_BYTES, MAX_TARGET_BUFFER_BYTES).toInt()
    }

    /**
     * Network streams (browser videos, HLS, DASH, Drive) snap to the nearest key
     * frame, which starts at a segment boundary and plays without waiting for a
     * download-and-decode to an exact frame. Local files keep exact seeking,
     * which avoids the HEVC picture-freeze described below.
     */
    private fun seekParametersFor(uri: Uri?): SeekParameters {
        val scheme = uri?.scheme?.lowercase()
        return if (scheme == "http" || scheme == "https") SeekParameters.CLOSEST_SYNC else SeekParameters.EXACT
    }

    private var exoPlayer: ExoPlayer? = null
    private val _usesVlc = MutableStateFlow(false)
    val usesVlc = _usesVlc.asStateFlow()
    private val _vlcVideoSize = MutableStateFlow(0 to 0)
    val vlcVideoSize = _vlcVideoSize.asStateFlow()
    private val _vlcFirstFrame = MutableStateFlow(false)
    val vlcFirstFrame = _vlcFirstFrame.asStateFlow()
    private var vlcPlayback: VlcPlaybackEngine? = null
    private var vlcActiveUri: Uri? = null
    private var vlcExoFallbackUri: Uri? = null
    private var vlcInitializationFailed = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var routingRevision = 0L
    private var routingTimeout: Runnable? = null
    private var desiredAutoPlay = true
    private var activeMimeType: String? = null
    private var appliedPreset: ColorPreset? = null
    private var videoEffectsInitialized = false
    private var rebuildingGradePipeline = false
    private var pendingVideoPreparation: (() -> Unit)? = null
    private var surfaceWaitTimeout: Runnable? = null
    private val decodedSeekRevision = java.util.concurrent.atomic.AtomicLong()
    private val renderedFrames = java.util.concurrent.atomic.AtomicLong()
    val renderedFrameSequence: Long get() = renderedFrames.get()
    @Volatile private var seekDecoderAcknowledgement = -1L
    @Volatile private var pendingDecodedSeekRevision: Long? = null
    @Volatile var lastRenderedPresentationMs: Long = -1L
        private set
    @Volatile var lastCodecOutputColor: String = "Unknown"
        private set
    private var externalSubtitleReadJob: Job? = null
    private var activeExternalSubtitleUri: Uri? = null
    private var activeExternalSubtitleMime: String? = null
    private var localProbeUri: Uri? = null
    private var localProbeUsesMedia3 = true

    val vlcSurfaceEngine: VlcPlaybackEngine
        get() = vlcPlayback ?: createVlc().also { vlcPlayback = it }

    private fun createVlc() = VlcPlaybackEngine(
        context,
        onUpdate = { state ->
            if (_usesVlc.value) {
                _isPlaying.value = state.playing
                _isBuffering.value = state.buffering
                _currentPositionMs.value = state.positionMs
                _durationMs.value = state.durationMs
                _bufferedPositionMs.value = state.bufferedPositionMs
                val a = _abRepeatA.value
                val b = _abRepeatB.value
                if (a != null && b != null && state.positionMs >= b) vlcPlayback?.seekTo(a)
            }
        },
        onTracks = { audio, subtitles ->
            if (_usesVlc.value) {
                _availableAudioTracks.value = audio
                _availableSubtitleTracks.value = subtitles
                _telemetry.value = _telemetry.value.copy(
                    codecName = "LibVLC / native codec",
                    audioFormat = audio.firstOrNull { it.isSelected }?.label ?: "LibVLC / FFmpeg",
                )
            }
        },
        onVideoLayout = { width, height ->
            if (_usesVlc.value) {
                _vlcVideoSize.value = width to height
                _is4kContent.value = width >= 3840 || height >= 2160
            }
        },
        onFirstFrame = { if (_usesVlc.value) _vlcFirstFrame.value = true },
        onSeekUnavailable = { position ->
            if (_usesVlc.value) vlcActiveUri?.let { uri ->
                vlcExoFallbackUri = uri
                prepareMedia(uri, position, autoPlay = desiredAutoPlay, mediaMimeType = activeMimeType)
            }
        },
        onEnd = { if (_usesVlc.value) handlePlaybackEnded() },
        onError = { message ->
            if (vlcPlayback?.unresponsive == true) vlcInitializationFailed = true
            if (_usesVlc.value) {
                val uri = vlcActiveUri
                if (uri != null) {
                    vlcExoFallbackUri = uri
                    prepareMedia(uri, _currentPositionMs.value, autoPlay = desiredAutoPlay,
                        mediaMimeType = activeMimeType)
                } else {
                    _isPlaying.value = false
                    _isBuffering.value = false
                    _playerError.value = message
                }
            }
        },
    )

    private fun shouldUseVlc(uri: Uri, mimeType: String?): Boolean =
        PlaybackBackendPolicy.shouldUseVlc(uri.scheme, uri.lastPathSegment, mimeType,
            growing = isGrowingDownloadUri(uri),
            colorManaged = shouldApplyColorPreset(_hdrColorPreset.value, _hdrEnhanceActive.value) ||
                (localProbeUri == uri && localProbeUsesMedia3))
    // The engine replaces its ExoPlayer when decoder settings change or after a
    // Dolby Vision decoder failure. Screens observe this so the video surface,
    // gestures and media session always talk to the live instance instead of a
    // released one (which left a black picture and dead skip buttons).
    private val _playerInstance = MutableStateFlow<ExoPlayer?>(null)
    val playerInstance = _playerInstance.asStateFlow()
    private var trackSelector: DefaultTrackSelector? = null
    private var rememberedAudioLanguage: String? = null
    private var rememberedSubtitleLanguage: String? = null
    private var rememberedSubtitlesEnabled = true
    private var rememberedMuted = false
    private var pendingBackendAudioIndex: Int? = null
    private var pendingBackendSubtitleIndex: Int? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var equalizer: Equalizer? = null
    private var progressJob: Job? = null
    private var audioSelectionRecoveryParameters: DefaultTrackSelector.Parameters? = null
    private var audioSelectionRecoveryUri: Uri? = null
    private var audioFallbackAttemptedUri: Uri? = null
    private val attemptedAudioFallbackTracks = mutableSetOf<Pair<Int, Int>>()
    private var configuredHwDecoding: Boolean? = null
    private var configuredRemuxUltraBuffer: Boolean? = null
    private var configuredTunneling: Boolean? = null
    private var resumeAfterForeground = false

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

    private val _equalizerPreset = MutableStateFlow("Flat")
    val equalizerPreset = _equalizerPreset.asStateFlow()

    private val _equalizerBandsDb = MutableStateFlow(List(5) { 0f })
    val equalizerBandsDb = _equalizerBandsDb.asStateFlow()

    private val _audioDelayMs = MutableStateFlow(0L)
    val audioDelayMs = _audioDelayMs.asStateFlow()

    private val _subtitleOffsetMs = MutableStateFlow(0L)
    val subtitleOffsetMs = _subtitleOffsetMs.asStateFlow()

    private val _hdrEnhanceActive = MutableStateFlow(false)
    val hdrEnhanceActive = _hdrEnhanceActive.asStateFlow()

    private val _hdrColorPreset = MutableStateFlow(ColorPresets.HDR_DISABLED)
    val hdrColorPreset = _hdrColorPreset.asStateFlow()

    /** True when the video decoder reports an HDR transfer function (HLG/PQ). */
    private val _isHdrContent = MutableStateFlow(false)
    val isHdrContent = _isHdrContent.asStateFlow()

    /** False until Media3 exposes color metadata for the selected video format. */
    private val _isVideoColorMetadataKnown = MutableStateFlow(false)
    val isVideoColorMetadataKnown = _isVideoColorMetadataKnown.asStateFlow()

    /** True when the selected video stream is UHD 4K or larger. */
    private val _is4kContent = MutableStateFlow(false)
    val is4kContent = _is4kContent.asStateFlow()

    /** HDR enhancement state. Native HDR output is managed by HdrColorModeManager. */
    fun setPictureMode(enabled: Boolean, preset: ColorPreset) {
        if (_hdrEnhanceActive.value == enabled && _hdrColorPreset.value == preset) return
        _hdrEnhanceActive.value = enabled
        _hdrColorPreset.value = preset
        applyVideoGrade()
    }

    fun setHdrEnhanceActive(enabled: Boolean) {
        _hdrEnhanceActive.value = enabled
        applyVideoGrade()
    }

    /**
     * Mark the next item's color metadata unknown before any async preparation.
     * PlayerScreen treats unknown metadata as native output, avoiding reuse of an
     * SDR GLES surface while the next track's transfer function is being detected.
     */
    fun beginVideoItemTransition() {
        _isHdrContent.value = false
        _isVideoColorMetadataKnown.value = false
        _is4kContent.value = false
    }

    fun setHdrColorPreset(preset: ColorPreset) {
        _hdrColorPreset.value = preset
        applyVideoGrade()
    }

    private fun applyVideoGrade(allowRebuild: Boolean = true) {
        val preset = _hdrColorPreset.value.takeIf { shouldApplyColorPreset(it, _hdrEnhanceActive.value) }
        if (preset != null) {
            // This also covers VLC -> Media3, which can reuse an empty native
            // player without crossing the player-rebuild branch below.
            trackSelector?.takeIf { it.parameters.tunnelingEnabled }?.let { selector ->
                selector.parameters = selector.buildUponParameters().setTunnelingEnabled(false).build()
            }
        }
        if (preset != null && _usesVlc.value) {
            val uri = vlcActiveUri ?: return
            vlcExoFallbackUri = uri
            prepareMedia(uri, _currentPositionMs.value, desiredAutoPlay, mediaMimeType = activeMimeType)
        }
        val player = exoPlayer ?: return
        if (!_usesVlc.value && allowRebuild && !rebuildingGradePipeline &&
            player.currentMediaItem != null && videoEffectsInitialized != (preset != null)) {
            // In Media3 1.11 even setVideoEffects(emptyList()) permanently enables
            // the GL video sink for this renderer. Original must have NO sink.
            // Cross this boundary once per explicit user mode change, retaining
            // the media item/track overrides and last decoded position. Changing
            // one grade to another does not recreate the player.
            val item = player.currentMediaItem ?: return
            val uri = item.localConfiguration?.uri ?: return
            val params = trackSelector?.parameters
            val position = _currentPositionMs.value
            val autoPlay = desiredAutoPlay
            rebuildingGradePipeline = true
            try {
                initializePlayer(configuredHwDecoding ?: true, configuredRemuxUltraBuffer ?: false,
                    configuredTunneling ?: false)
                params?.let {
                    trackSelector?.parameters = it.buildUpon()
                        .setTunnelingEnabled(configuredTunneling == true && preset == null)
                        .build()
                }
                prepareMedia(uri, position, autoPlay, activeExternalSubtitleUri, activeExternalSubtitleMime,
                    item.localConfiguration?.mimeType, mediaTitle = item.mediaMetadata.title?.toString())
            } finally { rebuildingGradePipeline = false }
            return
        }
        if (preset != null && appliedPreset != preset) {
            player.setVideoEffects(listOf(ColorPresetEffect(preset)))
            if (!player.playWhenReady && player.playbackState == Player.STATE_READY) {
                VideoEffectRedraw.request(player)
            }
            appliedPreset = preset
            videoEffectsInitialized = true
        }
        // Do not call setVideoEffects(emptyList()) on a direct-output renderer.
    }

    fun setAudioDelay(ms: Long) {
        // NOTE: this updates the reported delay value so the slider/UI is no
        // longer a dead no-op, but actually shifting audio relative to video
        // requires a custom AudioProcessor/renderer-level change that isn't
        // implemented yet - so audible sync will not shift from this alone.
        _audioDelayMs.value = ms
        if (_usesVlc.value) vlcPlayback?.setDelays(ms, _subtitleOffsetMs.value)
    }

    fun setSubtitleOffset(ms: Long) {
        _subtitleOffsetMs.value = ms
        if (_usesVlc.value) vlcPlayback?.setDelays(_audioDelayMs.value, ms)
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
        externalSubtitleReadJob?.cancel()
        val videoUri = if (_usesVlc.value) vlcActiveUri else exoPlayer?.currentMediaItem?.localConfiguration?.uri
        val loadRevision = routingRevision
        externalSubtitleReadJob = coroutineScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
            }
            val current = if (_usesVlc.value) vlcActiveUri else exoPlayer?.currentMediaItem?.localConfiguration?.uri
            if (current != videoUri || routingRevision != loadRevision) return@launch
            if (text == null) { _playerError.value = "Could not read subtitle file."; return@launch }
            lastExternalSubtitleRawText = text
            lastExternalSubtitleMimeType = guessSubtitleMimeType(uri.toString())
            reapplyExternalSubtitleWithOffset()
        }
    }

    private fun reapplyExternalSubtitleWithOffset() {
        val raw = lastExternalSubtitleRawText ?: return
        val mime = lastExternalSubtitleMimeType ?: MimeTypes.TEXT_VTT
        val videoUri = if (_usesVlc.value) vlcActiveUri else exoPlayer?.currentMediaItem?.localConfiguration?.uri
        videoUri ?: return
        val position = _currentPositionMs.value
        val offset = _subtitleOffsetMs.value
        val loadRevision = routingRevision
        coroutineScope.launch {
            val file = withContext(Dispatchers.IO) {
                java.io.File.createTempFile("hiplayer_sub", subtitleExtensionFor(mime), context.cacheDir).apply {
                    writeText(if (offset == 0L) raw else shiftSubtitleTimestamps(raw, offset, mime))
                }
            }
            val current = if (_usesVlc.value) vlcActiveUri else exoPlayer?.currentMediaItem?.localConfiguration?.uri
            if (current != videoUri || routingRevision != loadRevision || _subtitleOffsetMs.value != offset) { file.delete(); return@launch }
            vlcExoFallbackUri = videoUri
            prepareMedia(videoUri, position, desiredAutoPlay, Uri.fromFile(file), mime, activeMimeType)
        }
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

    private val _isBackgroundPlayActive = MutableStateFlow(false)
    val isBackgroundPlayActive = _isBackgroundPlayActive.asStateFlow()

    private val _abRepeatA = MutableStateFlow<Long?>(null)
    val abRepeatA = _abRepeatA.asStateFlow()

    private val _abRepeatB = MutableStateFlow<Long?>(null)
    val abRepeatB = _abRepeatB.asStateFlow()

        private val _playerError = MutableStateFlow<String?>(null)
    val playerError = _playerError.asStateFlow()
    private var dolbyVisionRetryAttempted = false
    private var pendingResumePositionMs: Long = 0L
    private var activeGrowingUri: Uri? = null
    @Volatile private var downloadedSeekEndMs = 0L
    private var growingRetryJob: Job? = null
    private var growingRetryAttempts = 0
    private var growingPlaybackReachedReady = false
    private var growingPlaybackGraceUntilMs = 0L
    var onVideoEnded: (() -> Unit)? = null
    @Volatile private var playbackEnded = false
    internal val hasPlaybackEnded: Boolean get() = playbackEnded

    private fun handlePlaybackEnded() {
        playbackEnded = true
        desiredAutoPlay = false
        _isPlaying.value = false
        _isBuffering.value = false
        onVideoEnded?.invoke()
    }

    fun isPreparedFor(uri: Uri): Boolean = if (_usesVlc.value) {
        vlcActiveUri == uri
    } else {
        exoPlayer?.currentMediaItem?.localConfiguration?.uri == uri
    }

    private val playerListener = object : Player.Listener {
        override fun onSurfaceSizeChanged(width: Int, height: Int) {
            if (width > 0 && height > 0) pendingVideoPreparation?.invoke()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (_usesVlc.value) return
            _isBuffering.value = (playbackState == Player.STATE_BUFFERING)
            if (playbackState == Player.STATE_READY) {
                if (exoPlayer?.currentMediaItem?.localConfiguration?.uri == activeGrowingUri) {
                    growingPlaybackReachedReady = true
                }
                val resumePosition = pendingResumePositionMs
                if (resumePosition > 0L) {
                    pendingResumePositionMs = 0L
                    exoPlayer?.setSeekParameters(seekParametersFor(exoPlayer?.currentMediaItem?.localConfiguration?.uri))
                    exoPlayer?.seekTo(resumePosition)
                }
                audioSelectionRecoveryParameters = null
                audioSelectionRecoveryUri = null
                _durationMs.value = exoPlayer?.duration?.coerceAtLeast(0L) ?: 0L
                _playerError.value = null
                // Some containers publish their caption tracks only once they are
                // prepared. Refresh here as well as in onTracksChanged so the CC
                // sheet never opens with a stale or empty list.
                updateAvailableTracks(exoPlayer?.currentTracks)
                updateVideoOutputInfo()
                updateTelemetry()
                exoPlayer?.let(::requestPausedVideoFrame)
            } else if (playbackState == Player.STATE_ENDED) {
                handlePlaybackEnded()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (_usesVlc.value) return
            _isPlaying.value = isPlaying
            if (isPlaying) {
                updateTelemetry()
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            if (_usesVlc.value) return
            updateAvailableTracks(tracks)
            restoreBackendTrackChoices()
            updateVideoOutputInfo()
            updateTelemetry()
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            setupEqualizer(audioSessionId)
        }

        override fun onPlayerError(error: PlaybackException) {
            if (_usesVlc.value) return
            _isBuffering.value = false
            _isPlaying.value = false
            val recoveryParameters = audioSelectionRecoveryParameters
            val recoveryUri = audioSelectionRecoveryUri
            if (recoveryParameters != null && recoveryUri != null) {
                // Some device decoders reject a newly selected language after
                // the renderer has already been prepared. Restore the previous
                // selection and re-prepare at the same position instead of
                // exposing a fatal runtime error to the user.
                audioSelectionRecoveryParameters = null
                audioSelectionRecoveryUri = null
                trackSelector?.parameters = recoveryParameters
                val recoveryPosition = _currentPositionMs.value
                coroutineScope.launch(Dispatchers.Main) {
                    prepareMedia(recoveryUri, recoveryPosition, autoPlay = true)
                }
                return
            }
            val details = buildString {
                var cause: Throwable? = error
                var depth = 0
                while (cause != null && depth < 8) {
                    append(cause.javaClass.simpleName).append(' ')
                    append(cause.message.orEmpty()).append(' ')
                    cause = cause.cause
                    depth++
                }
            }.lowercase(Locale.ROOT)
            if (AudioRendererFallbackPolicy.shouldTryPlayableTrack(details) && recoverWithPlayableAudioTrack(details)) {
                return
            }
            if (isUnsupportedVideoRendererFailure(details)) {
                _playerError.value = unsupportedVideoRendererMessage(details)
                return
            }
            val isDolbyVisionDecoderFailure = details.contains("dolby-vision") ||
                details.contains("dolby vision") ||
                (details.contains("mediacodecvideorenderer") && details.contains("hevc"))
            val retryUri = exoPlayer?.currentMediaItem?.localConfiguration?.uri
            if (isDolbyVisionDecoderFailure && !dolbyVisionRetryAttempted && retryUri != null) {
                dolbyVisionRetryAttempted = true
                val retryPosition = _currentPositionMs.value
                coroutineScope.launch(Dispatchers.Main) {
                    // Dolby Vision streams can fail when tunneling is enabled.
                    // Recreate the player once without tunneling and resume at
                    // the current position; do not loop on a second failure.
                    initializePlayer(
                        enableHwDecoding = false,
                        enableRemuxUltraBuffer = true,
                        enableTunneling = false
                    )
                    prepareMedia(retryUri, retryPosition, autoPlay = true, resetDecoderRetry = false)
                }
                return
            }
            if (retryUri != null && activeGrowingUri == retryUri && !growingPlaybackReachedReady) {
                // Do not reject MP4/other containers immediately: if their
                // metadata is at the beginning, they can play while growing;
                // if it is at the end, retrying lets playback begin as soon as
                // the index arrives.
                if (growingRetryAttempts < 2 && shouldKeepAwaitingGrowingData(retryUri) &&
                    error.errorCode != PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED) {
                    // An MP4 whose metadata arrives at the end can need more
                    // than 30 seconds before its extractor becomes usable.
                    // Keep retrying while the file remains registered as an
                    // active download; navigation/release cancels the job.
                    growingRetryAttempts = (growingRetryAttempts + 1).coerceAtMost(Int.MAX_VALUE)
                    val retryPosition = _currentPositionMs.value
                    _isBuffering.value = true
                    growingRetryJob = coroutineScope.launch(Dispatchers.Main) {
                        delay(1500L)
                        prepareMedia(retryUri, retryPosition, autoPlay = true, resetDecoderRetry = false)
                    }
                    return
                }
                _playerError.value = incompleteDownloadMessage(retryUri)
                return
            }
            Log.e("HiPlayerPlayback", "Playback failed: ${error.errorCodeName}")
            _playerError.value = "This video could not be opened. Check the file or try another source."
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
        enableRemuxUltraBuffer: Boolean = false,
        enableTunneling: Boolean = false
    ) {
        val sessionPlayerBeforeRebuild = exoPlayer
        appliedPreset = null
        videoEffectsInitialized = false
        release(forReplacement = true)

        val renderersFactory = DefaultRenderersFactory(context).apply {
            // Prefer bundled extension renderers when a hardware decoder claims
            // support but cannot actually decode a track (notably DTS/DTS-HD via
            // FFmpeg). The FFmpeg extension is audio-only; HEVC HDR remains on
            // the platform MediaCodec path.
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            setEnableDecoderFallback(true)
            setMediaCodecSelector(if (enableHwDecoding) {
                com.example.playback.OptimizedPlayerBuilder.hardwarePreferredCodecSelector()
            } else {
                androidx.media3.exoplayer.mediacodec.MediaCodecSelector { mime, secure, tunneled ->
                    androidx.media3.exoplayer.mediacodec.MediaCodecSelector.DEFAULT
                        .getDecoderInfos(mime, secure, tunneled).sortedBy { it.hardwareAccelerated }
                }
            })
        }

        // Buffer tuning for smooth playback of large/high-bitrate files and
        // remote streams. Two minutes is a maximum time target, not an
        // unlimited allocation: the byte ceiling protects phones from trying
        // to retain a full two minutes of 4K HDR in RAM.
        // Previously this generous buffering only applied when the user had
        // manually enabled a hidden "Remux Ultra Buffer" setting - everyone
        // else got ExoPlayer's small default buffers, which is exactly what
        // causes stutter/rebuffering on big 4K/HDR files. Now a solid buffer
        // is applied by default; the setting simply pushes it further.
        val targetBufferBytes = safeTargetBufferBytes()
        val loadControl = if (enableRemuxUltraBuffer) {
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    10_000,   // Keep a safety buffer without delaying first frame.
                    120_000,  // Prefetch up to two minutes for remote forward seeks.
                    250,      // Start local playback after a short preroll.
                    1_000     // Recover quickly after a rebuffer.
                )
                .setTargetBufferBytes(targetBufferBytes) // Bounded by the app's heap size.
                // Keep the last few seconds so a 10-second rewind is served from
                // memory instead of reopening the file or stream.
                .setBackBuffer(BACK_BUFFER_MS, true)
                // Stop at either the two-minute target or the memory cap.
                .setPrioritizeTimeOverSizeThresholds(false)
                .setAllocator(DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
                .build()
        } else {
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    8_000,    // Avoid waiting for a long initial 4K buffer.
                    120_000,  // Prefetch up to two minutes when bandwidth permits.
                    250,      // Fast first-frame startup for local media.
                    1_000     // Quick recovery after a rebuffer.
                )
                // Reserve a bounded allocator for high-bitrate local media.
                // This improves 4K/HDR rebuffering without reading the full
                // file into memory or changing any UI/player behavior.
                .setTargetBufferBytes(targetBufferBytes)
                // Preserve a short range behind the playhead so quick reverse
                // seeks do not immediately trigger another disk or network read.
                .setBackBuffer(BACK_BUFFER_MS, true)
                // Keeps 4K/HDR buffering bounded on memory-constrained phones.
                .setPrioritizeTimeOverSizeThresholds(false)
                .setAllocator(DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
                .build()
        }

        trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setTunnelingEnabled(enableTunneling && !shouldApplyColorPreset(_hdrColorPreset.value, _hdrEnhanceActive.value))
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
                    .setAllowVideoNonSeamlessAdaptiveness(true)
            )
        }

        applyRememberedTrackPreferences(clearOverrides = false)

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

        // Keep credentials out of the visible stream URL and Room history.
        // The resolver adds a source's transient in-memory headers to every
        // Media3 request, including HLS/DASH manifests and child segments.
        val authenticatedHttpFactory = ResolvingDataSource.Factory(
            httpDataSourceFactory,
            ResolvingDataSource.Resolver { dataSpec ->
                val headers = RemoteStreamHeadersRegistry.headersFor(dataSpec.uri)
                if (headers.isEmpty()) dataSpec else dataSpec.withAdditionalHeaders(headers)
            }
        )
        val accountAwareHttpFactory = androidx.media3.datasource.DataSource.Factory {
            com.example.drive.AccountAwareHttpDataSource(context.applicationContext, authenticatedHttpFactory)
        }
        val dataSourceFactory = DefaultDataSource.Factory(context, accountAwareHttpFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(
            dataSourceFactory,
            createOptimizedExtractorsFactory()
        )

        exoPlayer = ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector!!)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true) // Auto handle audio focus
            .setHandleAudioBecomingNoisy(true)
            // Exact seeking makes the video decode through the nearest keyframe
            // to the requested timestamp before audio resumes, preventing the
            // several-second picture-freeze/catch-up effect on long-GOP HEVC.
            .setSeekParameters(SeekParameters.EXACT)
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_OFF
                volume = if (rememberedMuted) 0f else 1f
                playbackParameters = PlaybackParameters(_playbackSpeed.value)
                // Keep scaling in the native video pipeline for UHD surfaces.
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                playWhenReady = true
                val frameOwner = this
                setVideoFrameMetadataListener { presentationTimeUs, _, format, output ->
                    if (_playerInstance.value !== frameOwner) return@setVideoFrameMetadataListener
                    renderedFrames.incrementAndGet()
                    lastRenderedPresentationMs = presentationTimeUs / 1_000
                    val seek = pendingDecodedSeekRevision
                    if (seek != null && seekDecoderAcknowledgement == seek) {
                        mainHandler.post {
                            if (_playerInstance.value === frameOwner && pendingDecodedSeekRevision == seek) {
                                pendingDecodedSeekRevision = null
                                _currentPositionMs.value = frameOwner.currentPosition.coerceAtLeast(0)
                            }
                        }
                    }
                    fun color(key: String): Int = if (output?.containsKey(key) == true) output.getInteger(key) else -1
                    val observed = "standard=${color("color-standard")},transfer=${color("color-transfer")},range=${color("color-range")},input=${format.colorInfo}"
                    if (lastCodecOutputColor != observed) {
                        lastCodecOutputColor = observed
                        Log.i("HiPlayerColor", "Codec output $observed")
                    }
                }
                addListener(playerListener)
            }
        applyVideoGrade(allowRebuild = false)
        _playerInstance.value = exoPlayer
        if (!_usesVlc.value) _isPlaying.value = exoPlayer?.isPlaying == true
        HiMediaSessionService.replacePlayer(sessionPlayerBeforeRebuild, exoPlayer)
        setBackgroundPlay(_isBackgroundPlayActive.value)
        setupLoudnessEnhancer()
        setupEqualizer()
        startProgressTracker()
        // initializePlayer is also used for the engine's first default instance.
        // Record that configuration immediately so the first playVideo call does
        // not release and recreate the just-created player with identical values.
        configuredHwDecoding = enableHwDecoding
        configuredRemuxUltraBuffer = enableRemuxUltraBuffer
        configuredTunneling = enableTunneling
    }

    /**
     * Keeps progressive extraction lightweight for large local media while
     * enabling fast approximate seeking when a container supports it. MIME
     * types supplied by the library are still preferred, so this factory only
     * falls back to its optimized extractor order when necessary.
     */
    private fun createOptimizedExtractorsFactory(): DefaultExtractorsFactory =
        DefaultExtractorsFactory()
            // For multi-GB 4K MP4/MKV files, trust the container index instead of
            // triggering constant-bitrate probing over the full file.
            .setConstantBitrateSeekingEnabled(false)

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

    private fun setupEqualizer(sessionId: Int? = null) {
        try {
            val audioSessionId = sessionId
                ?: exoPlayer?.audioSessionId
                ?: C.AUDIO_SESSION_ID_UNSET
            if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
            equalizer?.release()
            equalizer = Equalizer(0, audioSessionId).apply { enabled = true }
            applyEqualizerPreset(_equalizerPreset.value)
        } catch (_: Exception) {
            equalizer?.release()
            equalizer = null
        }
    }

    fun setEqualizerPreset(preset: String) {
        val normalized = preset.trim().ifBlank { "Flat" }
        _equalizerPreset.value = normalized
        applyEqualizerPreset(normalized)
    }

    fun setCustomEqualizerBand(index: Int, gainDb: Float) {
        if (index !in 0..4) return
        val updated = _equalizerBandsDb.value.toMutableList()
        updated[index] = gainDb.coerceIn(-12f, 12f)
        _equalizerBandsDb.value = updated
        _equalizerPreset.value = "Custom"
        applyEqualizerPreset("Custom")
    }

    private fun applyEqualizerPreset(preset: String) {
        val eq = equalizer ?: return
        try {
            val gainsDb = when (preset) {
                "Bass Boost" -> floatArrayOf(8f, 5f, 2f, 0f, -1f)
                "Jazz" -> floatArrayOf(4f, 2f, 0f, 2f, 4f)
                "Rock" -> floatArrayOf(6f, 3f, -1f, 3f, 5f)
                "Classical" -> floatArrayOf(3f, 0f, 2f, 4f, 3f)
                "Vocal" -> floatArrayOf(-1f, 2f, 5f, 2f, -1f)
                "Dance" -> floatArrayOf(6f, 3f, 0f, 4f, 6f)
                "Electronic" -> floatArrayOf(5f, 1f, -2f, 3f, 7f)
                "Acoustic" -> floatArrayOf(2f, 3f, 4f, 3f, 1f)
                "Podcast" -> floatArrayOf(-2f, 1f, 5f, 3f, -3f)
                "Movie" -> floatArrayOf(5f, 2f, 0f, 3f, 5f)
                "Custom" -> _equalizerBandsDb.value.toFloatArray()
                else -> floatArrayOf(0f, 0f, 0f, 0f, 0f)
            }
            val range = eq.bandLevelRange
            val minLevel = range[0].toInt()
            val maxLevel = range[1].toInt()
            val bandCount = eq.numberOfBands.toInt()
            for (band in 0 until bandCount) {
                val normalized = if (bandCount <= 1) 0f else band.toFloat() / (bandCount - 1)
                val position = normalized * (gainsDb.size - 1)
                val low = position.toInt().coerceIn(0, gainsDb.lastIndex)
                val high = (low + 1).coerceAtMost(gainsDb.lastIndex)
                val fraction = position - low
                val gainDb = gainsDb[low] + (gainsDb[high] - gainsDb[low]) * fraction
                val level = (gainDb * 100f).toInt().coerceIn(minLevel, maxLevel).toShort()
                eq.setBandLevel(band.toShort(), level)
            }
            eq.enabled = true
        } catch (_: Exception) {
            // OEM audio stacks may expose no writable bands; playback remains usable.
        }
    }

    fun setVolumeBoost(percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        _volumeBoostPercent.value = clamped
        if (_usesVlc.value) vlcPlayback?.setMuted(rememberedMuted, clamped)
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
        if (exoPlayer != null && configuredHwDecoding == hwDecoding && configuredRemuxUltraBuffer == remuxUltraBuffer && configuredTunneling == tunneling) {
            return
        }
        val currentUri = exoPlayer?.currentMediaItem?.localConfiguration?.uri
        val currentMimeType = exoPlayer?.currentMediaItem?.localConfiguration?.mimeType
        val currentTitle = exoPlayer?.currentMediaItem?.mediaMetadata?.title?.toString()
        val currentPos = exoPlayer?.currentPosition ?: 0L
        val isCurrentlyPlaying = exoPlayer?.isPlaying ?: false

        initializePlayer(
            enableHwDecoding = hwDecoding,
            enableRemuxUltraBuffer = remuxUltraBuffer,
            enableTunneling = tunneling
        )
        configuredHwDecoding = hwDecoding
        configuredRemuxUltraBuffer = remuxUltraBuffer
        configuredTunneling = tunneling

        if (currentUri != null) {
            prepareMedia(
                uri = currentUri,
                startPositionMs = currentPos,
                autoPlay = isCurrentlyPlaying,
                mediaMimeType = currentMimeType,
                mediaTitle = currentTitle,
            )
        }
    }

    fun prepareMedia(
        uri: Uri,
        startPositionMs: Long = 0L,
        autoPlay: Boolean = true,
        externalSubtitleUri: Uri? = null,
        externalSubtitleMimeType: String? = null,
        mediaMimeType: String? = null,
        resetDecoderRetry: Boolean = true,
        mediaTitle: String? = null,
        audioFallbackRecovery: Boolean = false,
    ) {
        if (uri.scheme == null && uri.path?.startsWith("/") == true) {
            prepareMedia(Uri.fromFile(java.io.File(uri.path!!)), startPositionMs, autoPlay,
                externalSubtitleUri, externalSubtitleMimeType, mediaMimeType, resetDecoderRetry, mediaTitle, audioFallbackRecovery)
            return
        }
        playbackEnded = false
        if (uri != vlcActiveUri && uri != exoPlayer?.currentMediaItem?.localConfiguration?.uri) {
            pendingBackendAudioIndex = null
            pendingBackendSubtitleIndex = null
        }
        pendingVideoPreparation = null
        surfaceWaitTimeout?.let(mainHandler::removeCallbacks)
        val routeToken = ++routingRevision
        routingTimeout?.let(mainHandler::removeCallbacks)
        desiredAutoPlay = autoPlay
        activeMimeType = mediaMimeType
        val needsProbe = localProbeUri != uri && !isGrowingDownloadUri(uri) &&
            PlaybackBackendPolicy.shouldUseVlc(uri.scheme, uri.lastPathSegment, mediaMimeType)
        if (needsProbe) {
            _isBuffering.value = true
            _playerError.value = null
            fun continueWith(useMedia3: Boolean) {
                if (routeToken != routingRevision) return
                routingTimeout?.let(mainHandler::removeCallbacks)
                localProbeUri = uri
                localProbeUsesMedia3 = useMedia3
                prepareMedia(uri, startPositionMs, desiredAutoPlay, externalSubtitleUri,
                    externalSubtitleMimeType, mediaMimeType, resetDecoderRetry, mediaTitle, audioFallbackRecovery)
            }
            val timeout = Runnable { continueWith(true) }
            routingTimeout = timeout
            mainHandler.postDelayed(timeout, 5_000)
            if (!LocalVideoProbe.submit(context.applicationContext, uri) { useMedia3 ->
                mainHandler.post { continueWith(useMedia3) }
            }) continueWith(true)
            return
        }
        if (uri != vlcActiveUri && uri != exoPlayer?.currentMediaItem?.localConfiguration?.uri) vlcExoFallbackUri = null
        if (externalSubtitleUri == null && shouldUseVlc(uri, mediaMimeType) &&
            vlcExoFallbackUri != uri && !vlcInitializationFailed
        ) {
            // Native initialization can throw UnsatisfiedLinkError on a device
            // even when the APK passed ELF checks in CI. Do not force-close
            // the entire app or stop a working Media3 instance first.
            val backend = try {
                vlcSurfaceEngine
            } catch (error: LinkageError) {
                vlcInitializationFailed = true
                vlcExoFallbackUri = uri
                Log.e("HiPlayerLibVLC", "Native VLC initialization unavailable", error)
                null
            } catch (error: RuntimeException) {
                vlcInitializationFailed = true
                vlcExoFallbackUri = uri
                Log.e("HiPlayerLibVLC", "VLC initialization failed", error)
                null
            }
            if (backend != null) {
                vlcActiveUri = uri
                if (!_usesVlc.value) exoPlayer?.stop()
                _usesVlc.value = true
                _vlcFirstFrame.value = false
                _vlcVideoSize.value = 0 to 0
                _playerError.value = null
                _availableAudioTracks.value = emptyList()
                _availableSubtitleTracks.value = emptyList()
                _isHdrContent.value = false
                _isVideoColorMetadataKnown.value = false
                try {
                    backend.setSpeed(_playbackSpeed.value)
                    backend.setMuted(rememberedMuted, _volumeBoostPercent.value)
                    backend.setDelays(_audioDelayMs.value, _subtitleOffsetMs.value)
                    backend.open(uri, startPositionMs, autoPlay, isGrowingDownloadUri(uri))
                    return
                } catch (error: LinkageError) {
                    Log.e("HiPlayerLibVLC", "Native VLC playback failed; using Media3", error)
                } catch (error: RuntimeException) {
                    Log.e("HiPlayerLibVLC", "VLC playback startup failed; using Media3", error)
                }
                vlcExoFallbackUri = uri
                runCatching { backend.stop() }
            }
        }
        if (_usesVlc.value) {
            if (uri == vlcActiveUri) {
                pendingBackendAudioIndex = _availableAudioTracks.value.indexOfFirst { it.isSelected }.takeIf { it >= 0 }
                pendingBackendSubtitleIndex = _availableSubtitleTracks.value.indexOfFirst { it.isSelected }
            }
            vlcPlayback?.stop()
            _usesVlc.value = false
            // A newly selected paused Media3 player may never emit an
            // onIsPlayingChanged(false) callback: it has always been paused.
            // Do not retain the old VLC instance's last asynchronous sample.
            _isPlaying.value = false
            _vlcFirstFrame.value = false
        }
        _playerError.value = null
        if (!audioFallbackRecovery || audioFallbackAttemptedUri != uri) {
            audioFallbackAttemptedUri = uri
            attemptedAudioFallbackTracks.clear()
        }
        // Never carry a file-local group/index override into a different film.
        if (exoPlayer?.currentMediaItem?.localConfiguration?.uri != uri) applyRememberedTrackPreferences(clearOverrides = true)
        if (uri != activeGrowingUri) {
            growingRetryJob?.cancel()
            growingRetryJob = null
            growingRetryAttempts = 0
            downloadedSeekEndMs = 0
            activeGrowingUri = uri.takeIf { isGrowingDownloadUri(it) }
            growingPlaybackReachedReady = false
            growingPlaybackGraceUntilMs = if (activeGrowingUri != null) {
                System.currentTimeMillis() + GROWING_INITIAL_GRACE_MS
            } else {
                0L
            }
        }
        if (resetDecoderRetry) dolbyVisionRetryAttempted = false
        // Do not show caption tracks from a previously played film while this
        // item is still being prepared. Fresh tracks are published on
        // onTracksChanged/STATE_READY.
        _availableAudioTracks.value = emptyList()
        _availableSubtitleTracks.value = emptyList()
        // A growing-source retry re-prepares the same MediaItem. Preserve the
        // already-detected HDR/color state in that case; resetting it can make
        // Compose swap surfaces while the incremental Matroska stream is active.
        if (exoPlayer?.currentMediaItem?.localConfiguration?.uri != uri) {
            beginVideoItemTransition()
        }
        if (uri != vlcActiveUri && uri != exoPlayer?.currentMediaItem?.localConfiguration?.uri) {
            activeExternalSubtitleUri = null
            activeExternalSubtitleMime = null
        }
        if (externalSubtitleUri != null) {
            activeExternalSubtitleUri = externalSubtitleUri
            activeExternalSubtitleMime = externalSubtitleMimeType
        }
        val title = mediaTitle ?: exoPlayer?.currentMediaItem
            ?.takeIf { it.localConfiguration?.uri == uri }?.mediaMetadata?.title?.toString()
        val mediaItemBuilder = MediaItem.Builder().setUri(uri)
            .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(title).build())
        if (!mediaMimeType.isNullOrBlank() && mediaMimeType != "video/*" && mediaMimeType != "*/*") {
            // Supplying the known MediaStore MIME type lets Media3 select the
            // right progressive extractor immediately instead of relying only
            // on broad container sniffing for every large local file.
            mediaItemBuilder.setMimeType(mediaMimeType)
        }

        val subtitleUri = activeExternalSubtitleUri
        if (subtitleUri != null) {
            val mimeType = activeExternalSubtitleMime ?: guessSubtitleMimeType(subtitleUri.toString())
            val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(subtitleUri)
                .setMimeType(mimeType)
                .setLanguage("en")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            mediaItemBuilder.setSubtitleConfigurations(listOf(subtitleConfig))
        }

        val mediaItem = mediaItemBuilder.build()
        applyVideoGrade(allowRebuild = false)
        exoPlayer?.apply {
            stop()
            val initialPositionMs = startPositionMs.coerceAtLeast(0L)
            pendingDecodedSeekRevision = null
            _currentPositionMs.value = initialPositionMs
            val growingSource = if (isGrowingDownloadUri(uri)) {
                createGrowingFileSource(uri, mediaItem)
            } else {
                null
            }
            if (growingSource != null) {
                setMediaSource(growingSource, initialPositionMs)
            } else {
                // Media3 opens content URIs on its loader thread. Opening an
                // AssetFileDescriptor here blocks the main thread on the media
                // provider and can freeze controls and rotation during startup.
                setMediaItem(mediaItem, initialPositionMs)
            }
            // Supplying the initial position to the source avoids a second
            // STATE_READY-to-seek-to-buffer cycle on Continue Watching. This is
            // still exact seeking, but the decoder resolves it during prepare
            // instead of starting at zero and seeking only after audio is ready.
            setSeekParameters(seekParametersFor(uri))
            pendingResumePositionMs = 0L
            playWhenReady = autoPlay
            prepareWithVideoSurface(this, routeToken, mediaMimeType?.startsWith("audio/") == true)
        }
        setupLoudnessEnhancer()
    }

    private fun prepareWithVideoSurface(player: ExoPlayer, token: Long, audioOnly: Boolean) {
        // Media3's paused GL renderer can consume its one initial frame while
        // Compose is still binding the replacement PlayerView. Start only after
        // ExoPlayer acknowledges a real SurfaceView size. The UI never waits.
        fun startIfReady() {
            if (player !== exoPlayer || token != routingRevision) return
            if (!audioOnly && !_isBackgroundPlayActive.value &&
                (player.surfaceSize.width <= 0 || player.surfaceSize.height <= 0)) return
            pendingVideoPreparation = null
            surfaceWaitTimeout?.let(mainHandler::removeCallbacks)
            player.prepare()
        }
        pendingVideoPreparation = ::startIfReady
        _isBuffering.value = true
        surfaceWaitTimeout = Runnable {
            if (token == routingRevision && player === exoPlayer && pendingVideoPreparation != null) {
                pendingVideoPreparation = null
                _isBuffering.value = false
                _playerError.value = "Video output is unavailable. Return to the player and try again."
            }
        }.also { mainHandler.postDelayed(it, 8_000) }
        startIfReady()
    }

    private fun createGrowingFileSource(uri: Uri, mediaItem: MediaItem): MediaSource? {
        val key = downloadRegistryKey(uri)
        // Recheck registry state after URI classification: a download can finish
        // between the initial test and source construction. Completed media must
        // go through the regular extractor factory so its final Cues are indexed.
        if (!isGrowingDownloadUri(uri)) return null
        return DefaultMediaSourceFactory(
            com.example.playback.FileDescriptorDataSource.Factory(context, waitForGrowth = true),
            createGrowingExtractorsFactory(uri)
        )
            // A growing file signals a recoverable I/O error at temporary EOF.
            // Let Media3 retry the same progressive source while its registry
            // entry remains active instead of promoting it to a fatal player
            // error and recreating the whole surface/player.
            .setLoadErrorHandlingPolicy(growingFileLoadErrorPolicy(key))
            .createMediaSource(mediaItem)
    }

    /** True for an incomplete source, but false for a pending-name alias already published as complete. */
    fun isActivelyGrowingDownload(uri: Uri?): Boolean {
        uri ?: return false
        val key = downloadRegistryKey(uri)
        return DownloadingFilesRegistry.completedUri(key) == null && isGrowingDownloadUri(uri)
    }

    private fun createGrowingExtractorsFactory(uri: Uri): ExtractorsFactory {
        // Keep the normal parser on the sequential path while the tail Cues are
        // unavailable. The wrapper incrementally scans complete top-level
        // Clusters and reads each Cluster Timecode, without inspecting block
        // payloads or guessing time-to-byte positions.
        val flags = MatroskaExtractor.FLAG_DISABLE_SEEK_FOR_CUES
        val factory = createOptimizedExtractorsFactory()
            .setMatroskaExtractorFlags(flags)
        val key = downloadRegistryKey(uri)
        return GrowingMkvExtractorsFactory.wrap(
            delegateFactory = factory,
            context = context,
            uri = uri,
            isDownloading = { isGrowingDownloadUri(uri) },
            onSeekFrontier = { frontier -> mainHandler.post { if (activeGrowingUri == uri) downloadedSeekEndMs = frontier } },
        )
    }

    private fun growingFileLoadErrorPolicy(downloadKey: String) =
        object : DefaultLoadErrorHandlingPolicy() {
            override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long =
                if (isGrowingDownloadKey(downloadKey) && loadErrorInfo.errorCount <= 3 &&
                    loadErrorInfo.exception !is androidx.media3.common.ParserException) 1_500L
                else super.getRetryDelayMsFor(loadErrorInfo)

            override fun getMinimumLoadableRetryCount(dataType: Int): Int =
                if (isGrowingDownloadKey(downloadKey)) 3
                else super.getMinimumLoadableRetryCount(dataType)
        }

    private fun isGrowingDownloadKey(key: String): Boolean =
        DownloadingFilesRegistry.completedUri(key) == null &&
            (DownloadingFilesRegistry.isDownloading(key) ||
                PendingDownloadFormat.isPending(key.substringAfterLast('/')))

    private fun isGrowingDownloadUri(uri: Uri): Boolean {
        val key = downloadRegistryKey(uri)
        return isGrowingDownloadKey(key) ||
            (DownloadingFilesRegistry.completedUri(key) == null &&
                PendingDownloadFormat.isPending(uri.lastPathSegment.orEmpty()))
    }

    /**
     * File observers, MediaStore and the five-second library scan do not fire
     * at exactly the same time. Hold an opened growing source through a short
     * gap before its first READY state, but never mask a renderer problem after
     * a frame has already become playable.
     */
    private fun shouldKeepAwaitingGrowingData(uri: Uri): Boolean {
        if (DownloadingFilesRegistry.isActivelyGrowing(downloadRegistryKey(uri))) {
            growingPlaybackGraceUntilMs = System.currentTimeMillis() + GROWING_INITIAL_GRACE_MS
            return true
        }
        return System.currentTimeMillis() < growingPlaybackGraceUntilMs
    }

    private fun downloadRegistryKey(uri: Uri): String =
        if (uri.scheme == "file") uri.path.orEmpty() else uri.toString()

    private fun isLikelyStreamableGrowingContainer(uri: Uri): Boolean {
        val name = uri.lastPathSegment?.lowercase().orEmpty()
        val withoutDownloadSuffix = PendingDownloadFormat.originalName(name)
        return withoutDownloadSuffix.endsWith(".mkv") ||
            withoutDownloadSuffix.endsWith(".webm") ||
            withoutDownloadSuffix.endsWith(".ts")
    }

    private fun incompleteDownloadMessage(uri: Uri): String {
        return if (isLikelyStreamableGrowingContainer(uri)) {
            "This download has not received playable data yet. Keep the download running and try again shortly."
        } else {
            "This video is still downloading. Standard MP4 files need their final metadata before they can play; wait for the download to finish."
        }
    }

    fun play() {
        desiredAutoPlay = true
        if (_usesVlc.value) {
            if (playbackEnded) {
                vlcActiveUri?.let { prepareMedia(it, 0L, autoPlay = true, mediaMimeType = activeMimeType) }
            } else vlcPlayback?.play()
            return
        }
        exoPlayer?.let { player ->
            if (playbackEnded || player.playbackState == Player.STATE_ENDED) {
                playbackEnded = false
                seekTo(0L)
            }
            player.play()
        }
    }

    fun pause() {
        desiredAutoPlay = false
        if (_usesVlc.value) { vlcPlayback?.pause(); return }
        exoPlayer?.pause()
    }

    /** Pauses for a temporary app switch without removing the active stream. */
    fun pauseForAppBackground() {
        if (_usesVlc.value) {
            resumeAfterForeground = _isPlaying.value
            desiredAutoPlay = false
            vlcPlayback?.pause()
            return
        }
        val player = exoPlayer ?: return
        resumeAfterForeground = player.playWhenReady || player.isPlaying
        desiredAutoPlay = false
        player.pause()
        _isPlaying.value = false
    }

    /** Resumes only a stream that the activity lifecycle paused. */
    fun resumeAfterAppForeground() {
        if (_usesVlc.value) {
            if (resumeAfterForeground && _playerError.value == null) play()
            resumeAfterForeground = false
            return
        }
        val player = exoPlayer ?: return
        if (HiMediaSessionService.sharedPlayer != null && HiMediaSessionService.sharedPlayer !== player) {
            resumeAfterForeground = false
            return
        }
        if (resumeAfterForeground && _playerError.value == null && player.currentMediaItem != null) {
            play()
        }
        resumeAfterForeground = false
    }

    /** Prevents playback from resuming after leaving the player screen. */
    fun stopForNavigation() {
        pendingVideoPreparation = null
        surfaceWaitTimeout?.let(mainHandler::removeCallbacks)
        routingRevision++
        externalSubtitleReadJob?.cancel()
        routingTimeout?.let(mainHandler::removeCallbacks)
        if (_usesVlc.value) { vlcPlayback?.stop(); _usesVlc.value = false }
        vlcActiveUri = null
        vlcExoFallbackUri = null
        growingRetryJob?.cancel()
        growingRetryJob = null
        activeGrowingUri = null
        growingRetryAttempts = 0
        growingPlaybackReachedReady = false
        growingPlaybackGraceUntilMs = 0L
        resumeAfterForeground = false
        setBackgroundPlay(false)
        exoPlayer?.let {
            it.playWhenReady = false
            it.stop()
        }
        _isPlaying.value = false
        _isBuffering.value = false
    }

    fun togglePlayPause() {
        if (playbackEnded || (!_usesVlc.value && exoPlayer?.playbackState == Player.STATE_ENDED)) {
            play()
            return
        }
        val playingRequested = if (_usesVlc.value) desiredAutoPlay else exoPlayer?.playWhenReady == true
        if (playingRequested) pause() else play()
    }

    fun clampSeekTarget(positionMs: Long): Long {
        if (_usesVlc.value) {
            val known = _durationMs.value
            return if (known > 0L) positionMs.coerceIn(0L, known) else positionMs.coerceAtLeast(0L)
        }
        val player = exoPlayer ?: return positionMs.coerceAtLeast(0L)
        // Clamp only to a known duration. Before the duration is published (and
        // for live streams) clamping to 0..0 would incorrectly send every skip to
        // the start. Growing files additionally stop at the safely buffered edge.
        val known = player.duration.takeIf { it != C.TIME_UNSET && it > 0L }
            ?: _durationMs.value.takeIf { it > 0L }
        val durationBounded = if (known != null) positionMs.coerceIn(0L, known) else positionMs.coerceAtLeast(0L)
        val uri = player.currentMediaItem?.localConfiguration?.uri
        val growing = uri?.let(::isGrowingDownloadUri) == true
        // The Matroska map resolves the target to a downloaded keyframe's REAL
        // Cluster offset. Decoder-buffer length is not the downloaded seek frontier.
        if (growing && PendingDownloadFormat.originalName(uri?.lastPathSegment.orEmpty()).let {
                it.endsWith(".mkv", true) || it.endsWith(".webm", true) || it.endsWith(".mk3d", true)
            }) return positionMs.coerceIn(0L, maxOf(downloadedSeekEndMs, player.currentPosition))
        return GrowingSeekBounds.clamp(durationBounded, player.currentPosition, player.bufferedPosition, growing)
    }

    fun seekTo(positionMs: Long) {
        if (_usesVlc.value) {
            val target = clampSeekTarget(positionMs)
            if (playbackEnded) {
                vlcActiveUri?.let {
                    prepareMedia(it, target, autoPlay = desiredAutoPlay, mediaMimeType = activeMimeType)
                }
            } else vlcPlayback?.seekTo(target)
            return
        }
        val player = exoPlayer ?: return
        val clamped = clampSeekTarget(positionMs)
        playbackEnded = false
        val seek = decodedSeekRevision.incrementAndGet()
        val expectVideo = !_isBackgroundPlayActive.value && activeMimeType?.startsWith("audio/") != true
        pendingDecodedSeekRevision = seek.takeIf { expectVideo }
        player.seekTo(clamped)
        if (expectVideo) {
            // A processed-frame PTS may be relative to a newly configured GL
            // input stream. It is not necessarily movie time. Confirm the core
            // seek/flush first, then wait for a rendered frame and read the
            // authoritative Player timeline on main. Never compare raw PTS to
            // the requested movie position or advertise a request as decoded.
            player.createMessage { _, _ -> seekDecoderAcknowledgement = seek }
                .setType(0).send()
        }
        requestPausedVideoFrame(player)
    }

    private fun requestPausedVideoFrame(player: ExoPlayer) {
        if (player.playWhenReady || player.playbackState == Player.STATE_IDLE) return
        // The Media3 GL sink may retain RELEASE_FIRST_FRAME_WHEN_STARTED after
        // a paused seek/rebind. Explicitly allow the new frame without starting
        // audio or momentarily resuming playback. Renderer access runs on its
        // playback looper, never on Compose/main or a native event callback.
        repeat(player.rendererCount) { index ->
            if (player.getRendererType(index) == C.TRACK_TYPE_VIDEO) {
                val renderer = player.getRenderer(index)
                player.createMessage { _, _ -> renderer.enableMayRenderStartOfStream() }
                    .setType(0).send()
            }
        }
    }

    fun seekRelative(deltaMs: Long) {
        val current = if (_usesVlc.value) _currentPositionMs.value else exoPlayer?.currentPosition ?: 0L
        seekTo(current + deltaMs)
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        if (_usesVlc.value) vlcPlayback?.setSpeed(speed)
        else exoPlayer?.playbackParameters = PlaybackParameters(speed)
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
        if (enabled) pendingVideoPreparation?.invoke()
        val selector = trackSelector ?: return
        selector.setParameters(selector.buildUponParameters()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, enabled))
    }

    fun setAbRepeatA() {
        _abRepeatA.value = if (_usesVlc.value) _currentPositionMs.value else exoPlayer?.currentPosition ?: 0L
    }

    fun setAbRepeatB() {
        val current = if (_usesVlc.value) _currentPositionMs.value else exoPlayer?.currentPosition ?: 0L
        if (_abRepeatA.value != null && current > _abRepeatA.value!!) {
            _abRepeatB.value = current
        }
    }

    fun clearAbRepeat() {
        _abRepeatA.value = null
        _abRepeatB.value = null
    }

    /**
     * A phone often has no TrueHD/DTS hardware decoder even though another
     * embedded track, such as E-AC3/DDP, is playable. When MediaCodec rejects
     * the selected track, silently select the best supported alternative and
     * resume at the same position instead of leaving playback on an error UI.
     */
    private fun isUnsupportedVideoRendererFailure(details: String): Boolean =
        details.contains("mediacodecvideorenderer") &&
            (details.contains("no_exceeds_capabilities") ||
                details.contains("exceeds capabilities") ||
                details.contains("format_supported=no"))

    /**
     * Video codec capability is owned by the device, not by the file source.
     * Libgav1 is loaded automatically for AV1 when hardware support is absent.
     * Reaching this branch for AV1 therefore means the software fallback itself
     * could not sustain or initialize the requested profile (commonly 8K/10-bit
     * HDR on a memory-constrained phone), not merely that hardware is absent.
     */
    private fun unsupportedVideoRendererMessage(details: String): String {
        val codec = when {
            details.contains("video/av01") -> "AV1"
            details.contains("video/hevc") || details.contains("video/h265") -> "HEVC"
            details.contains("video/vp9") -> "VP9"
            else -> "this video format"
        }
        val resolution = when {
            details.contains("[7680, 4320") || details.contains("7680,4320") -> "8K"
            details.contains("[3840, 2160") || details.contains("3840,2160") -> "4K"
            else -> "high-resolution"
        }
        return if (codec == "AV1") {
            "Hardware AV1 decoding is unavailable and the automatic software fallback " +
                "could not play this $resolution profile. Try a lower-resolution or SDR copy."
        } else {
            "This device cannot decode $resolution $codec video at this profile. " +
                "Try a lower-resolution copy or a device with $codec hardware decoding."
        }
    }

    private fun recoverWithPlayableAudioTrack(details: String): Boolean {
        val player = exoPlayer ?: return false
        val selector = trackSelector ?: return false
        val tracks = player.currentTracks
        var fallbackGroup: TrackGroup? = null
        var fallbackGroupIndex = -1
        var fallbackTrackIndex = -1
        var fallbackPriority = Int.MAX_VALUE
        val failedTrueHd = details.contains("audio/true-hd")
        val failedDts = details.contains("audio/vnd.dts") || details.contains("audio/dts")

        for ((groupIndex, group) in tracks.groups.withIndex()) {
            if (group.type != C.TRACK_TYPE_AUDIO) continue
            for (trackIndex in 0 until group.length) {
                val fallbackKey = groupIndex to trackIndex
                if (!group.isTrackSupported(trackIndex) || group.isTrackSelected(trackIndex) ||
                    fallbackKey in attemptedAudioFallbackTracks
                ) continue

                val sampleMimeType = group.getTrackFormat(trackIndex).sampleMimeType.orEmpty()
                    .lowercase(Locale.ROOT)
                if ((failedTrueHd && sampleMimeType.contains("audio/true-hd")) ||
                    (failedDts && (sampleMimeType.contains("audio/vnd.dts") || sampleMimeType.contains("audio/dts")))
                ) continue

                val priority = AudioRendererFallbackPolicy.fallbackPriority(sampleMimeType)
                if (priority < fallbackPriority) {
                    fallbackGroup = group.mediaTrackGroup
                    fallbackGroupIndex = groupIndex
                    fallbackTrackIndex = trackIndex
                    fallbackPriority = priority
                }
            }
        }

        val fallback = fallbackGroup ?: return false
        val uri = player.currentMediaItem?.localConfiguration?.uri ?: return false
        val mimeType = player.currentMediaItem?.localConfiguration?.mimeType
        val position = player.currentPosition.coerceAtLeast(0L)
        attemptedAudioFallbackTracks.add(fallbackGroupIndex to fallbackTrackIndex)
        selector.parameters = selector.parameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(fallback, listOf(fallbackTrackIndex)))
            .build()
        audioSelectionRecoveryParameters = null
        audioSelectionRecoveryUri = null
        coroutineScope.launch(Dispatchers.Main) {
            prepareMedia(
                uri = uri,
                startPositionMs = position,
                autoPlay = true,
                mediaMimeType = mimeType,
                audioFallbackRecovery = true,
            )
        }
        return true
    }

    fun setMuted(muted: Boolean) {
        rememberedMuted = muted
        if (_usesVlc.value) vlcPlayback?.setMuted(muted, _volumeBoostPercent.value)
        exoPlayer?.volume = if (muted) 0f else 1f
    }

    fun setRememberedTrackPreferences(audioLanguage: String?, subtitleLanguage: String?, subtitlesEnabled: Boolean) {
        rememberedAudioLanguage = audioLanguage
        rememberedSubtitleLanguage = subtitleLanguage
        rememberedSubtitlesEnabled = subtitlesEnabled
        applyRememberedTrackPreferences(clearOverrides = false)
    }

    private fun applyRememberedTrackPreferences(clearOverrides: Boolean) {
        val selector = trackSelector ?: return
        val builder = selector.parameters.buildUpon()
            .setPreferredAudioLanguage(rememberedAudioLanguage)
            .setPreferredTextLanguage(rememberedSubtitleLanguage)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !rememberedSubtitlesEnabled)
            .setTrackTypeDisabled(C.TRACK_TYPE_IMAGE, !rememberedSubtitlesEnabled)
        if (clearOverrides) {
            builder.clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .clearOverridesOfType(C.TRACK_TYPE_TEXT).clearOverridesOfType(C.TRACK_TYPE_IMAGE)
        }
        selector.parameters = builder.build()
    }

    fun selectAudioTrack(track: VideoTrackInfo) {
        if (!track.isSupported) return
        if (_usesVlc.value) { vlcPlayback?.selectAudioTrack(track); return }
        audioFallbackAttemptedUri = exoPlayer?.currentMediaItem?.localConfiguration?.uri
        attemptedAudioFallbackTracks.clear()
        rememberedAudioLanguage = com.example.data.normalizeTrackLanguage(track.language)
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
                    audioSelectionRecoveryParameters = selector.parameters
                    audioSelectionRecoveryUri = exoPlayer?.currentMediaItem?.localConfiguration?.uri
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
        if (_usesVlc.value) { vlcPlayback?.selectSubtitleTrack(track); return }
        rememberedSubtitlesEnabled = track != null
        if (track != null) rememberedSubtitleLanguage = com.example.data.normalizeTrackLanguage(track.language)
        val selector = trackSelector ?: return
        val player = exoPlayer ?: return
        val builder = selector.parameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .clearOverridesOfType(C.TRACK_TYPE_IMAGE)
        var updatedParameters: DefaultTrackSelector.Parameters? = null
        if (track == null) {
            // Captions in movie files are commonly text (SRT/ASS/WebVTT), but
            // Blu-ray PGS/VobSub captions can be reported as image tracks.
            // Disable both when the user selects "Subtitles Off".
            updatedParameters = builder
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .setTrackTypeDisabled(C.TRACK_TYPE_IMAGE, true)
                .build()
        } else {
            val currentTracks = player.currentTracks
            for ((groupIndex, group) in currentTracks.groups.withIndex()) {
                if (group.type == track.trackType && groupIndex == track.trackGroupIndex) {
                    val mediaTrackGroup = group.mediaTrackGroup
                    if (mediaTrackGroup.length > track.trackIndex) {
                        val override = TrackSelectionOverride(mediaTrackGroup, listOf(track.trackIndex))
                        updatedParameters = builder
                            // Only one caption type should be active at a time.
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, track.trackType != C.TRACK_TYPE_TEXT)
                            .setTrackTypeDisabled(C.TRACK_TYPE_IMAGE, track.trackType != C.TRACK_TYPE_IMAGE)
                            .setOverrideForType(override)
                            .build()
                    }
                    break
                }
            }
        }
        updatedParameters?.let { parameters ->
            selector.parameters = parameters
            // Apply the exact same object to the player. This is important for
            // caption renderers because changing only the selector can leave a
            // previously disabled text/image renderer disabled until reprepare.
            player.trackSelectionParameters = parameters
        }
        refreshAvailableTracks()
    }

    /** Re-reads Media3's current track groups for a newly opened CC panel. */
    fun refreshAvailableTracks() {
        if (_usesVlc.value) { vlcPlayback?.refreshTracks(); return }
        updateAvailableTracks(exoPlayer?.currentTracks)
        updateVideoOutputInfo()
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
                    val mime = format.sampleMimeType ?: "audio"
                    val fullLabel = displayTrackLanguage(
                        format.language,
                        format.label,
                        "Audio Track ${audioList.size + 1}"
                    )
                    audioList.add(
                        VideoTrackInfo(
                            id = format.id ?: "$groupIndex-$trackIndex",
                            label = fullLabel,
                            language = format.language,
                            mimeType = mime,
                            channelCount = format.channelCount,
                            isSelected = group.isTrackSelected(trackIndex),
                            isSupported = group.isTrackSupported(trackIndex),
                            trackType = group.type,
                            trackGroupIndex = groupIndex,
                            trackIndex = trackIndex
                        )
                    )
                }
            } else if (group.type == C.TRACK_TYPE_TEXT || group.type == C.TRACK_TYPE_IMAGE) {
                for (trackIndex in 0 until mediaTrackGroup.length) {
                    val format = mediaTrackGroup.getFormat(trackIndex)
                    val mime = format.sampleMimeType ?: "text"
                    val fullLabel = displayTrackLanguage(
                        format.language,
                        format.label,
                        "Subtitle ${subtitleList.size + 1}"
                    )
                    subtitleList.add(
                        VideoTrackInfo(
                            id = format.id ?: "$groupIndex-$trackIndex",
                            label = fullLabel,
                            language = format.language,
                            mimeType = mime,
                            isSelected = group.isTrackSelected(trackIndex),
                            isSupported = group.isTrackSupported(trackIndex),
                            trackType = group.type,
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

    private fun restoreBackendTrackChoices() {
        pendingBackendAudioIndex?.let { index ->
            _availableAudioTracks.value.getOrNull(index)?.let { track ->
                pendingBackendAudioIndex = null
                selectAudioTrack(track)
            }
        }
        pendingBackendSubtitleIndex?.let { index ->
            if (index < 0 || _availableSubtitleTracks.value.size > index) {
                pendingBackendSubtitleIndex = null
                selectSubtitleTrack(_availableSubtitleTracks.value.getOrNull(index))
            }
        }
    }

    private fun updateVideoOutputInfo() {
        val format = exoPlayer?.videoFormat
        _isHdrContent.value = isHdrVideoFormat(format)
        _isVideoColorMetadataKnown.value = hasKnownVideoColorMetadata(format)
        _is4kContent.value = (format?.width ?: 0) >= 3840 || (format?.height ?: 0) >= 2160
    }

    private fun updateTelemetry() {
        val player = exoPlayer ?: return
        val videoFormat = player.videoFormat
        val audioFormat = player.audioFormat
        val width = videoFormat?.width?.coerceAtLeast(0) ?: 0
        val height = videoFormat?.height?.coerceAtLeast(0) ?: 0
        val fps = videoFormat?.frameRate?.takeIf { it > 0f } ?: 0f
        val bitrate = videoFormat?.bitrate?.takeIf { it > 0 }?.div(1_000_000f) ?: 0f
        val mime = videoFormat?.sampleMimeType.orEmpty()
        val dropped = player.videoDecoderCounters?.droppedBufferCount ?: 0
        val codecName = videoFormat?.codecs?.takeIf(String::isNotBlank) ?: when {
            mime.contains("hevc") || mime.contains("h265") -> "HEVC"
            mime.contains("av01") || mime.contains("av1") -> "AV1"
            mime.contains("vp9") -> "VP9"
            mime.contains("avc") || mime.contains("h264") -> "AVC"
            mime.isNotBlank() -> mime
            else -> "Unknown decoder"
        }
        val channelLabel = audioFormat?.channelCount?.takeIf { it > 0 }?.let { " · ${it} ch" }.orEmpty()
        val actualAudio = audioFormat?.label?.takeIf(String::isNotBlank)
            ?: audioFormat?.codecs?.takeIf(String::isNotBlank)
            ?: audioFormat?.sampleMimeType?.takeIf(String::isNotBlank)
            ?: "Unknown audio"

        _telemetry.value = DecoderTelemetry(
            codecName = codecName,
            resolution = if (width > 0 && height > 0) "${width}x${height}" else "Unknown",
            fps = fps,
            bitrateMbps = bitrate,
            droppedFrames = dropped,
            colorSpace = when {
                isDolbyVisionFormat(videoFormat) -> "Dolby Vision"
                isHdrVideoFormat(videoFormat) -> "HDR"
                hasKnownVideoColorMetadata(videoFormat) -> "SDR"
                else -> "Unknown"
            },
            audioFormat = actualAudio + channelLabel,
        )
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = coroutineScope.launch(Dispatchers.Main) {
            while (isActive) {
                if (_usesVlc.value) vlcPlayback?.publish()
                else exoPlayer?.let { player ->
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    if (pendingDecodedSeekRevision == null && player.playbackState == Player.STATE_READY) _currentPositionMs.value = pos
                    val buffered = player.bufferedPosition.coerceAtLeast(0L)
                    _bufferedPositionMs.value = buffered
                    val reportedDuration = player.duration
                    _durationMs.value = if (
                        reportedDuration == C.TIME_UNSET &&
                        player.currentMediaItem?.localConfiguration?.uri?.let(::isGrowingDownloadUri) == true
                    ) {
                        maxOf(pos, buffered)
                    } else {
                        reportedDuration.coerceAtLeast(0L)
                    }

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

    fun release(forReplacement: Boolean = false) {
        pendingVideoPreparation = null
        surfaceWaitTimeout?.let(mainHandler::removeCallbacks)
        if (!forReplacement) { routingRevision++; routingTimeout?.let(mainHandler::removeCallbacks); vlcPlayback?.release(); vlcPlayback = null; _usesVlc.value = false }
        if (!forReplacement) HiMediaSessionService.detachPlayer(exoPlayer)
        growingRetryJob?.cancel()
        growingRetryJob = null
        activeGrowingUri = null
        growingRetryAttempts = 0
        growingPlaybackReachedReady = false
        growingPlaybackGraceUntilMs = 0L
        resumeAfterForeground = false
        progressJob?.cancel()
        progressJob = null
        loudnessEnhancer?.release()
        loudnessEnhancer = null
        equalizer?.release()
        equalizer = null
        _playerInstance.value = null
        exoPlayer?.removeListener(playerListener)
        exoPlayer?.release()
        exoPlayer = null
    }
}
