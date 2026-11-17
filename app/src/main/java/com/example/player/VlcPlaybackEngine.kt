package com.example.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.os.SystemClock
import android.util.Log
import android.os.ParcelFileDescriptor
import android.view.SurfaceView
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.SeekPoint
import com.example.model.PendingDownloadFormat
import com.example.model.VideoTrackInfo
import com.example.playback.GrowingMkvClusterIndex
import com.example.playback.createGrowingMkvClusterIndex
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IVLCVout
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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

/**
 * A growing file is seekable only within its *validated* Cluster prefix.
 * This helper never extrapolates a time beyond the downloaded index.
 */
internal object VlcGrowingSeekPolicy {
    fun clusterTimeMs(requestedMs: Long, orderedClusterTimesUs: List<Long>): Long? {
        if (orderedClusterTimesUs.isEmpty()) return null
        val requestedUs = requestedMs.coerceAtLeast(0L)
            .coerceAtMost(Long.MAX_VALUE / 1_000L) * 1_000L
        if (requestedUs < orderedClusterTimesUs.first()) return null
        val boundedUs = requestedUs.coerceAtMost(orderedClusterTimesUs.last())
        var low = 0
        var high = orderedClusterTimesUs.lastIndex
        var selected = -1
        while (low <= high) {
            val middle = (low + high) ushr 1
            if (orderedClusterTimesUs[middle] <= boundedUs) {
                selected = middle
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        return if (selected >= 0) orderedClusterTimesUs[selected] / 1_000L else null
    }
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

    private data class OpenRequest(
        val uri: Uri,
        val startMs: Long,
        val autoPlay: Boolean,
        val growing: Boolean,
    )

    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val main = Handler(Looper.getMainLooper())

    // Native LibVLC initialization, file-provider I/O, media preparation and
    // polling MUST NOT execute on the Android UI thread. The player has one
    // serialized worker; callbacks and SurfaceView ownership stay on main.
    private val nativeThread = HandlerThread("HiPlayer-LibVLC").apply { start() }
    private val native = Handler(nativeThread.looper)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val generation = AtomicLong(0)
    private val sampleQueued = AtomicBoolean(false)

    // Main-thread state. No property getter on MediaPlayer is called from UI.
    private var uiPlayer: MediaPlayer? = null
    private var requestedView: SurfaceView? = null
    private var attachedView: SurfaceView? = null
    @Volatile private var requested: OpenRequest? = null
    private var cached = State(false, false, 0L, 0L, 0L, false)
    private var indexJob: Job? = null
    private var growthProbeJob: Job? = null
    private var growingClusterIndex: GrowingMkvClusterIndex? = null
    @Volatile private var indexedClusters: List<SeekPoint> = emptyList()
    private var lastIndexScanAtMs = 0L
    private var retryScheduled = false
    private var stalledGrowthRetries = 0
    private var lastSize = -1L
    @Volatile private var lastGoodTimeMs = 0L
    private var speed = 1f
    private var muted = false
    private var boost = 0
    private var audioDelayUs = 0L
    private var subtitleDelayUs = 0L
    @Volatile private var colorPreset: ColorPreset = ColorPresets.HDR_DISABLED
    @Volatile private var colorEnhancementEnabled = false

    @Volatile private var released = false
    @Volatile private var surfaceAttached = false
    @Volatile private var wantsPlayback = false

    // Native-worker-only state. The UI NEVER calls methods on these objects.
    private var libVlc: LibVLC? = null
    private var nativePlayer: MediaPlayer? = null
    private var openDescriptor: ParcelFileDescriptor? = null
    private var loadedGeneration = -1L
    private var pendingResumeMs = 0L
    private var nativeBuffering = false
    private var triedAvformat = false
    private var reportedTracks: Pair<List<VideoTrackInfo>, List<VideoTrackInfo>>? = null

    private val startupWatchdog = Runnable {
        val request = requested ?: return@Runnable
        if (released || !request.autoPlay) return@Runnable
        // A player that never advanced after opening is not usable, even when
        // its native decoder did not publish an explicit failure event.
        if (cached.positionMs <= request.startMs) {
            Log.e(TAG, "LibVLC playback stalled at startup; requesting Media3 fallback")
            onError("LibVLC failed to start playback in time; using Media3 fallback.")
        }
    }

    private val resumeGrowing = Runnable {
        retryScheduled = false
        val request = requested ?: return@Runnable
        if (!request.growing || growthProbeJob?.isActive == true) return@Runnable
        val token = generation.get()
        growthProbeJob = ioScope.launch {
            val size = sourceSize(request.uri)
            main.post {
                if (released || token != generation.get() || requested?.uri != request.uri) {
                    return@post
                }
                if ((size < 0L && isPotentiallyGrowing(request.uri)) || size > lastSize) {
                    if (size >= 0L) lastSize = size
                    stalledGrowthRetries = 0
                    // Native stop/reopen is serialized on the worker. Resume at
                    // the last actual decoded timestamp, never an invented 0:00.
                    open(request.uri, lastGoodTimeMs, wantsPlayback, true)
                } else if (isPotentiallyGrowing(request.uri)) {
                    // Temporary EOF is not the end of a downloading movie.
                    stalledGrowthRetries = (stalledGrowthRetries + 1).coerceAtMost(100)
                    scheduleGrowingRetry()
                } else {
                    onEnd()
                }
            }
        }
    }

    init {
        native.post {
            try {
                val lib = LibVLC(appContext, arrayListOf(
                    "--no-video-title-show", "--avcodec-hw=any", "--file-caching=250",
                ))
                libVlc = lib
                val player = MediaPlayer(lib)
                nativePlayer = player
                player.setEventListener { event ->
                    // Do not call back into LibVLC on its native event thread.
                    // Serialize handling and media operations on our worker.
                    val eventType = event.type
                    val token = generation.get()
                    native.post {
                        if (!released && token == generation.get()) {
                            handleNativeEvent(player, eventType, token)
                        }
                    }
                }
                main.post {
                    if (!released) {
                        uiPlayer = player
                        attachRequestedSurface()
                    }
                }
            } catch (error: Throwable) {
                if (error !is LinkageError && error !is RuntimeException) throw error
                Log.e(TAG, "LibVLC native initialization failed", error)
                main.post {
                    if (!released) onError("LibVLC could not initialize on this device: " +
                        (error.localizedMessage ?: error.javaClass.simpleName))
                }
            }
        }
    }

    private fun onMain(token: Long, callback: () -> Unit) {
        main.post {
            if (!released && token == generation.get()) callback()
        }
    }

    private fun registryKey(uri: Uri): String =
        if (uri.scheme == "file") uri.path.orEmpty() else uri.toString()

    private fun isPotentiallyGrowing(uri: Uri): Boolean {
        val key = registryKey(uri)
        return DownloadingFilesRegistry.completedUri(key) == null &&
            (DownloadingFilesRegistry.isDownloading(key) ||
                PendingDownloadFormat.isPending(uri.lastPathSegment.orEmpty()))
    }

    private fun sourceSize(uri: Uri): Long = runCatching {
        if (uri.scheme == "file") {
            File(uri.path.orEmpty()).length()
        } else {
            resolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
        }
    }.getOrDefault(-1L)

    /**
     * Called on the main thread; preparing media is queued immediately, but
     * native initialization and content-provider opens happen only on worker.
     */
    fun open(uri: Uri, startPositionMs: Long, autoPlay: Boolean, isGrowing: Boolean) {
        if (released) return
        val changed = requested?.uri != uri
        val token = generation.incrementAndGet()
        main.removeCallbacks(resumeGrowing)
        main.removeCallbacks(startupWatchdog)
        retryScheduled = false
        growthProbeJob?.cancel()
        if (changed) {
            indexJob?.cancel()
            indexedClusters = emptyList()
            growingClusterIndex = null
            lastIndexScanAtMs = 0L
            lastSize = -1L
            stalledGrowthRetries = 0
        }
        requested = OpenRequest(uri, startPositionMs.coerceAtLeast(0L), autoPlay, isGrowing)
        wantsPlayback = autoPlay
        lastGoodTimeMs = startPositionMs.coerceAtLeast(0L)
        if (isGrowing) {
            if (growingClusterIndex == null) {
                growingClusterIndex = createGrowingMkvClusterIndex(
                    context = appContext, uri = uri,
                    isDownloading = { isPotentiallyGrowing(uri) },
                )
            }
            refreshGrowingIndex(force = true)
            probeInitialSize(uri, token)
        } else {
            indexJob?.cancel()
            growingClusterIndex = null
            indexedClusters = emptyList()
        }
        cached = State(false, true, lastGoodTimeMs, 0L, 0L, false)
        onUpdate(cached)
        if (autoPlay) {
            main.postDelayed(startupWatchdog, if (isGrowing) 30_000L else 18_000L)
        }
        native.post {
            if (released || token != generation.get()) return@post
            triedAvformat = false
            prepareOnWorker(requested ?: return@post, token)
        }
    }

    private fun probeInitialSize(uri: Uri, token: Long) {
        ioScope.launch {
            val size = sourceSize(uri)
            onMain(token) { if (size >= 0L) lastSize = size }
        }
    }

    /** Must only be called on nativeThread. */
    private fun prepareOnWorker(request: OpenRequest, token: Long) {
        if (released || token != generation.get()) return
        val player = nativePlayer ?: return
        val library = libVlc ?: return
        try {
            loadedGeneration = -1L
            player.stop()
            openDescriptor?.close()
            openDescriptor = null
            pendingResumeMs = request.startMs
            nativeBuffering = true
            reportedTracks = null
            val media = if (request.uri.scheme == "content") {
                val descriptor = resolver.openFileDescriptor(request.uri, "r")
                    ?: throw IllegalStateException("Provider returned no file descriptor")
                openDescriptor = descriptor
                Media(library, descriptor.fileDescriptor)
            } else {
                Media(library, request.uri)
            }
            try {
                media.setHWDecoderEnabled(true, false)
                media.addOption(":file-caching=250")
                media.addOption(":no-video-title-show")
                if (triedAvformat) media.addOption(":demux=avformat")
                vlcColorAdjustOptions(colorPreset, colorEnhancementEnabled)
                    .forEach(media::addOption)
                player.media = media
            } finally {
                media.release()
            }
            loadedGeneration = token
            if (surfaceAttached) player.play()
            emitState(player, token)
        } catch (error: Throwable) {
            if (error !is RuntimeException && error !is LinkageError) throw error
            Log.e(TAG, "LibVLC media preparation failed", error)
            loadedGeneration = -1L
            runCatching { openDescriptor?.close() }
            openDescriptor = null
            nativeBuffering = false
            onMain(token) {
                onError("Cannot open video with LibVLC: " +
                    (error.localizedMessage ?: error.javaClass.simpleName))
            }
        }
    }

    /**
     * LibVLC IVLCVout.setVideoView/attachViews is @MainThread. Keep only this
     * video-surface operation on UI; native player operations stay on worker.
     */
    fun attachSurface(view: SurfaceView) {
        if (released) return
        requestedView = view
        attachRequestedSurface()
    }

    private fun attachRequestedSurface() {
        val view = requestedView ?: return
        val player = uiPlayer ?: return
        if (released || !view.holder.surface.isValid || attachedView === view) return
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
                        if (!released && attachedView === view) {
                            onVideoLayout(
                                (if (visibleWidth > 0) visibleWidth else width).coerceAtLeast(0),
                                (if (visibleHeight > 0) visibleHeight else height).coerceAtLeast(0),
                            )
                        }
                    }
                }
            })
            attachedView = view
            surfaceAttached = true
            vout.setWindowSize(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1))
            val token = generation.get()
            native.post {
                if (!released && token == loadedGeneration && surfaceAttached) {
                    nativePlayer?.play()
                }
            }
        } catch (error: RuntimeException) {
            Log.e(TAG, "LibVLC SurfaceView attachment failed", error)
            attachedView = null
            surfaceAttached = false
            runCatching { player.vlcVout.detachViews() }
            val token = generation.get()
            onMain(token) { onError("LibVLC could not attach its video surface.") }
        }
    }

    fun updateWindowSize(view: SurfaceView) {
        if (released || view !== attachedView || !view.holder.surface.isValid) return
        runCatching {
            uiPlayer?.vlcVout?.setWindowSize(
                view.width.coerceAtLeast(1), view.height.coerceAtLeast(1),
            )
        }.onFailure { Log.w(TAG, "Cannot resize LibVLC surface", it) }
    }

    fun detachSurface(view: SurfaceView?) {
        if (view == null) return
        if (requestedView === view) requestedView = null
        if (attachedView !== view) return
        attachedView = null
        surfaceAttached = false
        // The vout API explicitly requires the main thread.
        runCatching { uiPlayer?.vlcVout?.detachViews() }
            .onFailure { Log.w(TAG, "LibVLC surface detach failed", it) }
    }

    fun play() {
        if (released) return
        wantsPlayback = true
        val token = generation.get()
        native.post {
            if (!released && token == loadedGeneration && surfaceAttached) nativePlayer?.play()
        }
    }

    fun pause() {
        if (released) return
        wantsPlayback = false
        val token = generation.get()
        native.post {
            if (!released && token == loadedGeneration) nativePlayer?.pause()
        }
    }

    fun togglePlayPause() { if (wantsPlayback) pause() else play() }

    fun stop() {
        if (released) return
        val token = generation.incrementAndGet()
        main.removeCallbacks(resumeGrowing)
        main.removeCallbacks(startupWatchdog)
        retryScheduled = false
        indexJob?.cancel()
        growthProbeJob?.cancel()
        growingClusterIndex = null
        indexedClusters = emptyList()
        requested = null
        wantsPlayback = false
        lastGoodTimeMs = 0L
        lastSize = -1L
        cached = State(false, false, 0L, 0L, 0L, false)
        native.post {
            if (token != generation.get() || released) return@post
            loadedGeneration = -1L
            runCatching { nativePlayer?.stop() }
            runCatching { openDescriptor?.close() }
            openDescriptor = null
        }
    }

    /** Do not present an index position as a successful native seek. */
    fun seekTo(positionMs: Long) {
        if (released) return
        val request = requested ?: return
        val requestedMs = positionMs.coerceAtLeast(0L)
        if (request.growing) refreshGrowingIndex(force = true)
        val target = if (request.growing) {
            VlcGrowingSeekPolicy.clusterTimeMs(requestedMs, indexedClusters.map { it.timeUs })
        } else {
            requestedMs
        }
        if (target == null) {
            onSeekUnavailable(requestedMs)
            return
        }
        val token = generation.get()
        native.post {
            if (released || token != loadedGeneration) return@post
            val player = nativePlayer ?: return@post
            if (player.isSeekable) {
                player.setTime(target)
                pendingResumeMs = 0L
                emitState(player, token)
            } else {
                onMain(token) { onSeekUnavailable(requestedMs) }
            }
        }
    }

    fun setColorPreset(preset: ColorPreset, enhancementEnabled: Boolean, reopen: Boolean = true) {
        val changed = colorPreset != preset || colorEnhancementEnabled != enhancementEnabled
        colorPreset = preset
        colorEnhancementEnabled = enhancementEnabled
        val request = requested
        if (changed && reopen && request != null) {
            open(request.uri, lastGoodTimeMs, wantsPlayback, request.growing)
        }
    }

    fun setSpeed(value: Float) {
        speed = value.coerceIn(0.25f, 4f)
        val rate = speed
        native.post {
            if (!released) nativePlayer?.let { runCatching { it.setRate(rate) } }
        }
    }

    fun setMuted(value: Boolean, boostPercent: Int) {
        muted = value
        boost = boostPercent.coerceIn(0, 100)
        val volume = if (muted) 0 else 100 + boost
        native.post {
            if (!released) nativePlayer?.let { runCatching { it.setVolume(volume) } }
        }
    }

    fun setDelays(audioMs: Long, subtitleMs: Long) {
        audioDelayUs = audioMs * 1_000L
        subtitleDelayUs = subtitleMs * 1_000L
        val audio = audioDelayUs
        val subtitles = subtitleDelayUs
        native.post {
            if (!released) nativePlayer?.let {
                runCatching { it.setAudioDelay(audio) }
                runCatching { it.setSpuDelay(subtitles) }
            }
        }
    }

    fun selectAudioTrack(info: VideoTrackInfo) {
        val token = generation.get()
        native.post {
            if (token == loadedGeneration && !released) {
                nativePlayer?.setAudioTrack(info.trackIndex)
                refreshTracksOnWorker(nativePlayer ?: return@post, token)
            }
        }
    }

    fun selectSubtitleTrack(info: VideoTrackInfo?) {
        val token = generation.get()
        native.post {
            if (token == loadedGeneration && !released) {
                nativePlayer?.setSpuTrack(info?.trackIndex ?: -1)
                refreshTracksOnWorker(nativePlayer ?: return@post, token)
            }
        }
    }

    fun refreshTracks() {
        val token = generation.get()
        native.post {
            if (token == loadedGeneration && !released) {
                refreshTracksOnWorker(nativePlayer ?: return@post, token)
            }
        }
    }

    private fun refreshTracksOnWorker(player: MediaPlayer, token: Long) {
        val audio = player.audioTracks.orEmpty().filter { it.id >= 0 }.map { track ->
            VideoTrackInfo(
                id = "vlc-audio-" + track.id,
                label = track.name ?: "Audio " + track.id,
                language = null, mimeType = "audio/vlc",
                isSelected = player.audioTrack == track.id,
                isSupported = true, trackType = C.TRACK_TYPE_AUDIO,
                trackGroupIndex = 0, trackIndex = track.id,
            )
        }
        val subtitles = player.spuTracks.orEmpty().filter { it.id >= 0 }.map { track ->
            VideoTrackInfo(
                id = "vlc-sub-" + track.id,
                label = track.name ?: "Subtitles " + track.id,
                language = null, mimeType = "text/vlc",
                isSelected = player.spuTrack == track.id,
                isSupported = true, trackType = C.TRACK_TYPE_TEXT,
                trackGroupIndex = 0, trackIndex = track.id,
            )
        }
        val tracks = audio to subtitles
        if (reportedTracks != tracks) {
            reportedTracks = tracks
            onMain(token) { onTracks(audio, subtitles) }
        }
    }

    private fun handleNativeEvent(player: MediaPlayer, eventType: Int, token: Long) {
        if (token != loadedGeneration) return
        when (eventType) {
            MediaPlayer.Event.Opening, MediaPlayer.Event.Buffering -> nativeBuffering = true
            MediaPlayer.Event.Playing -> {
                nativeBuffering = false
                if (pendingResumeMs > 0L && player.isSeekable) {
                    player.setTime(pendingResumeMs)
                    pendingResumeMs = 0L
                }
                player.setRate(speed)
                player.setVolume(if (muted) 0 else 100 + boost)
                player.setAudioDelay(audioDelayUs)
                player.setSpuDelay(subtitleDelayUs)
                if (!wantsPlayback) player.pause()
                refreshTracksOnWorker(player, token)
            }
            MediaPlayer.Event.Paused -> nativeBuffering = false
            MediaPlayer.Event.Vout -> {
                onMain(token) { onFirstFrame() }
                refreshTracksOnWorker(player, token)
            }
            MediaPlayer.Event.ESAdded, MediaPlayer.Event.ESSelected ->
                refreshTracksOnWorker(player, token)
            MediaPlayer.Event.EncounteredError -> {
                val request = requested
                if (request != null && !triedAvformat) {
                    triedAvformat = true
                    prepareOnWorker(request.copy(startMs = lastGoodTimeMs), token)
                    return
                }
                nativeBuffering = false
                if (request?.growing == true) {
                    onMain(token) { scheduleGrowingRetry() }
                } else {
                    onMain(token) { onError("LibVLC could not decode this file; trying Media3.") }
                }
            }
            MediaPlayer.Event.EndReached -> {
                nativeBuffering = false
                val request = requested
                if (request?.growing == true) {
                    onMain(token) { scheduleGrowingRetry() }
                } else {
                    onMain(token) { onEnd() }
                }
            }
        }
        emitState(player, token)
    }

    private fun refreshGrowingIndex(force: Boolean = false) {
        val index = growingClusterIndex ?: return
        if (indexJob?.isActive == true) return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastIndexScanAtMs < 1_000L) return
        lastIndexScanAtMs = now
        indexJob = ioScope.launch {
            val points = runCatching { index.scanAvailable() }.getOrNull()
            main.post {
                if (!released && growingClusterIndex === index && points != null) {
                    indexedClusters = points
                }
            }
        }
    }

    private fun scheduleGrowingRetry() {
        if (released || retryScheduled || requested?.growing != true) return
        retryScheduled = true
        main.postDelayed(resumeGrowing, if (stalledGrowthRetries >= 3) 4_000L else 1_200L)
    }

    private fun emitState(player: MediaPlayer, token: Long) {
        if (released || token != generation.get() || token != loadedGeneration) return
        if (pendingResumeMs > 0L && player.isSeekable && player.isPlaying) {
            player.setTime(pendingResumeMs)
            pendingResumeMs = 0L
        }
        val rawPosition = player.time.coerceAtLeast(0L)
        val position = if (rawPosition > 0L) rawPosition else lastGoodTimeMs
        val lastIndexedMs = indexedClusters.lastOrNull()?.timeUs?.div(1_000L) ?: 0L
        val nativeLength = player.length.coerceAtLeast(0L)
        val state = State(
            playing = player.isPlaying,
            buffering = nativeBuffering,
            positionMs = position,
            durationMs = maxOf(nativeLength, lastIndexedMs),
            bufferedPositionMs = maxOf(position, lastIndexedMs),
            seekable = player.isSeekable || indexedClusters.size >= 2,
        )
        onMain(token) {
            if (state.positionMs > 0L && !state.buffering) {
                lastGoodTimeMs = state.positionMs
            }
            cached = state
            val request = requested
            if (request != null && state.playing &&
                state.positionMs > request.startMs + 250L
            ) {
                main.removeCallbacks(startupWatchdog)
            }
            onUpdate(state)
        }
    }

    fun snapshot(): State = cached

    /** Request a worker snapshot at most once per UI poll interval. */
    fun publish() {
        if (released) return
        if (requested?.growing == true) refreshGrowingIndex()
        if (!sampleQueued.compareAndSet(false, true)) return
        val token = generation.get()
        native.post {
            try {
                if (!released && token == loadedGeneration) {
                    nativePlayer?.let { emitState(it, token) }
                }
            } finally {
                sampleQueued.set(false)
            }
        }
    }

    fun release() {
        if (released) return
        detachSurface(attachedView)
        released = true
        generation.incrementAndGet()
        main.removeCallbacks(resumeGrowing)
        main.removeCallbacks(startupWatchdog)
        indexJob?.cancel()
        growthProbeJob?.cancel()
        ioScope.cancel()
        requested = null
        growingClusterIndex = null
        indexedClusters = emptyList()
        native.post {
            runCatching { nativePlayer?.setEventListener(null) }
            runCatching { nativePlayer?.stop() }
            runCatching { openDescriptor?.close() }
            openDescriptor = null
            runCatching { nativePlayer?.release() }
            runCatching { libVlc?.release() }
            nativePlayer = null
            libVlc = null
            nativeThread.quitSafely()
        }
    }

    private companion object {
        const val TAG = "HiPlayerLibVLC"
    }
}
