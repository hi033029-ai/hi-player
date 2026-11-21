package com.example.player

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.view.KeyEvent
import android.view.SurfaceView
import android.view.ViewGroup
import androidx.core.view.descendants
import androidx.media3.ui.PlayerView
import com.example.data.PlayerPreferencesRepository
import com.example.data.dataStore
import com.example.data.PlaybackLoudness
import com.example.model.AspectRatioMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import com.example.playback.createGrowingMkvClusterIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Injects Android touch events into the FULL player UI over real decoder surfaces. */
@RunWith(AndroidJUnit4::class)
class PlaybackGestureDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test(timeout = 100_000) fun completedMp4SwipesReachMedia3AndDeviceControls() = exercise("gecko_test.mp4",false)
    @Test(timeout = 100_000) fun completedMkvSwipesReachLibVlcAndDeviceControls() = exercise("gecko_test.mkv",true)
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Test(timeout = 100_000) fun partialMkvSwipesAndDoubleTapsSeekBothWaysBeforeDownloadCompletes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val full = File.createTempFile("gesture-source-", ".mkv", context.cacheDir)
        val partial = File.createTempFile("gesture-prefix-", ".mkv.part", context.cacheDir)
        try {
            context.assets.open("growing_test.mkv").use { input -> full.outputStream().use { input.copyTo(it) } }
            val points = createGrowingMkvClusterIndex(context, Uri.fromFile(full), { false }).use { it.scanAvailable() }
            val split = points.indexOfFirst { it.timeUs >= 16_000_000 }
            assertTrue("Fixture must contain multiple seekable clusters", split > 1)
            val frontier = points[split - 1].timeUs / 1000
            partial.writeBytes(full.readBytes().copyOf(points[split].position.toInt()))
            DownloadingFilesRegistry.markOwnedDownloading(partial.absolutePath, partial.length())
            ActivityScenario.launch<PlaybackGestureActivity>(Intent(context, PlaybackGestureActivity::class.java)
                .putExtra("fixture", partial.absolutePath)).use { scenario ->
                lateinit var engine: HiPlayerEngine
                scenario.onActivity { engine = it.model.engine }
                compose.waitUntil(40_000) { engine.renderedFrameSequence > 0 && engine.currentPositionMs.value > 250 }
                scenario.onActivity { engine.pause() }
                assertFalse(engine.usesVlc.value)
                val surface = compose.onNodeWithTag("player_gesture_surface")
                fun seekGesture(range: LongRange, gesture: androidx.compose.ui.test.TouchInjectionScope.() -> Unit) {
                    val sequence = engine.renderedFrameSequence
                    surface.performTouchInput(gesture)
                    compose.waitUntil(12_000) {
                        engine.playerError.value != null ||
                            (engine.renderedFrameSequence > sequence && engine.currentPositionMs.value in range)
                    }
                    assertNull(engine.playerError.value)
                }
                val start = engine.currentPositionMs.value
                seekGesture((start + 8_500)..(start + 11_500)) { doubleClick(Offset(width * .78f, height * .4f)) }
                seekGesture(0L..(start + 1_000)) { doubleClick(Offset(width * .22f, height * .4f)) }
                seekGesture((frontier - 1_500)..(frontier + 500)) {
                    down(Offset(width * .2f, height * .4f))
                    moveTo(Offset(width * .85f, height * .4f), delayMillis = 180)
                    up()
                }
                seekGesture(0L..2_000L) {
                    down(Offset(width * .85f, height * .4f))
                    moveTo(Offset(width * .2f, height * .4f), delayMillis = 180)
                    up()
                }
                scenario.onActivity { engine.play() }
                compose.waitUntil(10_000) { engine.isPlaying.value && engine.currentPositionMs.value > 1_000 }
                assertNull(engine.playerError.value)
            }
        } finally {
            DownloadingFilesRegistry.completeOwned(partial.absolutePath)
            partial.delete()
            full.delete()
        }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Test(timeout = 100_000) fun vividPartialVideoResizesAndVolumeKeysPreservePicture() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = PlayerPreferencesRepository(context)
        val saved = runBlocking { context.dataStore.data.first() }
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val originalVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val partial = File.createTempFile("graded-prefix-", ".mkv.part", context.cacheDir)
        try {
            val bytes = context.assets.open("growing_test.mkv").use { it.readBytes() }
            partial.writeBytes(bytes.copyOf(bytes.size / 2))
            DownloadingFilesRegistry.markOwnedDownloading(partial.absolutePath, partial.length())
            runBlocking { preferences.setPictureMode(ColorPresets.VIVID_HDR) }
            PlaybackLoudness.remember(context, 75)
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
            ActivityScenario.launch<PlaybackGestureActivity>(Intent(context, PlaybackGestureActivity::class.java)
                .putExtra("fixture", partial.absolutePath)).use { scenario ->
                lateinit var host: PlaybackGestureActivity
                scenario.onActivity { host = it }
                val engine = host.model.engine
                compose.waitUntil(40_000) { engine.renderedFrameSequence > 0 && engine.currentPositionMs.value > 250 }
                assertEquals(ColorPresets.VIVID_HDR, engine.hdrColorPreset.value)
                assertEquals(0, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
                lateinit var view: SurfaceView
                scenario.onActivity {
                    val playerView = (it.window.decorView as ViewGroup).descendants.filterIsInstance<PlayerView>().first()
                    view = playerView.videoSurfaceView as SurfaceView
                }
                fun selectRatio(mode: AspectRatioMode) {
                    scenario.onActivity { it.model.setAspectRatio(mode) }
                    compose.waitUntil(8_000) {
                        var matches = false
                        scenario.onActivity {
                            val buffer = view.holder.surfaceFrame
                            matches = view.height > 0 && buffer.height() > 0 &&
                                kotlin.math.abs(view.width.toFloat() / view.height - mode.targetRatio!!) < .02f &&
                                kotlin.math.abs(buffer.width().toFloat() / buffer.height() - 640f / 360f) < .02f
                        }
                        matches
                    }
                }
                selectRatio(AspectRatioMode.IMAX_ORIGINAL)
                selectRatio(AspectRatioMode.CINEMA_21_9)
                fun pictureVisible(): Boolean = runBlocking {
                    val bitmap = withContext(Dispatchers.Main) { com.example.ui.copyPictureFrame(view) } ?: return@runBlocking false
                    try {
                        (1..7).any { x -> (1..7).any { y ->
                            val color = bitmap.getPixel(bitmap.width * x / 8, bitmap.height * y / 8)
                            android.graphics.Color.red(color) > 55 || android.graphics.Color.green(color) > 55 ||
                                android.graphics.Color.blue(color) > 55
                        } }
                    } finally { bitmap.recycle() }
                }
                compose.waitUntil(8_000) { pictureVisible() }
                scenario.onActivity { it.model.selectPictureMode(ColorPresets.CINEMATIC) }
                compose.waitUntil(8_000) { engine.hdrColorPreset.value == ColorPresets.CINEMATIC && pictureVisible() }
                assertEquals("Changing the preset raised media volume", 0, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
                var brightness = 0f
                scenario.onActivity {
                    brightness = it.window.attributes.screenBrightness
                    for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
                        assertTrue(it.dispatchKeyEvent(KeyEvent(action, KeyEvent.KEYCODE_VOLUME_UP)))
                    }
                }
                compose.waitUntil(5_000) { audio.getStreamVolume(AudioManager.STREAM_MUSIC) > 0 }
                val raised = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                compose.waitUntil(8_000) { pictureVisible() }
                scenario.onActivity {
                    assertEquals(brightness, it.window.attributes.screenBrightness, .001f)
                    for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
                        assertTrue(it.dispatchKeyEvent(KeyEvent(action, KeyEvent.KEYCODE_VOLUME_DOWN)))
                    }
                }
                compose.waitUntil(5_000) { audio.getStreamVolume(AudioManager.STREAM_MUSIC) < raised }
                assertNull(engine.playerError.value)
            }
        } finally {
            runBlocking { context.dataStore.updateData { saved } }
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
            PlaybackLoudness.capture(context)
            DownloadingFilesRegistry.completeOwned(partial.absolutePath)
            partial.delete()
        }
    }

    private fun exercise(name: String, vlc: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir,"${System.nanoTime()}-$name")
        context.assets.open(name).use { input -> file.outputStream().use { input.copyTo(it) } }
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val originalVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        try {
            ActivityScenario.launch<PlaybackGestureActivity>(Intent(context,PlaybackGestureActivity::class.java)
                .putExtra("fixture",file.absolutePath).putExtra("saved_at_end",true)).use { scenario ->
                lateinit var activity: PlaybackGestureActivity
                scenario.onActivity { activity = it }
                val engine = activity.model.engine
                compose.waitUntil(40_000) {
                    if (vlc) engine.vlcFirstFrame.value && engine.currentPositionMs.value > 1_000
                    else engine.renderedFrameSequence > 0 && engine.currentPositionMs.value > 1_000
                }
                assertEquals("Exercise the intended backend",vlc,engine.usesVlc.value)
                assertTrue("Finished videos must reopen near the beginning",engine.currentPositionMs.value < 8_000)
                scenario.onActivity {
                    engine.pause()
                    it.model.onBrightnessGestureLevel(.3f)
                    it.model.onVolumeGestureLevel(.25f)
                }
                compose.waitForIdle()
                val surface = compose.onNodeWithTag("player_gesture_surface")
                surface.performTouchInput {
                    down(Offset(width * .22f, height * .48f))
                    moveTo(Offset(width * .22f, height * .25f), delayMillis = 180)
                }
                compose.onNodeWithContentDescription("Brightness").assertIsDisplayed()
                surface.performTouchInput { up() }
                scenario.onActivity { assertTrue("Left swipe did not change actual window brightness",it.window.attributes.screenBrightness > .45f) }
                val volumeBefore = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                surface.performTouchInput {
                    down(Offset(width * .78f, height * .48f))
                    moveTo(Offset(width * .78f, height * .25f), delayMillis = 180)
                }
                compose.onNodeWithContentDescription("Volume").assertIsDisplayed()
                surface.performTouchInput { up() }
                assertTrue("Right swipe did not change actual AudioManager volume",audio.getStreamVolume(AudioManager.STREAM_MUSIC) > volumeBefore)
                val before = engine.currentPositionMs.value
                surface.performTouchInput {
                    down(Offset(width * .35f, height * .4f))
                    moveTo(Offset(width * .7f, height * .4f), delayMillis = 180)
                }
                compose.onNodeWithTag("seek_preview_hud").assertIsDisplayed()
                surface.performTouchInput { up() }
                compose.waitUntil(20_000) { engine.currentPositionMs.value > before + 5_000 }
                val forward = engine.currentPositionMs.value
                surface.performTouchInput {
                    down(Offset(width * .7f, height * .4f))
                    moveTo(Offset(width * .35f, height * .4f), delayMillis = 180)
                }
                compose.onNodeWithTag("seek_preview_hud").assertIsDisplayed()
                val reversePreview = activity.model.scrubTimeMs.value
                assertNotNull("Reverse swipe did not publish a seek target", reversePreview)
                assertTrue("Reverse preview did not move earlier: $reversePreview after $forward", reversePreview!! < forward - 5_000)
                surface.performTouchInput { up() }
                compose.waitUntil(20_000) { engine.currentPositionMs.value < forward - 5_000 }
                assertNull("Swiping must not leave a stale preview",activity.model.scrubTimeMs.value)
                assertNull(engine.playerError.value)

                val beforeSkip = engine.currentPositionMs.value
                compose.waitForIdle()
                compose.mainClock.autoAdvance = false
                try {
                    surface.performTouchInput { doubleClick(Offset(width * .78f, height * .4f)) }
                    compose.mainClock.advanceTimeUntil(timeoutMillis = 2_000) {
                        compose.onAllNodesWithTag("double_tap_hud").fetchSemanticsNodes()
                            .any { it.size.width > 0 && it.size.height > 0 }
                    }
                    val hud = compose.onNodeWithTag("double_tap_hud").assertIsDisplayed()
                    assertTrue("Double-tap indicator exceeded the compact height",
                        hud.fetchSemanticsNode().size.height <= with(compose.density) { 40.dp.roundToPx() })
                    compose.onNodeWithText("+10s").assertIsDisplayed()
                    compose.mainClock.advanceTimeBy(800)
                    compose.onNodeWithTag("double_tap_hud").assertDoesNotExist()
                } finally {
                    compose.mainClock.autoAdvance = true
                }
                val expectedSkip = (beforeSkip + 8_500)..(beforeSkip + 11_500)
                try {
                    compose.waitUntil(20_000) { engine.currentPositionMs.value in expectedSkip }
                } catch (timeout: androidx.compose.ui.test.ComposeTimeoutException) {
                    throw AssertionError("Paused double-tap from $beforeSkip expected $expectedSkip, " +
                        "observed ${engine.currentPositionMs.value}, duration=${engine.durationMs.value}, " +
                        "playing=${engine.isPlaying.value}, vlc=${engine.usesVlc.value}", timeout)
                }
                assertFalse("A paused double-tap seek started playback", engine.isPlaying.value)

                // Reach a genuine decoder EOF, then use the same public action
                // as the Play control. A playWhenReady flag alone cannot replay.
                scenario.onActivity { engine.seekTo(31_300); engine.play() }
                compose.waitUntil(25_000) { engine.hasPlaybackEnded }
                val frameBeforeReplay = engine.renderedFrameSequence
                scenario.onActivity { engine.togglePlayPause() }
                compose.waitUntil(20_000) {
                    engine.isPlaying.value && engine.currentPositionMs.value in 200..5_000 &&
                        (if (vlc) engine.vlcFirstFrame.value else engine.renderedFrameSequence > frameBeforeReplay)
                }
                assertNull("Replay failed",engine.playerError.value)
                if (vlc) {
                    // The first grade can reuse the dormant Media3 player. Its
                    // native tunneling preference must not enter a GL pipeline.
                    val beforeGrade = engine.renderedFrameSequence
                    val positionBeforeGrade = engine.currentPositionMs.value
                    scenario.onActivity {
                        engine.pause()
                        engine.applyConfiguration(hwDecoding = true, remuxUltraBuffer = false, tunneling = true)
                        engine.setHdrEnhanceActive(true)
                        engine.setHdrColorPreset(ColorPresets.NATURAL_HDR)
                    }
                    compose.waitUntil(25_000) {
                        !engine.usesVlc.value && engine.renderedFrameSequence > beforeGrade &&
                            kotlin.math.abs(engine.currentPositionMs.value - positionBeforeGrade) < 1_000
                    }
                    assertFalse("Paused output was reported as playing", engine.isPlaying.value)
                    scenario.onActivity {
                        assertFalse("Grading a paused video must not request playback",engine.getPlayer().playWhenReady)
                        assertFalse("Grading a paused video must not start audio",engine.getPlayer().isPlaying)
                    }
                    assertNull("VLC to graded Media3 transition failed",engine.playerError.value)
                }
            }
        } finally { audio.setStreamVolume(AudioManager.STREAM_MUSIC,originalVolume,0); file.delete() }
    }
}
