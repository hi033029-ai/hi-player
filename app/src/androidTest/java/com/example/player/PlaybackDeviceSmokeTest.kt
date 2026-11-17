package com.example.player

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import androidx.media3.common.Player
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Runs within the real app's process and exercises an actual SurfaceView and
 * native LibVLC/Media3 decoder. CI also runs separate Gecko browser tests.
 * These fixtures are small: a real 4K HDR phone is still needed for device-specific
 * Dolby Vision, DTS-HD/TrueHD, and 10-bit HDR validation.
 */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackDeviceSmokeTest {
    private fun launchSmokeActivity(): ActivityScenario<PlaybackSmokeActivity> {
        // ActivityScenario.launch(Class) can resolve against the instrumentation
        // .test package, even though this debug-only Activity belongs to the
        // actual app APK. Specify the target app package explicitly.
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(Intent.ACTION_MAIN).setClassName(
            target.packageName, PlaybackSmokeActivity::class.java.name,
        )
        return ActivityScenario.launch(intent)
    }

    private fun fixture(name: String, part: Boolean = false): File {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(context.cacheDir, "hiplayer-test-" + name + (if (part) ".part" else ""))
        context.assets.open(name).use { input ->
            file.outputStream().use { output ->
                if (part) {
                    val bytes = input.readBytes()
                    output.write(bytes, 0, (bytes.size * 9 / 10).coerceAtLeast(1))
                } else {
                    input.copyTo(output)
                }
            }
        }
        return file
    }

    @Test
    fun localMkvOpensOnRealLibVlcSurfaceWithoutBlockingUi() {
        playWithVlc(fixture("gecko_test.mkv"), growing = false)
    }

    @Test
    fun incompleteMkvPrefixDoesNotHangNativePlayback() {
        playWithVlc(fixture("gecko_test.mkv", part = true), growing = true)
    }

    private fun playWithVlc(file: File, growing: Boolean) {
        val arrived = CountDownLatch(1)
        val mainResponsive = CountDownLatch(1)
        val error = AtomicReference<String?>(null)
        val engine = AtomicReference<VlcPlaybackEngine>()
        try {
            launchSmokeActivity().use { scenario ->
                scenario.onActivity { activity ->
                    val playback = VlcPlaybackEngine(
                        activity,
                        onUpdate = { state -> if (state.playing && state.positionMs > 0L) arrived.countDown() },
                        onTracks = { _, _ -> },
                        onVideoLayout = { width, height -> if (width > 0 && height > 0) arrived.countDown() },
                        onFirstFrame = { arrived.countDown() },
                        onSeekUnavailable = { error.set("Native seek unavailable") },
                        onEnd = {},
                        onError = { error.set(it); arrived.countDown() },
                    )
                    engine.set(playback)
                    val view = SurfaceView(activity)
                    view.holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            playback.attachSurface(view)
                        }
                        override fun surfaceChanged(
                            holder: SurfaceHolder, format: Int, width: Int, height: Int,
                        ) {
                            playback.updateWindowSize(view)
                        }
                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            playback.detachSurface(view)
                        }
                    })
                    activity.addContentView(
                        view, FrameLayout.LayoutParams(280, 160),
                    )
                    playback.open(Uri.fromFile(file), 0L, true, growing)
                    Handler(Looper.getMainLooper()).post { mainResponsive.countDown() }
                }
                assertTrue("Native opening blocked the UI thread", mainResponsive.await(3, TimeUnit.SECONDS))
                assertTrue(
                    "LibVLC did not initialize its video output (error: " + error.get() + ")",
                    arrived.await(25, TimeUnit.SECONDS),
                )
                assertTrue("LibVLC playback error: " + error.get(), error.get() == null)
                scenario.onActivity { engine.get()?.release() }
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun ordinaryMp4ProducesRealMedia3VideoFrame() {
        val file = fixture("gecko_test.mp4")
        val arrived = CountDownLatch(1)
        val failure = AtomicReference<String?>(null)
        val engineRef = AtomicReference<HiPlayerEngine>()
        val scopeRef = AtomicReference<CoroutineScope>()
        try {
            launchSmokeActivity().use { scenario ->
                scenario.onActivity { activity ->
                    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
                    val engine = HiPlayerEngine(activity, scope)
                    scopeRef.set(scope)
                    engineRef.set(engine)
                    val videoPlayer = engine.getPlayer()
                    videoPlayer.addListener(object : Player.Listener {
                        override fun onRenderedFirstFrame() { arrived.countDown() }
                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                            failure.set(error.message)
                            arrived.countDown()
                        }
                    })
                    val view = SurfaceView(activity)
                    activity.addContentView(view, FrameLayout.LayoutParams(280, 160))
                    videoPlayer.setVideoSurfaceView(view)
                    engine.prepareMedia(Uri.fromFile(file), mediaMimeType = "video/mp4")
                }
                assertTrue("Media3 did not render a first MP4 frame", arrived.await(25, TimeUnit.SECONDS))
                assertTrue("Media3 error: " + failure.get(), failure.get() == null)
                scenario.onActivity {
                    engineRef.get()?.release()
                    scopeRef.get()?.cancel()
                }
            }
        } finally {
            file.delete()
        }
    }
}
