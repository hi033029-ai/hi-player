package com.example.browser.gecko

import androidx.compose.ui.graphics.asImageBitmap
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.mozilla.geckoview.GeckoView

internal object GeckoTabPreviews {
    val views = java.util.WeakHashMap<GeckoTabController<GeckoView>, java.lang.ref.WeakReference<GeckoView>>()
    fun capture(controller: GeckoTabController<GeckoView>, url: String, currentUrl: () -> String) {
        val view = views[controller]?.get() ?: return
        val id = controller.activeId
        if (view.width <= 0 || view.height <= 0 || !com.example.ui.screens.BrowserHistoryPolicy.isWebUrl(url) ||
            controller.tabs.none { it.id == id && !it.privateTab }) return
        runCatching { view.capturePixels().accept({ bitmap ->
            if (bitmap != null && controller.activeId == id && currentUrl() == url && view.isAttachedToWindow) {
                val width = minOf(360, bitmap.width)
                val height = (bitmap.height.toLong() * width / bitmap.width).toInt().coerceIn(1, 640)
                com.example.ui.screens.WebPagePreviews.save(url, android.graphics.Bitmap.createScaledBitmap(bitmap, width, height, true).asImageBitmap())
            }
        }, { }) }
    }
}

/** View host only: tab chrome, settings, error UI and feature parity remain with the caller. */
@Composable
internal fun GeckoBrowserSurface(controller: GeckoTabController<GeckoView>, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            GeckoView(context).also { view ->
                GeckoTabPreviews.views[controller] = java.lang.ref.WeakReference(view)
                controller.attach(view)
                var previousY: Float? = null
                view.setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> { previousY = event.y; controller.scrollChrome.endGesture() }
                        MotionEvent.ACTION_MOVE -> {
                            if (event.pointerCount == 1) {
                                previousY?.let { y -> controller.scrollChrome.onScroll(y - event.y) }
                                previousY = event.y
                            } else {
                                previousY = null
                                controller.scrollChrome.endGesture()
                            }
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            previousY = null
                            controller.scrollChrome.endGesture()
                        }
                    }
                    // Observation only: Gecko receives the original event and owns all gestures.
                    false
                }
            }
        },
        update = { view -> controller.attach(view) },
        onRelease = { view -> controller.detach(view); controller.scrollChrome.endGesture() },
    )
}
