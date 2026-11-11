package com.example.ui

import android.view.SurfaceView
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    DisposableEffect(player, playerViewRef) {
        val attachedView = playerViewRef
        onDispose {
            if (attachedView?.player === player) attachedView.player = null
        }
    }

    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                subtitleView?.visibility = View.GONE
                (videoSurfaceView as? SurfaceView)?.setZOrderMediaOverlay(true)
                this.player = player
                playerViewRef = this
            }
        },
        update = { view ->
            if (view.player !== player) view.player = player
            view.subtitleView?.visibility = View.GONE
            playerViewRef = view
        },
        modifier = modifier,
    )
}
