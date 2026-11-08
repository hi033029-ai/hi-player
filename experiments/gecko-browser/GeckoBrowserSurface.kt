package com.example.browser.gecko

import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.mozilla.geckoview.GeckoView

/** View host only: tab chrome, settings, error UI and feature parity remain with the caller. */
@Composable
internal fun GeckoBrowserSurface(controller: GeckoTabController<GeckoView>, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            GeckoView(context).also { view ->
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
