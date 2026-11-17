package com.example.player

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import com.example.playback.createGrowingMkvClusterIndex
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { engine.get()?.release() }
            heartbeat.close(); file.delete()
        }
    }

    @Test(timeout = 100_000) fun ordinaryMp4ProducesRealMedia3VideoFrame() = playMedia3("gecko_test.mp4", "video/mp4")
    @Test(timeout = 100_000) fun completedMkvThroughAppRoutingRenders() = playMedia3("gecko_test.mkv", "video/x-matroska")
    @Test(timeout = 100_000) fun webmRenders() = playMedia3("playback_test.webm", "video/webm")

    internal fun playMedia3(name: String, mime: String, grade: Boolean = mime.contains("matroska"), expectedHdr: Boolean? = null) {
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
                    if (grade) {
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
                expectedHdr?.let {
                    assertEquals("Input HDR metadata lost", it, engine.get().isHdrContent.value)
                    assertNotEquals("No decoder output metadata observed", "Unknown", engine.get().lastCodecOutputColor)
                }
                scenario.onActivity { engine.get().seekTo(12_000) }
                await("Forward seek did not decode") { engine.get().lastRenderedPresentationMs in 11_800..18_000 }
                scenario.onActivity { engine.get().seekTo(3_000) }
                await("Backward seek did not decode") { engine.get().lastRenderedPresentationMs in 2_800..7_000 }
                scenario.onActivity { engine.get().pauseForAppBackground(); engine.get().resumeAfterAppForeground() }
                heartbeat.check()
                scenario.onActivity { engine.get().release() }
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { engine.get()?.release() }
            scope.cancel(); heartbeat.close(); file.delete()
        }
    }

    @Test(timeout = 100_000) fun sdrPresetChangesRenderedPixelsAndOriginalRestoresThem() {
        // Pixel comparison proves the SDR effect is connected. It is not an HDR
        // accuracy test; physical HDR output metadata/reference checks are separate.
        val file = fixture("gecko_test.mp4")
        val engine = AtomicReference<HiPlayerEngine>()
        val surface = AtomicReference<SurfaceView>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val frame = AtomicLong(-1)
        fun capture(): android.graphics.Bitmap {
            val bitmap = android.graphics.Bitmap.createBitmap(480,270,android.graphics.Bitmap.Config.ARGB_8888)
            val done = CountDownLatch(1)
            val result = java.util.concurrent.atomic.AtomicInteger(-1)
            Handler(Looper.getMainLooper()).post {
                android.view.PixelCopy.request(surface.get(), bitmap, { result.set(it); done.countDown() }, Handler(Looper.getMainLooper()))
            }
            assertTrue("PixelCopy did not complete", done.await(3, TimeUnit.SECONDS))
            assertEquals(android.view.PixelCopy.SUCCESS, result.get())
            return bitmap
        }
        fun difference(a: android.graphics.Bitmap, b: android.graphics.Bitmap): Double {
            var sum = 0L
            var count = 0
            for (y in 10 until 260 step 5) for (x in 10 until 470 step 5) {
                val p = a.getPixel(x,y); val q = b.getPixel(x,y)
                sum += kotlin.math.abs(android.graphics.Color.red(p)-android.graphics.Color.red(q))
                sum += kotlin.math.abs(android.graphics.Color.green(p)-android.graphics.Color.green(q))
                sum += kotlin.math.abs(android.graphics.Color.blue(p)-android.graphics.Color.blue(q))
                count += 3
            }
            return sum.toDouble()/count
        }
        try {
            launch().use { scenario ->
                scenario.onActivity { activity ->
                    val playback = HiPlayerEngine(activity, scope)
                    engine.set(playback)
                    val view = SurfaceView(activity); surface.set(view)
                    activity.addContentView(view, FrameLayout.LayoutParams(480,270))
                    scope.launch {
                        playback.playerInstance.collect { player ->
                            player?.setVideoSurfaceView(view)
                            player?.setVideoFrameMetadataListener { us, _, _, _ -> frame.set(us/1000) }
                        }
                    }
                    playback.prepareMedia(Uri.fromFile(file), autoPlay = false, mediaMimeType = "video/mp4")
                }
                await("No paused SDR frame") { frame.get() >= 0 }
                fun atReferenceFrame(preset: ColorPreset) {
                    frame.set(-1)
                    scenario.onActivity { engine.get().setHdrEnhanceActive(true); engine.get().setHdrColorPreset(preset); engine.get().seekTo(1_000) }
                    await("Preset did not decode preroll") { frame.get() in 1_000..1_100 }
                    frame.set(-1)
                    scenario.onActivity { engine.get().seekTo(3_000) }
                    await("Preset did not render the reference frame") { frame.get() in 3_000..3_100 }
                    Thread.sleep(200) // PixelCopy consumes the frame after its release timestamp.
                }
                atReferenceFrame(ColorPresets.HDR_DISABLED)
                val original = capture()
                atReferenceFrame(ColorPresets.VIVID_HDR)
                val graded = capture()
                assertTrue("Preset did not change rendered SDR pixels", difference(original,graded) > 2.0)
                atReferenceFrame(ColorPresets.HDR_DISABLED)
                val restored = capture()
                assertTrue("Original retained a color filter", difference(original,restored) < 2.0)
                original.recycle(); graded.recycle(); restored.recycle()
                scenario.onActivity { engine.get().release() }
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { engine.get()?.release() }
            scope.cancel(); file.delete()
        }
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
                    val view = SurfaceView(activity)
                    activity.addContentView(view, FrameLayout.LayoutParams(480,270))
                    engine.get().getPlayer().setVideoSurfaceView(view)
                    engine.get().prepareMedia(Uri.fromFile(invalid), mediaMimeType = "video/x-matroska")
                }
                await("Invalid file never reached an error state", 30_000) { engine.get().playerError.value != null }
                assertFalse(engine.get().isBuffering.value)
                heartbeat.check()
                scenario.onActivity { engine.get().release() }
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { engine.get()?.release() }
            heartbeat.close(); scope.cancel(); invalid.delete()
        }
    }

    @Test(timeout = 120_000) fun growingMkvWaitsAtEofResumesAndSeeksWithoutPlayerReplacement() {
        val complete = fixture("growing_test.mkv")
        val bytes = complete.readBytes()
        // Cluster count is not elapsed time: FFmpeg also limits Cluster byte
        // size and may emit several Clusters per second. Split on a verified
        // video-keyframe timestamp, not magic-byte counting or byte percentages.
        val index = createGrowingMkvClusterIndex(context, Uri.fromFile(complete), { false })
        val split = try { index.scanAvailable().first { it.timeUs >= 6_000_000 }.position.toInt() }
                    finally { index.close() }
        val partial = File(context.cacheDir,"growing-${System.nanoTime()}.mkv.part")
        partial.writeBytes(bytes.copyOf(split))
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
                partial.appendBytes(bytes.copyOfRange(split, bytes.size))
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
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { engine.get()?.release() }
            scope.cancel(); heartbeat.close(); DownloadingFilesRegistry.completeOwned(partial.absolutePath); partial.delete(); complete.delete()
        }
    }
}
