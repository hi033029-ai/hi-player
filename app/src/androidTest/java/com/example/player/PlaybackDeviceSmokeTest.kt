package com.example.player

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** Real surfaces, decoder output, continuous UI heartbeat, seeks and append recovery. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackDeviceSmokeTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun launch(): ActivityScenario<PlaybackSmokeActivity> = ActivityScenario.launch(
        Intent(Intent.ACTION_MAIN).setClassName(context.packageName, PlaybackSmokeActivity::class.java.name))
    private fun fixture(name: String) = File(context.cacheDir, "test-${System.nanoTime()}-$name").apply {
        context.assets.open(name).use { input -> outputStream().use { output -> input.copyTo(output) } }
    }
    private fun await(message: String, timeout: Long = 25_000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (SystemClock.elapsedRealtime() < deadline && !condition()) Thread.sleep(50)
        assertTrue(message, condition())
    }
    private class Heartbeat {
        val maxDelayMs = AtomicLong()
        private val handler = Handler(Looper.getMainLooper())
        @Volatile var running = true
        private var previous = SystemClock.elapsedRealtime()
        private val tick = object : Runnable {
            override fun run() {
                val now = SystemClock.elapsedRealtime()
                maxDelayMs.accumulateAndGet(now - previous, ::maxOf)
                previous = now
                if (running) handler.postDelayed(this, 100)
            }
        }
        init { handler.post(tick) }
        fun check() { assertTrue("UI stalled for ${maxDelayMs.get()} ms", maxDelayMs.get() < 2_000) }
        fun close() { running = false; handler.removeCallbacks(tick) }
    }

    @Test(timeout = 100_000) fun localMkvDisplaysFramesAndSurvivesSurfaceReplacement() {
        val file = fixture("gecko_test.mkv")
        val engine = AtomicReference<VlcPlaybackEngine>()
        val failure = AtomicReference<String>()
        val frames = AtomicLong()
        val position = AtomicLong()
        val heartbeat = Heartbeat()
        try {
            launch().use { scenario ->
                var currentView: SurfaceView? = null
                fun attach(activity: PlaybackSmokeActivity, playback: VlcPlaybackEngine) {
                    val view = SurfaceView(activity)
                    currentView = view
                    view.holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) { playback.attachSurface(view) }
                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { playback.updateWindowSize(view) }
                        override fun surfaceDestroyed(holder: SurfaceHolder) { playback.detachSurface(view) }
                    })
                    activity.addContentView(view, FrameLayout.LayoutParams(480, 270))
                }
                scenario.onActivity { activity ->
                    val playback = VlcPlaybackEngine(activity,
                        onUpdate = { position.set(it.positionMs) }, onTracks = { _, _ -> },
                        onVideoLayout = { _, _ -> }, onFirstFrame = { frames.incrementAndGet() },
                        onSeekUnavailable = { failure.set("Seek unavailable") }, onEnd = {}, onError = { failure.set(it) })
                    engine.set(playback)
                    attach(activity, playback)
                    playback.open(Uri.fromFile(file), 0, true, false)
                }
                await("No displayed VLC pictures: ${failure.get()}") { frames.get() > 0 || failure.get() != null }
                assertNull(failure.get())
                await("Native clock did not advance") { position.get() > 2_000 }
                repeat(3) {
                    scenario.onActivity {
                        engine.get().pause(); engine.get().seekTo(5_000); engine.get().play()
                    }
                }
                await("Native seek did not decode at requested position") { position.get() in 4_800..12_000 }
                scenario.onActivity { activity ->
                    (currentView?.parent as? android.view.ViewGroup)?.removeView(currentView)
                    attach(activity, engine.get())
                }
                await("Replacement surface did not display frames") { frames.get() >= 2 || failure.get() != null }
                assertNull(failure.get())
                heartbeat.check()
                scenario.onActivity { engine.get().release() }
            }
        } finally { heartbeat.close(); file.delete() }
    }

    @Test(timeout = 100_000) fun ordinaryMp4ProducesRealMedia3VideoFrame() = playMedia3("gecko_test.mp4", "video/mp4")
    @Test(timeout = 100_000) fun completedMkvThroughAppRoutingRenders() = playMedia3("gecko_test.mkv", "video/x-matroska")
    @Test(timeout = 100_000) fun webmRenders() = playMedia3("playback_test.webm", "video/webm")

    private fun playMedia3(name: String, mime: String) {
        val file = fixture(name)
        val engine = AtomicReference<HiPlayerEngine>()
        val failure = AtomicReference<String>()
        val frames = AtomicLong()
        val heartbeat = Heartbeat()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        try {
            launch().use { scenario ->
                scenario.onActivity { activity ->
                    val playback = HiPlayerEngine(activity, scope)
                    engine.set(playback)
                    playback.getPlayer().addListener(object : Player.Listener {
                        override fun onRenderedFirstFrame() { frames.incrementAndGet() }
                        override fun onPlayerError(error: PlaybackException) { failure.set(error.errorCodeName) }
                    })
                    // This test forces Media3 via an explicit grade for MKV; the
                    // native test above independently requires displayed VLC frames.
                    if (mime.contains("matroska")) {
                        playback.setHdrEnhanceActive(true); playback.setHdrColorPreset(ColorPresets.NATURAL_HDR)
                    }
                    val view = SurfaceView(activity)
                    activity.addContentView(view, FrameLayout.LayoutParams(480,270))
                    playback.getPlayer().setVideoSurfaceView(view)
                    playback.prepareMedia(Uri.fromFile(file), mediaMimeType = mime)
                }
                await("No rendered frame: ${failure.get()}") { frames.get() > 0 || failure.get() != null }
                assertNull(failure.get())
                await("Clock did not advance") { engine.get().currentPositionMs.value > 2_000 }
                scenario.onActivity { engine.get().seekTo(12_000) }
                await("Forward seek did not decode") { engine.get().lastRenderedPresentationMs in 11_800..18_000 }
                scenario.onActivity { engine.get().seekTo(3_000) }
                await("Backward seek did not decode") { engine.get().lastRenderedPresentationMs in 2_800..7_000 }
                scenario.onActivity { engine.get().pauseForAppBackground(); engine.get().resumeAfterAppForeground() }
                heartbeat.check()
                scenario.onActivity { engine.get().release() }
            }
        } finally { scope.cancel(); heartbeat.close(); file.delete() }
    }

    @Test(timeout = 60_000) fun invalidFileReportsResponsiveError() {
        val invalid = File(context.cacheDir, "invalid-${System.nanoTime()}.mkv").apply { writeBytes(ByteArray(128*1024) { 0x55 }) }
        val engine = AtomicReference<HiPlayerEngine>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val heartbeat = Heartbeat()
        try {
            launch().use { scenario ->
                scenario.onActivity { activity ->
                    engine.set(HiPlayerEngine(activity, scope))
                    engine.get().prepareMedia(Uri.fromFile(invalid), mediaMimeType = "video/x-matroska")
                }
                await("Invalid file never reached an error state", 30_000) { engine.get().playerError.value != null }
                assertFalse(engine.get().isBuffering.value)
                heartbeat.check()
                scenario.onActivity { engine.get().release() }
            }
        } finally { heartbeat.close(); scope.cancel(); invalid.delete() }
    }

    @Test(timeout = 120_000) fun growingMkvWaitsAtEofResumesAndSeeksWithoutPlayerReplacement() {
        val complete = fixture("growing_test.mkv")
        val bytes = complete.readBytes()
        // Use real ffmpeg Cluster boundaries, never an arbitrary percentage split.
        val magic = byteArrayOf(0x1f,0x43,0xb6.toByte(),0x75)
        val clusters = (0..bytes.size-4).filter { i -> magic.indices.all { bytes[i+it] == magic[it] } }
        assertTrue("Fixture needs multiple clusters", clusters.size > 12)
        val partial = File(context.cacheDir,"growing-${System.nanoTime()}.mkv.part")
        partial.writeBytes(bytes.copyOf(clusters[6]))
        DownloadingFilesRegistry.markOwnedDownloading(partial.absolutePath, partial.length())
        val engine = AtomicReference<HiPlayerEngine>()
        val frames = AtomicLong()
        val failure = AtomicReference<String>()
        val heartbeat = Heartbeat()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        try {
            launch().use { scenario ->
                scenario.onActivity { activity ->
                    val playback = HiPlayerEngine(activity, scope)
                    engine.set(playback)
                    playback.getPlayer().addListener(object : Player.Listener {
                        override fun onRenderedFirstFrame() { frames.incrementAndGet() }
                        override fun onPlayerError(error: PlaybackException) { failure.set(error.errorCodeName) }
                    })
                    val view = SurfaceView(activity)
                    activity.addContentView(view, FrameLayout.LayoutParams(480,270))
                    playback.getPlayer().setVideoSurfaceView(view)
                    playback.prepareMedia(Uri.fromFile(partial), mediaMimeType = "video/x-matroska")
                }
                await("Growing prefix did not render") { frames.get() > 0 || failure.get() != null }
                assertNull(failure.get())
                assertFalse(engine.get().usesVlc.value)
                val originalPlayer = AtomicReference<androidx.media3.exoplayer.ExoPlayer>()
                scenario.onActivity { originalPlayer.set(engine.get().getPlayer()) }
                await("Prefix did not play before completion") { engine.get().currentPositionMs.value > 2_000 }
                // Let the reader hit actual temporary EOF, then append on this test thread.
                Thread.sleep(6_000)
                val beforeGrowth = engine.get().currentPositionMs.value
                partial.appendBytes(bytes.copyOfRange(clusters[6], bytes.size))
                DownloadingFilesRegistry.markOwnedDownloading(partial.absolutePath, partial.length())
                await("Playback did not resume from EOF") { engine.get().currentPositionMs.value > beforeGrowth + 1_000 || failure.get() != null }
                assertNull(failure.get())
                scenario.onActivity { assertSame(originalPlayer.get(), engine.get().getPlayer()); engine.get().seekTo(14_000) }
                await("Downloaded forward seek failed") { engine.get().lastRenderedPresentationMs in 13_800..19_000 }
                scenario.onActivity { engine.get().seekTo(2_000) }
                await("Downloaded backward seek failed") { engine.get().lastRenderedPresentationMs in 1_800..6_000 }
                scenario.onActivity {
                    val tracks = engine.get().availableAudioTracks.value
                    assertTrue("Fixture audio tracks missing", tracks.size >= 2)
                    engine.get().selectAudioTrack(tracks.last())
                    assertSame(originalPlayer.get(), engine.get().getPlayer())
                }
                val renamed = File(partial.parentFile, partial.name.removeSuffix(".part"))
                assertTrue(partial.renameTo(renamed))
                DownloadingFilesRegistry.completeOwned(partial.absolutePath, Uri.fromFile(renamed).toString())
                heartbeat.check()
                scenario.onActivity { engine.get().release() }
                renamed.delete()
            }
        } finally { scope.cancel(); heartbeat.close(); DownloadingFilesRegistry.completeOwned(partial.absolutePath); partial.delete(); complete.delete() }
    }
}
