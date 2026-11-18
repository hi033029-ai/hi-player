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
        val file = File(context.cacheDir,name)
        context.assets.open(name).use { input -> file.outputStream().use { input.copyTo(it) } }
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val originalVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        try {
            ActivityScenario.launch<PlaybackGestureActivity>(Intent(context,PlaybackGestureActivity::class.java)
                .putExtra("fixture",file.absolutePath)).use { scenario ->
                lateinit var activity: PlaybackGestureActivity
                scenario.onActivity { activity = it }
                val engine = activity.model.engine
                compose.waitUntil(40_000) {
                    if (vlc) engine.vlcFirstFrame.value && engine.currentPositionMs.value > 1_000
                    else engine.renderedFrameSequence > 0 && engine.currentPositionMs.value > 1_000
                }
                assertEquals("Exercise the intended backend",vlc,engine.usesVlc.value)
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
            }
        } finally { audio.setStreamVolume(AudioManager.STREAM_MUSIC,originalVolume,0); file.delete() }
    }
}
