package com.example.player

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import com.example.playback.createGrowingMkvClusterIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.onNodeWithContentDescription
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
                surface.performTouchInput { doubleClick(Offset(width * .78f, height * .4f)) }
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
