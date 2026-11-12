package com.example.browser.gecko

import android.content.ContentValues
import android.content.Context
import android.os.SystemClock
import android.provider.MediaStore
import android.view.SurfaceView
import androidx.annotation.OptIn
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
    private fun waitFor(message: String, predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 60_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (predicate()) return
            SystemClock.sleep(100)
        }
        fail(message)
    }
}
