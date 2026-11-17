package com.example.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.view.SurfaceView
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import com.example.model.PendingDownloadFormat
import com.example.model.VideoTrackInfo
import com.example.playback.GrowingMkvClusterIndex
import com.example.playback.createGrowingMkvClusterIndex
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IVLCVout
import java.io.File

/**
 * Local Matroska playback only. HTTP, HLS, DASH, and remote account URLs remain on Media3.
 * LibVLC's FFmpeg codec modules decode lossless TrueHD/DTS-HD when present in the
 * distributed LibVLC binary, and its direct SurfaceView retains the platform HDR path.
 *
 * A truncated Matroska file has no guaranteed timeline or seek index: VLC/avformat can
 * seek to clusters it has indexed, but we must not manufacture an inaccurate 0:00 seek.
 * Retain the Media3 GrowingMkvSeekableExtractor as the separate fallback path.
 */
internal fun vlcColorAdjustOptions(
    preset: ColorPreset,
    enhancementEnabled: Boolean,
): List<String> {
    if (!shouldApplyColorPreset(preset, enhancementEnabled)) return emptyList()
    val gamma = ((preset.red + preset.green + preset.blue) / 3f).coerceIn(0.25f, 4f)
    val hue = ((preset.blue - preset.red) * 90f).coerceIn(-180f, 180f)
    return listOf(
        ":video-filter=adjust",
        ":brightness=${preset.brightness.coerceIn(0.5f, 2f)}",
        ":contrast=${preset.contrast.coerceIn(0f, 2f)}",
        ":saturation=${(preset.saturation * preset.vibrance).coerceIn(0f, 3f)}",
        ":gamma=$gamma",
        ":hue=$hue",
    )
}

@androidx.annotation.OptIn(UnstableApi::class)
class VlcPlaybackEngine(
    context: Context,
    private val onUpdate: (State) -> Unit,
    private val onTracks: (List<VideoTrackInfo>, List<VideoTrackInfo>) -> Unit,
    private val onVideoLayout: (Int, Int) -> Unit,
    private val onFirstFrame: () -> Unit,
    private val onSeekUnavailable: (Long) -> Unit,
    private val onEnd: () -> Unit,
    private val onError: (String) -> Unit,
) {
    data class State(
        val playing: Boolean,
        val buffering: Boolean,
        val positionMs: Long,
        val durationMs: Long,
        val bufferedPositionMs: Long,
        val seekable: Boolean,
    )

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val vlc = LibVLC(appContext, arrayListOf(
        "--no-video-title-show", "--avcodec-hw=any", "--file-caching=250",
    ))
    private val player = MediaPlayer(vlc)
    private val resolver = appContext.contentResolver
    private var openDescriptor: ParcelFileDescriptor? = null
    private var currentUri: Uri? = null
    private var attachedView: SurfaceView? = null
    private var growing = false
    private var retryWithAvformat = false
    private var wantsPlayback = false
    private var pendingSeekMs = 0L
    private var lastGoodTimeMs = 0L
    private var lastReportedTracks: Pair<List<VideoTrackInfo>, List<VideoTrackInfo>>? = null
    private var isBuffering = false
    private var released = false
    private var muted = false
    private var boost = 0
    private var speed = 1f
    private var audioDelayUs = 0L
    private var subtitleDelayUs = 0L
    private var retryPending = false
    private var lastSize = -1L
    private var stalledGrowthRetries = 0
    private var growingClusterIndex: GrowingMkvClusterIndex? = null
    private var colorPreset: ColorPreset = ColorPresets.HDR_DISABLED
    private var colorEnhancementEnabled = false

    private val resumeGrowing = Runnable {
        retryPending = false
        val uri = currentUri ?: return@Runnable
        if (!growing) {
            onEnd()
            return@Runnable
        }
        val stillExpectedToGrow = isPotentiallyGrowing(uri)
        val size = sourceSize(uri)
        if ((size < 0L && stillExpectedToGrow) || size > lastSize) {
            lastSize = size
            stalledGrowthRetries = 0
            // Keep the last decoded timestamp, not VLC's 0:00 EndReached reset.
            open(uri, lastGoodTimeMs, wantsPlayback, true)
        } else if (stillExpectedToGrow && stalledGrowthRetries++ < MAX_STALLED_GROWTH_RETRIES) {
            scheduleGrowingRetry()
        } else {
            onEnd()
        }
    }

    init {
        player.setEventListener { event ->
            main.post {
                if (released) return@post
                when (event.type) {
                    MediaPlayer.Event.Opening, MediaPlayer.Event.Buffering -> isBuffering = true
                    MediaPlayer.Event.Playing -> {
                        isBuffering = false
                        if (pendingSeekMs > 0L) {
                            player.setTime(pendingSeekMs)
                        }
                        player.setRate(speed)
                        player.setVolume(if (muted) 0 else 100 + boost)
                        player.setAudioDelay(audioDelayUs)
                        player.setSpuDelay(subtitleDelayUs)
                        if (!wantsPlayback) player.pause()
                        refreshTracks()
                    }
                    MediaPlayer.Event.Paused -> isBuffering = false
                    MediaPlayer.Event.Vout -> {
                        onFirstFrame()
                        refreshTracks()
                    }
                    MediaPlayer.Event.ESAdded, MediaPlayer.Event.ESSelected -> refreshTracks()
                    MediaPlayer.Event.EncounteredError -> {
                        val uri = currentUri
                        if (uri != null && !retryWithAvformat) {
                            // Alternate FFmpeg demux can expose the HEVC base layer
                            // of a hybrid DV remux on devices rejecting DV signalling.
                            // It is a recovery attempt, NOT a guaranteed DV conversion.
                            retryWithAvformat = true
                            open(uri, lastGoodTimeMs, wantsPlayback, growing)
                        } else if (uri != null && growing) {
                            scheduleGrowingRetry()
                        } else {
                            isBuffering = false
                            onError("LibVLC could not decode this local Matroska file or its HDR video track.")
                        }
                    }
                    MediaPlayer.Event.EndReached -> {
                        isBuffering = false
                        if (growing) scheduleGrowingRetry() else onEnd()
                    }
                }
                publish()
            }
        }
    }

    private fun registryKey(uri: Uri): String =
        if (uri.scheme == "file") uri.path.orEmpty() else uri.toString()

    private fun isPotentiallyGrowing(uri: Uri): Boolean =
        DownloadingFilesRegistry.completedUri(registryKey(uri)) == null &&
            (DownloadingFilesRegistry.isDownloading(registryKey(uri)) ||
                PendingDownloadFormat.isPending(uri.lastPathSegment.orEmpty()))

    private fun sourceSize(uri: Uri): Long = runCatching {
        if (uri.scheme == "file") {
            File(uri.path.orEmpty()).length()
        } else {
            resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                descriptor.statSize
            } ?: -1L
        }
    }.getOrDefault(-1L)

    /** All mutating operations are called from the engine's main/UI thread. */
    fun open(uri: Uri, startPositionMs: Long, autoPlay: Boolean, isGrowing: Boolean) {
        if (released) return
        main.removeCallbacks(resumeGrowing)
        retryPending = false
        val changed = currentUri != uri
        if (changed) {
            retryWithAvformat = false
            growingClusterIndex = null
            stalledGrowthRetries = 0
        }
        currentUri = uri
        growing = isGrowing
        if (isGrowing && growingClusterIndex == null) {
            growingClusterIndex = createGrowingMkvClusterIndex(
                context = appContext,
                uri = uri,
                isDownloading = { growing && isPotentiallyGrowing(uri) },
            )
        }
        lastSize = maxOf(lastSize, sourceSize(uri))
        wantsPlayback = autoPlay
        pendingSeekMs = startPositionMs.coerceAtLeast(0L)
        lastGoodTimeMs = pendingSeekMs
        isBuffering = true
        lastReportedTracks = null
        onTracks(emptyList(), emptyList())
        player.stop()
        openDescriptor?.close()
        openDescriptor = null
        try {
            val media = if (uri.scheme == "content") {
                val fd = resolver.openFileDescriptor(uri, "r")
                    ?: throw IllegalStateException("Cannot open content URI")
                openDescriptor = fd
                Media(vlc, fd.fileDescriptor)
            } else {
                Media(vlc, uri)
            }
            try {
                media.setHWDecoderEnabled(true, false)
                media.addOption(":file-caching=250")
                media.addOption(":no-video-title-show")
                if (retryWithAvformat) media.addOption(":demux=avformat")
                // An explicit grade opts out of untouched native HDR. Original HDR
                // never installs this filter, preserving the 10-bit SurfaceView path.
                vlcColorAdjustOptions(colorPreset, colorEnhancementEnabled)
                    .forEach { option -> media.addOption(option) }
                // FFmpeg demuxing works with downloaded clusters, not end-of-file Cues.
                // Do not set :start-time=0 or restart after every seek.
                player.media = media
            } finally {
                media.release()
            }
            if (attachedView != null) player.play()
            publish()
        } catch (error: Exception) {
            isBuffering = false
            onError("Cannot open local video: ${error.localizedMessage ?: error.javaClass.simpleName}")
        }
    }

    fun attachSurface(view: SurfaceView) {
        if (released || attachedView === view || !view.holder.surface.isValid) return
        detachSurface(attachedView)
        try {
            val vout = player.vlcVout
            vout.setVideoView(view)
            vout.attachViews(object : IVLCVout.OnNewVideoLayoutListener {
                override fun onNewVideoLayout(
                    vlcVout: IVLCVout, width: Int, height: Int,
                    visibleWidth: Int, visibleHeight: Int, sarNum: Int, sarDen: Int,
                ) {
                    main.post {
                        if (!released && attachedView === view) onVideoLayout(
                            (if (visibleWidth > 0) visibleWidth else width).coerceAtLeast(0),
                            (if (visibleHeight > 0) visibleHeight else height).coerceAtLeast(0),
                        )
                    }
                }
            })
            attachedView = view
            vout.setWindowSize(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1))
            if (currentUri != null) player.play()
        } catch (_: RuntimeException) {
            attachedView = null
            runCatching { player.vlcVout.detachViews() }
            main.post { if (!released) onError("LibVLC could not attach the video surface.") }
        }
    }

    fun updateWindowSize(view: SurfaceView) {
        if (attachedView === view && !released) {
            player.vlcVout.setWindowSize(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1))
        }
    }

    fun detachSurface(view: SurfaceView?) {
        if (view == null || attachedView !== view || released) return
        attachedView = null
        runCatching { player.vlcVout.detachViews() }
    }

    fun play() {
        wantsPlayback = true
        if (attachedView != null) player.play()
        publish()
    }

    fun pause() {
        wantsPlayback = false
        player.pause()
        publish()
    }

    fun togglePlayPause() { if (wantsPlayback) pause() else play() }

    fun stop() {
        wantsPlayback = false
        currentUri = null
        pendingSeekMs = 0L
        growingClusterIndex = null
        stalledGrowthRetries = 0
        main.removeCallbacks(resumeGrowing)
        retryPending = false
        player.stop()
        openDescriptor?.close()
        openDescriptor = null
        isBuffering = false
    }

    /** VLC time is milliseconds; never fake a successful seek at zero. */
    fun seekTo(positionMs: Long) {
        val requested = positionMs.coerceAtLeast(0L)
        val target = if (growing) {
            val cluster = growingClusterIndex?.seekPointAtOrBefore(requested * 1_000L)
            if (cluster == null) {
                onSeekUnavailable(requested)
                return
            }
            cluster.timeUs / 1_000L
        } else {
            requested
        }
        if (player.isSeekable) {
            player.setTime(target)
            pendingSeekMs = 0L
        } else if (currentUri != null) {
            // Use the existing Media3 growing-MKV cluster index instead of
            // pretending that an unsupported VLC seek succeeded at 0:00.
            onSeekUnavailable(requested)
            return
        } else {
            pendingSeekMs = target
        }
        publish()
    }

    fun setColorPreset(preset: ColorPreset, enhancementEnabled: Boolean, reopen: Boolean = true) {
        val changed = colorPreset != preset || colorEnhancementEnabled != enhancementEnabled
        colorPreset = preset
        colorEnhancementEnabled = enhancementEnabled
        val uri = currentUri
        if (changed && reopen && uri != null) {
            val resumePosition = maxOf(lastGoodTimeMs, player.time.coerceAtLeast(0L))
            open(uri, resumePosition, wantsPlayback, growing)
        }
    }

    fun setSpeed(value: Float) {
        speed = value.coerceIn(0.25f, 4f)
        player.setRate(speed)
    }

    fun setMuted(value: Boolean, boostPercent: Int) {
        muted = value
        boost = boostPercent.coerceIn(0, 100)
        player.setVolume(if (muted) 0 else 100 + boost)
    }

    fun setDelays(audioMs: Long, subtitleMs: Long) {
        audioDelayUs = audioMs * 1000L
        subtitleDelayUs = subtitleMs * 1000L
        player.setAudioDelay(audioDelayUs)
        player.setSpuDelay(subtitleDelayUs)
    }

    fun selectAudioTrack(info: VideoTrackInfo) {
        player.setAudioTrack(info.trackIndex)
        refreshTracks()
    }

    fun selectSubtitleTrack(info: VideoTrackInfo?) {
        player.setSpuTrack(info?.trackIndex ?: -1)
        refreshTracks()
    }

    fun refreshTracks() {
        if (released) return
        val audio = player.audioTracks.orEmpty()
            .filter { it.id >= 0 }
            .map { track ->
                VideoTrackInfo(
                    id = "vlc-audio-${track.id}", label = track.name ?: "Audio ${track.id}",
                    language = null, mimeType = "audio/vlc", isSelected = player.audioTrack == track.id,
                    isSupported = true, trackType = C.TRACK_TYPE_AUDIO,
                    trackGroupIndex = 0, trackIndex = track.id,
                )
            }
        val subtitles = player.spuTracks.orEmpty()
            .filter { it.id >= 0 }
            .map { track ->
                VideoTrackInfo(
                    id = "vlc-sub-${track.id}", label = track.name ?: "Subtitles ${track.id}",
                    language = null, mimeType = "text/vlc", isSelected = player.spuTrack == track.id,
                    isSupported = true, trackType = C.TRACK_TYPE_TEXT,
                    trackGroupIndex = 0, trackIndex = track.id,
                )
            }
        val result = audio to subtitles
        if (result != lastReportedTracks) {
            lastReportedTracks = result
            onTracks(audio, subtitles)
        }
    }

    fun snapshot(): State {
        val rawPosition = player.time.coerceAtLeast(0L)
        val position = when {
            rawPosition > 0L -> rawPosition
            pendingSeekMs > 0L -> pendingSeekMs
            growing && lastGoodTimeMs > 0L -> lastGoodTimeMs
            else -> rawPosition
        }
        val indexedPoints = growingClusterIndex?.scanAvailable().orEmpty()
        val indexedEnd = indexedPoints.lastOrNull()?.timeUs?.div(1_000L) ?: 0L
        val duration = player.length.coerceAtLeast(0L)
        return State(
            playing = player.isPlaying,
            buffering = isBuffering,
            positionMs = position,
            durationMs = maxOf(duration, indexedEnd),
            bufferedPositionMs = maxOf(position, indexedEnd),
            seekable = player.isSeekable || indexedPoints.size >= 2,
        )
    }

    fun publish() {
        if (released) return
        val state = snapshot()
        if (state.positionMs > 0L) lastGoodTimeMs = state.positionMs
        if (pendingSeekMs > 0L) {
            val target = pendingSeekMs
            if (state.seekable) {
                pendingSeekMs = 0L
                player.setTime(target)
            } else if (!state.buffering && player.isPlaying) {
                pendingSeekMs = 0L
                onSeekUnavailable(target)
                return
            }
        }
        onUpdate(state)
    }

    private fun scheduleGrowingRetry() {
        if (retryPending || released) return
        retryPending = true
        main.postDelayed(resumeGrowing, 1200L)
    }

    fun release() {
        if (released) return
        main.removeCallbacks(resumeGrowing)
        detachSurface(attachedView)
        stop()
        released = true
        player.setEventListener(null)
        player.release()
        vlc.release()
    }

    private companion object {
        const val MAX_STALLED_GROWTH_RETRIES = 100
    }
}
