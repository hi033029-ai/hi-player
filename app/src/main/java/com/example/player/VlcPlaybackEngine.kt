package com.example.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.SurfaceView
import androidx.media3.common.C
import com.example.model.VideoTrackInfo
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IVLCVout
import java.util.concurrent.atomic.AtomicLong

/**
 * Completed local media. Growing Matroska uses Media3's byte-addressed seek map.
 *
 * There are two owners, with an asynchronous barrier between them:
 * - the native handler owns ALL Media/MediaPlayer calls, including stop/release;
 * - main owns AWindow attach/detach, ONLY after the worker has stopped playback.
 *
 * Bind a raw Surface without a SurfaceHolder. AWindow must not install its own
 * holder callback: in libvlc 3.7.7 that callback synchronously calls play() or
 * setVideoTrackEnabled() on main, racing native stop and decoder teardown.
 * Surface destruction queues a stop; its acknowledgement permits detach on main.
 * Neither thread waits for the other. A stuck vendor codec cannot lock the UI.
 */
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
        val playing: Boolean, val buffering: Boolean, val positionMs: Long,
        val durationMs: Long, val bufferedPositionMs: Long, val seekable: Boolean,
    )
    private data class Request(val uri: Uri, val startMs: Long)

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val thread = HandlerThread("HiPlayer-LibVLC").apply { start() }
    private val worker = Handler(thread.looper)
    private val revision = AtomicLong()
    @Volatile private var released = false
    @Volatile private var request: Request? = null
    @Volatile private var wantsPlayback = false
    private var view: SurfaceView? = null // main only
    private var bound = false // main only
    private var windowWidth = 0
    private var windowHeight = 0
    private var cached = State(false, false, 0, 0, 0, false)
    @Volatile private var lastGoodMs = 0L
    @Volatile private var speed = 1f
    @Volatile private var muted = false
    @Volatile private var boost = 0
    @Volatile private var audioDelayUs = 0L
    @Volatile private var subtitleDelayUs = 0L
    @Volatile private var selectedAudio: Int? = null
    @Volatile private var selectedSubtitle: Int? = null

    // Worker only. loadedRevision remains invalid throughout the surface barrier.
    private var library: LibVLC? = null
    private var player: MediaPlayer? = null
    private var descriptor: ParcelFileDescriptor? = null
    private var loadedRevision = -1L
    private var pendingResumeMs = 0L
    // Worker only. While paused, ignore pre-seek samples and accept the native
    // clock (or time event) near the latest requested presentation time.
    private var pausedSeekMs: Long? = null
    private var buffering = false
    @Volatile private var firstFrameRevision = -1L
    private var displayedPictures = 0
    private var reportedTracks: Pair<List<VideoTrackInfo>, List<VideoTrackInfo>>? = null
    private var failedRevision = -1L

    @Volatile var unresponsive: Boolean = false
        private set

    private val watchdog = Runnable {
        if (!released && request != null && firstFrameRevision != revision.get()) {
            unresponsive = true
            onError("Video output did not start. Trying the compatible playback engine.")
        }
    }
    private val sample = object : Runnable {
        override fun run() {
            if (released) return
            val token = loadedRevision
            try {
                if (token == revision.get()) player?.let { publishOnWorker(it, token) }
            } catch (error: RuntimeException) { fail(token, error) }
            worker.postDelayed(this, 200L)
        }
    }

    init {
        worker.post {
            try {
                library = LibVLC(appContext, arrayListOf(
                    "--no-video-title-show", "--avcodec-hw=any", "--file-caching=250",
                ))
                player = MediaPlayer(library!!).apply {
                    // The stock AudioDeviceCallback is registered with a null
                    // Handler and performs native route changes on main. PCM
                    // preserves decoded lossless/multichannel audio; Android owns
                    // device routing. No synchronous VLC plug callback is needed.
                    setAudioOutputDevice("pcm")
                }
                worker.post(sample)
            } catch (error: LinkageError) { fail(revision.get(), error) }
              catch (error: RuntimeException) { fail(revision.get(), error) }
        }
    }

    private fun postMain(token: Long, action: () -> Unit) {
        main.post { if (!released && token == revision.get()) action() }
    }

    fun open(uri: Uri, startPositionMs: Long, autoPlay: Boolean, isGrowing: Boolean) {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (released) return
        if (isGrowing) {
            onError("Growing media requires the indexed playback engine.")
            return
        }
        if (request?.uri != uri) { selectedAudio = null; selectedSubtitle = null }
        request = Request(uri, startPositionMs.coerceAtLeast(0))
        lastGoodMs = startPositionMs.coerceAtLeast(0)
        wantsPlayback = autoPlay
        cached = State(false, true, lastGoodMs, 0, 0, false)
        onUpdate(cached)
        rebind()
    }

    fun attachSurface(surfaceView: SurfaceView) {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (released || view === surfaceView || !surfaceView.holder.surface.isValid) return
        view = surfaceView
        windowWidth = surfaceView.width
        windowHeight = surfaceView.height
        rebind()
    }

    fun detachSurface(surfaceView: SurfaceView?) {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (surfaceView == null || view !== surfaceView) return
        view = null
        if (!released) rebind()
    }

    fun updateWindowSize(surfaceView: SurfaceView) {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (released || view !== surfaceView) return
        val width = surfaceView.width
        val height = surfaceView.height
        if (width == windowWidth && height == windowHeight) return
        windowWidth = width
        windowHeight = height
        // Some SurfaceView resizes preserve the Surface. Cross the same stop
        // barrier rather than invoking nativeOnWindowSize on a live vout on UI.
        rebind()
    }

    /** Stop -> main detach/attach -> worker prepare/play. No blocking handoff. */
    private fun rebind() {
        val token = revision.incrementAndGet()
        main.removeCallbacks(watchdog)
        if (!released && request != null) main.postDelayed(watchdog, 18_000)
        worker.post {
            loadedRevision = -1L
            val nativePlayer = player
            try {
                nativePlayer?.setEventListener(null)
                nativePlayer?.stop()
                // AWindow.attachViews can synchronously call
                // MediaPlayer.setVideoTrackEnabled on main. hasMedia MUST be
                // false at the barrier so that callback cannot enter JNI.
                nativePlayer?.media = null
                descriptor?.close()
                descriptor = null
            } catch (error: Exception) { fail(token, error); return@post }
            main.post barrier@{
                // Even a superseded stop must leave the old binding detached.
                // Only the latest revision can attach or prepare a new surface.
                if (token != revision.get()) return@barrier
                try {
                    if (bound) nativePlayer?.vlcVout?.detachViews()
                    bound = false
                    val nextView = view
                    if (!released && nextView != null && nextView.holder.surface.isValid && nativePlayer != null) {
                        val vout = nativePlayer.vlcVout
                        vout.setVideoSurface(nextView.holder.surface, null)
                        vout.attachViews(object : IVLCVout.OnNewVideoLayoutListener {
                            override fun onNewVideoLayout(
                                vlcVout: IVLCVout, width: Int, height: Int,
                                visibleWidth: Int, visibleHeight: Int, sarNum: Int, sarDen: Int,
                            ) {
                                postMain(token) {
                                    val pixels = if (visibleWidth > 0) visibleWidth else width
                                    val displayWidth = if (sarNum > 0 && sarDen > 0)
                                        Math.round(pixels * sarNum.toDouble() / sarDen).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
                                    else pixels.coerceAtLeast(0)
                                    onVideoLayout(
                                        displayWidth,
                                        (if (visibleHeight > 0) visibleHeight else height).coerceAtLeast(0),
                                    )
                                }
                            }
                        })
                        vout.setWindowSize(windowWidth.coerceAtLeast(1), windowHeight.coerceAtLeast(1))
                        bound = true
                    }
                    val canRender = bound
                    worker.post {
                        if (token != revision.get()) return@post
                        if (released) releaseOnWorker()
                        else if (canRender) prepareOnWorker(token)
                    }
                } catch (error: RuntimeException) { fail(token, error) }
            }
        }
    }

    private fun prepareOnWorker(token: Long) {
        val next = request ?: return
        val nativePlayer = player ?: return
        val lib = library ?: return
        if (released || token != revision.get()) return
        try {
            val media = if (next.uri.scheme == "content") {
                val fd = appContext.contentResolver.openFileDescriptor(next.uri, "r")
                    ?: throw IllegalStateException("No readable descriptor")
                descriptor = fd
                Media(lib, fd.fileDescriptor)
            } else Media(lib, next.uri)
            try {
                media.setHWDecoderEnabled(true, false)
                media.addOption(":no-video-title-show")
                nativePlayer.media = media
            } finally { media.release() }
            pendingResumeMs = lastGoodMs.takeIf { it > 0 } ?: next.startMs
            pausedSeekMs = null
            buffering = true
            displayedPictures = 0
            reportedTracks = null
            loadedRevision = token
            // Capture the load revision in the listener. Never stamp an old event
            // with the generation that happens to be current at callback delivery.
            nativePlayer.setEventListener { event ->
                val type = event.type
                // LibVLC reports a paused seek through TimeChanged even on
                // Android 7 builds where no new picture is counted until
                // playback resumes. Keep the event's timeline value so the
                // public position does not remain stuck at the last rendered
                // frame while the decoder is correctly paused.
                val changedTime = if (type == MediaPlayer.Event.TimeChanged) {
                    event.timeChanged
                } else {
                    null
                }
                worker.post {
                    if (!released && loadedRevision == token && revision.get() == token) {
                        handleEvent(nativePlayer, type, token, changedTime)
                    }
                }
            }
            nativePlayer.play() // also initializes a paused item's first frame

        } catch (error: LinkageError) { fail(token, error) }
          catch (error: Exception) { fail(token, error) }
    }

    private fun handleEvent(nativePlayer: MediaPlayer, type: Int, token: Long, changedTime: Long? = null) {
        when (type) {
            MediaPlayer.Event.Opening -> buffering = true
            MediaPlayer.Event.TimeChanged -> {
                if (pausedSeekMs == null && pendingResumeMs == 0L) {
                    lastGoodMs = (changedTime ?: nativePlayer.time).coerceAtLeast(0L)
                }
            }
            MediaPlayer.Event.Playing -> {
                buffering = false
                if (pendingResumeMs > 0) {
                    if (!nativePlayer.isSeekable) {
                        postMain(token) { onSeekUnavailable(pendingResumeMs) }
                        return
                    }
                    nativePlayer.setTime(pendingResumeMs)
                    pendingResumeMs = 0
                }
                applyControls(nativePlayer)
                selectedAudio?.let { nativePlayer.setAudioTrack(it) }
                selectedSubtitle?.let { nativePlayer.setSpuTrack(it) }
                if (!wantsPlayback) nativePlayer.pause()
                refreshTracksOnWorker(nativePlayer, token)
            }
            MediaPlayer.Event.Paused -> buffering = false
            MediaPlayer.Event.ESAdded, MediaPlayer.Event.ESSelected -> refreshTracksOnWorker(nativePlayer, token)
            MediaPlayer.Event.EndReached -> {
                wantsPlayback = false
                buffering = false
                postMain(token) { main.removeCallbacks(watchdog); onEnd() }
            }
            MediaPlayer.Event.EncounteredError -> {
                fail(token, IllegalStateException("Native playback failed")); return
            }
        }
        publishOnWorker(nativePlayer, token, changedTime)
    }

    private fun publishOnWorker(nativePlayer: MediaPlayer, token: Long, changedTime: Long? = null) {
        if (released || token != loadedRevision || token != revision.get()) return
        val time = nativePlayer.time
        // A vout/layout event is not proof that a frame was displayed.
        val media = nativePlayer.media
        val displayedCount = try { media?.stats?.displayedPictures ?: 0 }
                        finally { media?.release() }
        val displayed = displayedCount > displayedPictures
        displayedPictures = displayedCount
        if (displayed && firstFrameRevision != token) {
            firstFrameRevision = token
            postMain(token) { main.removeCallbacks(watchdog); onFirstFrame() }
        }
        if (pendingResumeMs == 0L) {
            val target = pausedSeekMs
            if (target != null) {
                // A paused vout need not display another picture for its
                // timeline to change. Delayed events may describe an older seek.
                if (time >= 0 && kotlin.math.abs(time - target) <= 1_000) lastGoodMs = time
                else if (changedTime != null && changedTime >= 0 && kotlin.math.abs(changedTime - target) <= 1_000) lastGoodMs = changedTime
            } else if (displayed && time >= 0) lastGoodMs = time
        }
        val duration = nativePlayer.length.coerceAtLeast(0)
        val state = State(nativePlayer.isPlaying, buffering,
            if (pendingResumeMs > 0) pendingResumeMs else lastGoodMs,
            duration, duration, nativePlayer.isSeekable)
        postMain(token) { cached = state; onUpdate(state) }
    }

    private fun active(action: (MediaPlayer) -> Unit) {
        val token = revision.get()
        worker.post {
            if (!released && token == loadedRevision && token == revision.get()) player?.let(action)
        }
    }
    fun play() { wantsPlayback = true; active { pausedSeekMs = null; it.play() } }
    fun pause() { wantsPlayback = false; active { it.pause() } }
    fun togglePlayPause() { if (wantsPlayback) pause() else play() }
    fun stop() {
        if (released) return
        request = null
        wantsPlayback = false
        lastGoodMs = 0
        rebind()
    }
    fun seekTo(positionMs: Long) {
        val target = positionMs.coerceAtLeast(0)
        active {
            if (it.isSeekable) {
                pausedSeekMs = target.takeUnless { wantsPlayback }
                it.setTime(target)
                publishOnWorker(it, loadedRevision)
            } else {
                postMain(revision.get()) { onSeekUnavailable(target) }
            }
        }
    }
    // Color grading belongs to one color-aware Media3 processor. VLC adjust is
    // an SDR filter and must never be attached to a potentially HDR stream.
    fun setSpeed(value: Float) { speed = value.coerceIn(0.25f, 4f); active(::applyControls) }
    fun setMuted(value: Boolean, boostPercent: Int) {
        muted = value; boost = boostPercent.coerceIn(0, 100); active(::applyControls)
    }
    fun setDelays(audioMs: Long, subtitleMs: Long) {
        audioDelayUs = audioMs * 1_000; subtitleDelayUs = subtitleMs * 1_000; active(::applyControls)
    }
    private fun applyControls(nativePlayer: MediaPlayer) {
        nativePlayer.setRate(speed)
        nativePlayer.setVolume(if (muted) 0 else 100 + boost)
        nativePlayer.setAudioDelay(audioDelayUs)
        nativePlayer.setSpuDelay(subtitleDelayUs)
    }
    fun selectAudioTrack(info: VideoTrackInfo) {
        selectedAudio = info.trackIndex
        active { it.setAudioTrack(info.trackIndex); refreshTracksOnWorker(it, loadedRevision) }
    }
    fun selectSubtitleTrack(info: VideoTrackInfo?) {
        selectedSubtitle = info?.trackIndex ?: -1
        active { it.setSpuTrack(selectedSubtitle!!); refreshTracksOnWorker(it, loadedRevision) }
    }
    fun refreshTracks() { active { refreshTracksOnWorker(it, loadedRevision) } }
    private fun refreshTracksOnWorker(nativePlayer: MediaPlayer, token: Long) {
        val audio = nativePlayer.audioTracks.orEmpty().filter { it.id >= 0 }.map {
            VideoTrackInfo("vlc-audio-${it.id}", it.name ?: "Audio ${it.id}", null, "audio/vlc",
                isSelected = nativePlayer.audioTrack == it.id, isSupported = true, trackType = C.TRACK_TYPE_AUDIO, trackGroupIndex = 0, trackIndex = it.id)
        }
        val subtitles = nativePlayer.spuTracks.orEmpty().filter { it.id >= 0 }.map {
            VideoTrackInfo("vlc-sub-${it.id}", it.name ?: "Subtitles ${it.id}", null, "text/vlc",
                isSelected = nativePlayer.spuTrack == it.id, isSupported = true, trackType = C.TRACK_TYPE_TEXT, trackGroupIndex = 0, trackIndex = it.id)
        }
        val tracks = audio to subtitles
        if (reportedTracks != tracks) {
            reportedTracks = tracks
            postMain(token) { onTracks(audio, subtitles) }
        }
    }
    fun snapshot(): State = cached
    fun publish() = Unit // one periodic worker sample; UI polling cannot queue JNI calls

    private fun fail(token: Long, error: Throwable) {
        Log.e("HiPlayerLibVLC", "Native playback operation failed (${error.javaClass.simpleName})")
        postMain(token) {
            if (failedRevision != token) {
                failedRevision = token
                main.removeCallbacks(watchdog)
                onError("This video could not start in the native player. Trying compatible playback.")
            }
        }
    }
    fun release() {
        if (released) return
        released = true
        request = null
        view = null
        main.removeCallbacks(watchdog)
        rebind() // stop acknowledgement -> main detach -> native release
    }
    private fun releaseOnWorker() {
        worker.removeCallbacksAndMessages(null)
        descriptor?.close(); descriptor = null
        player?.release(); player = null
        library?.release(); library = null
        thread.quitSafely()
    }
}
