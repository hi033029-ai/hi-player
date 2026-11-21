package com.example.player

import android.app.Activity
import android.view.SurfaceView
import android.view.ViewGroup
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.view.descendants
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.MainActivity
import com.example.model.AspectRatioMode
import com.example.viewmodel.PlayerViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

internal fun ComposeTestRule.tapPlayerButton(tag: String) {
    val button = onNodeWithTag(tag)
    if (!button.isDisplayed()) onNodeWithTag("player_gesture_surface").performTouchInput { click(center) }
    waitUntil(5_000) { button.isDisplayed() }
    button.performTouchInput { click(center) }
}

/** Checks pixels of the native video view for the 640 x 360 fixtures. */
internal fun <A : Activity> ComposeTestRule.checkResizeCycle(
    scenario: ActivityScenario<A>, engine: HiPlayerEngine,
) {
    val viewport = onNodeWithTag("player_gesture_surface").fetchSemanticsNode().size
    val modes = AspectRatioMode.entries
    val start = engine.aspectRatioMode.value
    for (step in 1..modes.size + 1) {
        val mode = modes[(start.ordinal + step) % modes.size]
        tapPlayerButton("bottom_aspect_button")
        waitUntil(5_000) {
            var matches = false
            scenario.onActivity {
                val view = (it.window.decorView as ViewGroup).descendants.filterIsInstance<SurfaceView>().first()
                val w = view.width
                val h = view.height
                val ratio = if (h > 0) w.toFloat() / h else 0f
                matches = engine.aspectRatioMode.value == mode && when (mode) {
                    AspectRatioMode.STRETCH -> abs(w - viewport.width) <= 2 && abs(h - viewport.height) <= 2
                    AspectRatioMode.FILL_CROP -> w >= viewport.width - 1 && h >= viewport.height - 1 &&
                        abs(ratio - 640f / 360f) < .02f
                    AspectRatioMode.ORIGINAL -> w <= 641 && h <= 361 && abs(ratio - 640f / 360f) < .02f
                    else -> (abs(w - viewport.width) <= 2 || abs(h - viewport.height) <= 2) &&
                        abs(ratio - (mode.targetRatio ?: (640f / 360f))) < .02f
                }
            }
            matches
        }
    }
}

/** Opens the indexed Movies fixture prepared by the launcher gate. */
@RunWith(AndroidJUnit4::class)
class LibraryPlaybackDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    private fun tapLabel(text: String) {
        val nodes = compose.onAllNodesWithText(text)
        fun visible() = nodes.fetchSemanticsNodes().indices.firstOrNull { nodes[it].isDisplayed() }
        compose.waitUntil(30_000) { visible() != null }
        nodes[checkNotNull(visible())].performTouchInput { click(center) }
    }

    @Test(timeout = 125_000) fun libraryTapAutoplaysAndTransportAndResizerRespond() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var engine: HiPlayerEngine
            scenario.onActivity { engine = ViewModelProvider(it)[PlayerViewModel::class.java].engine }
            tapLabel("HiPlayerLaunchCheck")
            tapLabel("launch-check.mp4")
            compose.waitUntil(35_000) {
                engine.isPlaying.value && engine.presentedPlayer.value != null && engine.currentPositionMs.value > 500
            }
            if (compose.onNodeWithText("Skip guide").isDisplayed()) tapLabel("Skip guide")
            compose.tapPlayerButton("bottom_play_pause_button")
            compose.waitUntil(8_000) { !engine.isPlaying.value }
            compose.checkResizeCycle(scenario, engine)
            val paused = engine.currentPositionMs.value
            compose.tapPlayerButton("bottom_play_pause_button")
            compose.waitUntil(8_000) { engine.isPlaying.value && engine.currentPositionMs.value > paused + 500 }
        }
    }
}
