package com.example.browser.gecko

import android.content.ContentValues
import android.content.Context
import android.os.SystemClock
import android.provider.MediaStore
import android.view.SurfaceView
import androidx.annotation.OptIn
import androidx.activity.compose.setContent
import androidx.media3.common.C
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.model.VideoItem
import com.example.ui.screens.PlayerScreen
import com.example.ui.theme.HiPlayerTheme
import com.example.viewmodel.PlayerViewModel
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.player.DownloadingFilesRegistry
import com.example.player.HiPlayerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean

/** Runs against the app engine, a real MediaStore pending URI and a real video surface. */
@RunWith(AndroidJUnit4::class)
@OptIn(UnstableApi::class)
class GrowingPlaybackDeviceTest {
    @Test fun matroskaRendersBeforeCompletionAndContinuesAfterGrowth() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val media = context.assets.open("gecko_growing_test.mkv").use { it.readBytes() }
        val prefix = media.size * 3 / 4
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "hi-growing-test.mkv")
            put(MediaStore.Downloads.MIME_TYPE, "video/x-matroska")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Test destination unavailable")
        val key = uri.toString()
        val rendered = AtomicBoolean(false)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        var engine: HiPlayerEngine? = null
        var surfaceView: SurfaceView? = null
        try {
            context.contentResolver.openOutputStream(uri, "w")!!.use { output ->
                output.write(media, 0, prefix); output.flush()
                DownloadingFilesRegistry.markOwnedDownloading(key, prefix.toLong())
                ActivityScenario.launch(GeckoMigrationTestActivity::class.java).use { activity ->
                    activity.onActivity { host ->
                        surfaceView = SurfaceView(host)
                        host.setContentView(surfaceView)
                    }
                    waitFor("Surface did not become available") { surfaceView?.holder?.surface?.isValid == true }
                    activity.onActivity { host ->
                        val player = HiPlayerEngine(host, scope).also { engine = it }
                        player.getPlayer().addListener(object : Player.Listener {
                            override fun onRenderedFirstFrame() { rendered.set(true) }
                        })
                        player.getPlayer().setVideoSurfaceView(surfaceView)
                        player.prepareMedia(uri, mediaMimeType = "video/x-matroska", mediaTitle = "Growing fixture")
                    }
                    try {
                        waitFor("No video frame was rendered while the file was incomplete") {
                            rendered.get() && DownloadingFilesRegistry.isOwnedDownloading(key) && (engine?.currentPositionMs?.value ?: 0L) >= 500
                        }
                        assertTrue(prefix < media.size)
                        output.write(media, prefix, media.size - prefix); output.flush()
                        DownloadingFilesRegistry.markOwnedDownloading(key, media.size.toLong())
                        waitFor("Playback did not continue into the newly added data") { (engine?.currentPositionMs?.value ?: 0L) >= 14_500 }
                    } finally {
                        activity.onActivity { engine?.release(); engine = null }
                    }
                }
            }
        } finally {
            DownloadingFilesRegistry.completeOwned(key)
            scope.cancel()
            runCatching { context.contentResolver.delete(uri, null, null) }
        }
    }
    /** Check pixels on the real player screen, not just the decoder's frame callback. */
    @Test fun playerScreenShowsVideoWhileAudioAndDownloadContinue() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("first_use_tips", Context.MODE_PRIVATE).edit()
            .putBoolean("video_player_visual_guide_v2", true).commit()
        val media = context.assets.open("gecko_growing_test.mkv").use { it.readBytes() }
        val prefix = media.size * 3 / 4
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "hi-screen-growing-test.mkv")
            put(MediaStore.Downloads.MIME_TYPE, "video/x-matroska")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Test destination unavailable")
        val key = uri.toString()
        var model: PlayerViewModel? = null
        try {
            context.contentResolver.openOutputStream(uri, "w")!!.use { output ->
                output.write(media, 0, prefix); output.flush()
                DownloadingFilesRegistry.markOwnedDownloading(key, prefix.toLong())
                ActivityScenario.launch(GeckoMigrationTestActivity::class.java).use { activity ->
                    activity.onActivity { host ->
                        val vm = ViewModelProvider(host)[PlayerViewModel::class.java].also { model = it }
                        host.setContent {
                            HiPlayerTheme {
                                PlayerScreen(playerViewModel = vm, onBack = {}, onEnterPip = {})
                            }
                        }
                        vm.playVideo(VideoItem(uri = uri, title = "Growing fixture", path = key,
                            mimeType = "video/x-matroska", isPartialDownload = true))
                    }
                    try {
                        waitFor("Audio and video tracks were not selected while download was active") {
                            var selected = false
                            activity.onActivity {
                                val tracks = model!!.engine.getPlayer().currentTracks
                                selected = listOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO).all { type ->
                                    tracks.groups.any { group -> group.type == type &&
                                        (0 until group.length).any { group.isTrackSelected(it) } }
                                }
                            }
                            selected && DownloadingFilesRegistry.isOwnedDownloading(key) &&
                                (model?.engine?.currentPositionMs?.value ?: 0L) >= 1_000L
                        }
                        try {
                        waitFor("Player screen remained black while audio and the growing file played") {
                            val image = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                            try {
                                var red = 0; var green = 0
                                for (y in image.height / 3 until image.height * 2 / 3 step 4) {
                                    for (x in image.width / 8 until image.width * 7 / 8 step 4) {
                                        val c = image.getPixel(x, y)
                                        val r = android.graphics.Color.red(c)
                                        val g = android.graphics.Color.green(c)
                                        val b = android.graphics.Color.blue(c)
                                        if (r > 125 && r > g * 2 && r > b * 2) red++
                                        if (g > 90 && g > r * 2 && g > b * 2) green++
                                    }
                                }
                                red > 20 && green > 20
                            } finally { image.recycle() }
                        }
                        } catch (error: AssertionError) {
                            val image = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                            try {
                                context.getExternalFilesDir(null)?.resolve("growing-screen-diagnostic.png")
                                    ?.outputStream()?.use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                            } finally { image.recycle() }
                            activity.onActivity {
                                val player = model!!.engine.getPlayer()
                                android.util.Log.i("GrowingScreenTest", "state=${player.playbackState} playing=${player.isPlaying} position=${player.currentPosition} videoSize=${player.videoSize.width}x${player.videoSize.height} rendered=${player.videoDecoderCounters?.renderedOutputBufferCount}")
                            }
                            throw error
                        }
                    } finally {
                        activity.onActivity { model?.stopForNavigation() }
                    }
                }
            }
        } finally {
            DownloadingFilesRegistry.completeOwned(key)
            runCatching { context.contentResolver.delete(uri, null, null) }
        }
    }

    private fun waitFor(message: String, predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 60_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (predicate()) return
            SystemClock.sleep(100)
        }
        fail(message)
    }
}
