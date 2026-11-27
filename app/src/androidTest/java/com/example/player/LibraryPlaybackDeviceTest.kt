package com.example.player

import android.app.Activity
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import kotlin.math.roundToInt
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.core.view.descendants
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.MainActivity
import com.example.model.AspectRatioMode
import com.example.viewmodel.PlayerViewModel
import android.content.ContentUris
import android.content.Intent
import android.graphics.Color
import android.provider.MediaStore
import com.example.db.HiPlayerDatabase
import com.example.db.VideoEntity
import kotlinx.coroutines.runBlocking
import org.junit.FixMethodOrder
import org.junit.runners.MethodSorters
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

/** Android's measured size excludes the native transforms applied to the SurfaceView or PlayerView. */
internal fun SurfaceView.pictureBounds(): Rect {
    val origin = IntArray(2).also(::getLocationOnScreen)
    var sx = 1f
    var sy = 1f
    var ancestor: View? = this
    while (ancestor != null) {
        sx *= ancestor.scaleX
        sy *= ancestor.scaleY
        ancestor = ancestor.parent as? View
    }
    return Rect(origin[0], origin[1], origin[0] + (width * sx).roundToInt(), origin[1] + (height * sy).roundToInt())
}

/** The colour bars start at the picture edge and the letterbox above it is black. */
private fun visiblePictureEdge(rect: Rect): Boolean {
    val frame = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return false
    try {
        if (rect.top < 40 || rect.top + 30 >= frame.height) return true
        fun colouredRow(y: Int) = (2..8).count { index ->
            val x = (rect.left + rect.width() * index / 10).coerceIn(0, frame.width - 1)
            val pixel = frame.getPixel(x, y)
            val r = android.graphics.Color.red(pixel)
            val g = android.graphics.Color.green(pixel)
            val b = android.graphics.Color.blue(pixel)
            maxOf(r, g, b) - minOf(r, g, b) > 45
        }
        return colouredRow(rect.top + 24) >= 3 && colouredRow(rect.top - 24) <= 1
    } finally { frame.recycle() }
}

/** Checks on-screen picture geometry of the 640 x 360 fixtures. */
internal fun <A : Activity> ComposeTestRule.checkResizeCycle(
    scenario: ActivityScenario<A>, engine: HiPlayerEngine,
) {
    val viewport = onNodeWithTag("player_gesture_surface").fetchSemanticsNode().size
    val modes = AspectRatioMode.entries
    val start = engine.aspectRatioMode.value
    for (step in 1..modes.size + 1) {
        val mode = modes[(start.ordinal + step) % modes.size]
        tapPlayerButton("bottom_aspect_button")
        var observed = ""
        var bounds = Rect()
        try { waitUntil(5_000) {
            var matches = false
            scenario.onActivity {
                val view = (it.window.decorView as ViewGroup).descendants.filterIsInstance<SurfaceView>().first()
                bounds = view.pictureBounds()
                val w = bounds.width()
                val h = bounds.height()
                val ratio = if (h > 0) w.toFloat() / h else 0f
                observed = "mode=${engine.aspectRatioMode.value} view=${w}x$h viewport=$viewport " +
                    "buffer=${view.holder.surfaceFrame} vlcSource=${engine.vlcVideoSize.value}"
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
            if (mode == AspectRatioMode.IMAX_ORIGINAL || mode == AspectRatioMode.CINEMA_21_9) {
                waitUntil(5_000) { visiblePictureEdge(bounds) }
            }
        } catch (timeout: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("Expected resize to $mode. Observed $observed", timeout)
        }
    }
}

/** Opens the indexed Movies fixture prepared by the launcher gate. */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class LibraryPlaybackDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation

    private fun awaitStep(description: String, condition: () -> Boolean) {
        instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\nChecking $description\n") })
        val deadline = SystemClock.elapsedRealtime() + 25_000
        while (!condition()) {
            if (SystemClock.elapsedRealtime() >= deadline) {
                val directory = instrumentation.targetContext.filesDir
                automation.takeScreenshot()?.let { image ->
                    java.io.File(directory, "library-screen.png").outputStream().use {
                        image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                    image.recycle()
                }
                java.io.File(directory, "library-state.txt").writeText(buildString {
                    fun appendNode(node: AccessibilityNodeInfo) {
                        val rect = Rect().also(node::getBoundsInScreen)
                        appendLine("${node.text} | ${node.contentDescription} | $rect")
                        for (i in 0 until node.childCount) node.getChild(i)?.let(::appendNode)
                    }
                    automation.rootInActiveWindow?.let(::appendNode)
                })
                error("Timed out waiting for $description")
            }
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
        awaitStep("stable visible $label") {
            val next = bounds(label)
            val stable = next != null && next == point
            point = next
            stable
        }
        val target = checkNotNull(point)
        instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\nTap $label at $target\n") })
        tap(target.centerX(), target.centerY())
    }

    @Test fun libraryTapAutoplaysAndTransportAndResizerRespond() {
        val context = instrumentation.targetContext
        context.startActivity(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        tapLabel("HiPlayerLaunchCheck")
        awaitStep("indexed folder contents") { bounds("Back to Folders") != null }
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
        awaitStep("guide dismissal") { bounds("Skip guide") == null }
        if (bounds("Pause") == null) {
            val window = Rect().also { requireNotNull(automation.rootInActiveWindow).getBoundsInScreen(it) }
            tap(window.centerX(), window.centerY())
        }
        tapLabel("Pause")
        awaitStep("paused playback") { !engine.isPlaying.value }
        val modes = AspectRatioMode.entries
        val start = engine.aspectRatioMode.value
        var resizeBounds: Rect? = null
        awaitStep("resize button") { resizeBounds = bounds(start.displayName); resizeBounds != null }
        // Keep tapping the original toolbar target. The transient HUD repeats its label.
        val resize = checkNotNull(resizeBounds)
        for (step in 1..modes.size + 1) {
            val next = modes[(start.ordinal + step) % modes.size]
            tap(resize.centerX(), resize.centerY())
            awaitStep("native surface for ${next.displayName}") {
                var matches = false
                instrumentation.runOnMainSync {
                    val view = (host.window.decorView as ViewGroup).descendants.filterIsInstance<SurfaceView>().first()
                    val ratio = next.targetRatio
                    val display = view.pictureBounds()
                    matches = engine.aspectRatioMode.value == next && display.height() > 0 &&
                        (ratio == null || abs(display.width().toFloat() / display.height() - ratio) < .02f)
                }
                matches
            }
        }
        val paused = engine.currentPositionMs.value
        tapLabel("Play")
        awaitStep("resumed playback") { engine.isPlaying.value && engine.currentPositionMs.value > paused + 500 }
        instrumentation.runOnMainSync { host.finish() }
    }

    private fun centeredMkvPicture(host: MainActivity, landscape: Boolean): Boolean {
        var viewport = Rect()
        instrumentation.runOnMainSync {
            val surface = (host.window.decorView as ViewGroup).descendants.filterIsInstance<SurfaceView>().firstOrNull()
                ?: return@runOnMainSync
            // VLC has a direct SurfaceView child. Media3 nests its SurfaceView
            // inside the already-resized PlayerView. Measure the full-screen
            // host outside PlayerView, not the picture-sized inner container.
            val playerView = generateSequence<View>(surface) { it.parent as? View }
                .filterIsInstance<androidx.media3.ui.PlayerView>().firstOrNull()
            val parent = (playerView ?: surface).parent as? View ?: return@runOnMainSync
            val location = IntArray(2)
            parent.getLocationOnScreen(location)
            viewport = Rect(location[0], location[1], location[0] + parent.width, location[1] + parent.height)
        }
        if (viewport.isEmpty || (viewport.width() > viewport.height()) != landscape) return false
        val screen = automation.takeScreenshot() ?: return false
        try {
            val ratio = requireNotNull(AspectRatioMode.IMAX_DIGITAL.targetRatio)
            val width = minOf(viewport.width(), (viewport.height() * ratio).roundToInt())
            val height = (width / ratio).roundToInt()
            val left = viewport.centerX() - width / 2
            val top = viewport.centerY() - height / 2
            fun magenta(x: Int, y: Int): Boolean {
                if (x !in 0 until screen.width || y !in 0 until screen.height) return false
                val c = screen.getPixel(x, y)
                return Color.red(c) > Color.green(c) + 30 && Color.blue(c) > Color.green(c) + 30
            }
            val inset = (minOf(width, height) / 40).coerceAtLeast(4)
            // Sample on both sides of each expected edge. Majority voting tolerates
            // control icons and captions without accepting a uniformly black frame.
            fun row(y: Int) = (2..8).count { magenta(left + width * it / 10, y) } >= 4
            fun column(x: Int) = (2..8).count { magenta(x, top + height * it / 10) } >= 4
            return row(top + inset) && row(top + height - inset - 1) &&
                column(left + inset) && column(left + width - inset - 1) &&
                !row(top - inset) && !row(top + height + inset) &&
                !column(left - inset) && !column(left + width + inset)
        } finally { screen.recycle() }
    }

    @Test fun portraitMkvResumesWithVisibleCenteredPictureThroughRotation() {
        val context = instrumentation.targetContext
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val name = "portrait-check.mkv"
        val uri = context.contentResolver.query(collection, arrayOf(MediaStore.Video.Media._ID),
            "${MediaStore.Video.Media.DISPLAY_NAME} = ?", arrayOf(name), null)?.use { cursor ->
            check(cursor.moveToFirst()) { "Missing indexed fixture $name" }
            ContentUris.withAppendedId(collection, cursor.getLong(0))
        } ?: error("Could not query indexed videos")
        context.startActivity(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        tapLabel("HiPlayerPortraitCheck")
        awaitStep("MKV folder contents") { bounds("Back to Folders") != null }
        val startMs = 338_000L
        runBlocking {
            HiPlayerDatabase.getInstance(context).videoDao().insertOrUpdate(VideoEntity(uri.toString(), name,
                lastPositionMs = startMs, durationMs = 390_000L, customAspectRatio = AspectRatioMode.IMAX_DIGITAL.name))
        }
        tapLabel(name)
        lateinit var host: MainActivity
        lateinit var engine: HiPlayerEngine
        instrumentation.runOnMainSync {
            host = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<MainActivity>().single()
            engine = ViewModelProvider(host)[PlayerViewModel::class.java].engine
        }
        try {
            var previousMs = startMs
            for ((index, landscape) in listOf(false, true, false).withIndex()) {
                if (index > 0) {
                    if (bounds("Rotate Screen") == null) {
                        val window = Rect().also { requireNotNull(automation.rootInActiveWindow).getBoundsInScreen(it) }
                        tap(window.centerX(), window.centerY())
                    }
                    tapLabel("Rotate Screen")
                }
                try {
                    awaitStep("centered IMAX video pixels, orientation step $index, continuously advancing from $previousMs") {
                        engine.aspectRatioMode.value == AspectRatioMode.IMAX_DIGITAL &&
                            (!engine.usesVlc.value || engine.vlcFirstFrame.value) && engine.isPlaying.value &&
                            engine.currentPositionMs.value in (previousMs + 100)..(previousMs + 30_000) &&
                            centeredMkvPicture(host, landscape)
                    }
                } catch (failure: IllegalStateException) {
                    val pixels = centeredMkvPicture(host, landscape)
                    throw IllegalStateException("IMAX step $index: ratio=${engine.aspectRatioMode.value}, " +
                        "vlc=${engine.usesVlc.value}, firstFrame=${engine.vlcFirstFrame.value}, " +
                        "playing=${engine.isPlaying.value}, position=${engine.currentPositionMs.value}, " +
                        "previous=$previousMs, centeredPixels=$pixels, error=${engine.playerError.value}", failure)
                }
                previousMs = engine.currentPositionMs.value
            }
        } finally { instrumentation.runOnMainSync { host.finish() } }
    }
}
