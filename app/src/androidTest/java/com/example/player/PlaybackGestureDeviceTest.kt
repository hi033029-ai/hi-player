package com.example.player

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
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
                surface.performTouchInput { swipe(Offset(width*.22f,height*.48f),Offset(width*.22f,height*.25f),500) }
                scenario.onActivity { assertTrue("Left swipe did not change actual window brightness",it.window.attributes.screenBrightness > .45f) }
                val volumeBefore = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                surface.performTouchInput { swipe(Offset(width*.78f,height*.48f),Offset(width*.78f,height*.25f),500) }
                assertTrue("Right swipe did not change actual AudioManager volume",audio.getStreamVolume(AudioManager.STREAM_MUSIC) > volumeBefore)
                val before = engine.currentPositionMs.value
                surface.performTouchInput { swipe(Offset(width*.35f,height*.4f),Offset(width*.7f,height*.4f),400) }
                compose.waitUntil(20_000) { engine.currentPositionMs.value > before + 5_000 }
                val forward = engine.currentPositionMs.value
                surface.performTouchInput { swipe(Offset(width*.7f,height*.4f),Offset(width*.35f,height*.4f),400) }
                compose.waitUntil(20_000) { engine.currentPositionMs.value < forward - 5_000 }
                assertNull("Swiping must not leave a stale preview",activity.model.scrubTimeMs.value)
                assertNull(engine.playerError.value)

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
                    assertFalse("Grading a paused video must not start audio", engine.isPlaying.value)
                    assertNull("VLC to graded Media3 transition failed",engine.playerError.value)
                }
            }
        } finally { audio.setStreamVolume(AudioManager.STREAM_MUSIC,originalVolume,0); file.delete() }
    }
}
