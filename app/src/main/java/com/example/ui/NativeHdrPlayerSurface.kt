package com.example.ui

import android.view.SurfaceView
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * Direct HDR output host. PlayerView defaults to SurfaceView, allowing Media3 and
 * Android's video/display pipeline to preserve HDR instead of round-tripping the
 * frame through the app's 8-bit GLES framebuffer.
 */
@OptIn(UnstableApi::class)
@Composable
fun NativeHdrPlayerSurface(
    player: Player,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                subtitleView?.visibility = View.GONE
                (videoSurfaceView as? SurfaceView)?.setZOrderMediaOverlay(true)
                this.player = player
            }
        },
        update = { view ->
            if (view.player !== player) view.player = player
            view.subtitleView?.visibility = View.GONE
        },
        modifier = modifier,
        // Release only when this AndroidView actually leaves the composition.
        // A DisposableEffect keyed to a mutable view reference could detach a
        // freshly rebound player during recomposition, leaving audio-only playback.
        onRelease = { view -> view.player = null },
    )
}
