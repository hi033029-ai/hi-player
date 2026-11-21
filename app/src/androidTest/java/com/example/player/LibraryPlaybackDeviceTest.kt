package com.example.player

import android.app.Activity
import android.view.SurfaceView
import android.view.ViewGroup
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.core.view.descendants
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.MainActivity
import com.example.model.AspectRatioMode
import com.example.viewmodel.PlayerViewModel
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
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
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation

    private fun awaitStep(description: String, condition: () -> Boolean) {
        instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\nChecking $description\n") })
        val deadline = SystemClock.elapsedRealtime() + 25_000
        while (!condition()) {
            check(SystemClock.elapsedRealtime() < deadline) { "Timed out waiting for $description" }
            SystemClock.sleep(100)
        }
    }

    private fun bounds(label: String): Rect? {
        fun locate(node: AccessibilityNodeInfo): Rect? {
            if (node.isVisibleToUser && (node.text?.toString() == label || node.contentDescription?.toString() == label)) {
                val rect = Rect().also(node::getBoundsInScreen)
                if (!rect.isEmpty) return rect
            }
            var lowest: Rect? = null
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                val hit = locate(child) ?: continue
                if (hit.centerY() > (lowest?.centerY() ?: Int.MIN_VALUE)) lowest = hit
            }
            return lowest
        }
        return automation.rootInActiveWindow?.let(::locate)
    }

    private fun tap(x: Int, y: Int) {
        val down = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x.toFloat(), y.toFloat(), 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { check(automation.injectInputEvent(event, true)) } finally { event.recycle() }
            if (action == MotionEvent.ACTION_DOWN) SystemClock.sleep(40)
        }
    }

    private fun tapLabel(label: String) {
        var point: Rect? = null
        awaitStep("visible $label") { point = bounds(label); point != null }
        val target = checkNotNull(point)
        tap(target.centerX(), target.centerY())
    }

    @Test fun libraryTapAutoplaysAndTransportAndResizerRespond() {
        val context = instrumentation.targetContext
        context.startActivity(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        tapLabel("HiPlayerLaunchCheck")
        tapLabel("launch-check.mp4")
        lateinit var host: MainActivity
        lateinit var engine: HiPlayerEngine
        instrumentation.runOnMainSync {
            host = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<MainActivity>().single()
            engine = ViewModelProvider(host)[PlayerViewModel::class.java].engine
        }
        awaitStep("autoplay and advancing decoded video") {
            engine.isPlaying.value && engine.presentedPlayer.value != null && engine.currentPositionMs.value > 500
        }
        // Cold launch starts with the first-use guide over the transport.
        tapLabel("Skip guide")
        if (bounds("Pause") == null) {
            val window = Rect().also { requireNotNull(automation.rootInActiveWindow).getBoundsInScreen(it) }
            tap(window.centerX(), window.centerY())
        }
        tapLabel("Pause")
        awaitStep("paused playback") { !engine.isPlaying.value }
        val modes = AspectRatioMode.entries
        val start = engine.aspectRatioMode.value
        for (step in 1..modes.size + 1) {
            val current = modes[(start.ordinal + step - 1) % modes.size]
            val next = modes[(start.ordinal + step) % modes.size]
            tapLabel(current.displayName)
            awaitStep("native surface for ${next.displayName}") {
                var matches = false
                instrumentation.runOnMainSync {
                    val view = (host.window.decorView as ViewGroup).descendants.filterIsInstance<SurfaceView>().first()
                    val ratio = next.targetRatio
                    matches = engine.aspectRatioMode.value == next && view.height > 0 &&
                        (ratio == null || abs(view.width.toFloat() / view.height - ratio) < .02f)
                }
                matches
            }
        }
        val paused = engine.currentPositionMs.value
        tapLabel("Play")
        awaitStep("resumed playback") { engine.isPlaying.value && engine.currentPositionMs.value > paused + 500 }
        instrumentation.runOnMainSync { host.finish() }
    }
}
