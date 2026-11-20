package com.example.ui

import android.view.Gravity
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/** The full-screen host clips zoom at the viewport edge. PlayerView owns the only video surface. */
@OptIn(UnstableApi::class)
@Composable
fun NativeHdrPlayerSurface(
    player: ExoPlayer,
    modifier: Modifier = Modifier,
    bufferSize: IntSize? = null,
    scaleX: Float = 1f,
    scaleY: Float = 1f,
    onSurface: (SurfaceView?) -> Unit = {},
) {
    AndroidView(
        factory = { context ->
            FrameLayout(context).apply {
                val video = PlayerView(context).apply {
                    useController = false
                    setKeepContentOnPlayerReset(true)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                    subtitleView?.visibility = View.GONE
                    (videoSurfaceView as? SurfaceView)?.setZOrderMediaOverlay(false)
                }
                addView(video, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
            }
        },
        update = { host ->
            val video = host.getChildAt(0) as PlayerView
            val width = bufferSize?.width ?: FrameLayout.LayoutParams.MATCH_PARENT
            val height = bufferSize?.height ?: FrameLayout.LayoutParams.MATCH_PARENT
            val params = video.layoutParams
            if (params.width != width || params.height != height) {
                params.width = width
                params.height = height
                video.layoutParams = params
            }
            video.scaleX = scaleX
            video.scaleY = scaleY
            if (video.player !== player) video.player = player
            onSurface(video.videoSurfaceView as? SurfaceView)
        },
        onRelease = { host -> (host.getChildAt(0) as PlayerView).player = null },
        modifier = modifier,
    )
}
