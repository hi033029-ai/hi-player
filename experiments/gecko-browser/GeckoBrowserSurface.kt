package com.example.browser.gecko

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.mozilla.geckoview.GeckoView

/** View host only: tab chrome, settings, error UI and feature parity remain with the caller. */
@Composable
internal fun GeckoBrowserSurface(controller: GeckoTabController<GeckoView>, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context -> GeckoView(context).also(controller::attach) },
        update = { view -> controller.attach(view) },
        onRelease = { view -> controller.detach(view) },
    )
}
