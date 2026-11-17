package com.example.ui

import android.view.SurfaceView
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * Media3 owns its native HDR SurfaceView through PlayerView.
 *
 * Calling setVideoSurfaceView() in addition to assigning PlayerView.player
 * double-attaches the same SurfaceView and can race surface release/rebinding
 * when switching between HDR, GLES and LibVLC backends.
 */
@OptIn(UnstableApi::class)
@Composable
fun NativeHdrPlayerSurface(
    player: ExoPlayer,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                subtitleView?.visibility = View.GONE
                (videoSurfaceView as? SurfaceView)?.setZOrderMediaOverlay(false)
                this.player = player
            }
        },
        update = { view ->
            if (view.player !== player) view.player = player
            view.visibility = View.VISIBLE
            view.subtitleView?.visibility = View.GONE
        },
        // PlayerView clears *only its own* managed video surface.
        onRelease = { view ->
            view.visibility = View.GONE
            view.player = null
        },
        modifier = modifier,
    )
}
