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
    displaySize: IntSize? = null,
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
            val surface = video.videoSurfaceView as? SurfaceView
            // Media3 fits the graded frame into its output buffer. Keep that buffer
            // at the source aspect while Android maps it to the selected view bounds.
            if (surface != null && surface.tag != bufferSize) {
                if (bufferSize != null) surface.holder.setFixedSize(bufferSize.width, bufferSize.height)
                else surface.holder.setSizeFromLayout()
                surface.tag = bufferSize
            }
            video.setVideoDisplaySize(displaySize, bufferSize)
            if (video.player !== player) video.player = player
        },
        onRelease = { host -> (host.getChildAt(0) as PlayerView).player = null },
        modifier = modifier,
    )
}

internal fun View.setVideoDisplaySize(displaySize: IntSize?, bufferSize: IntSize?) {
    val base = bufferSize ?: displaySize
    val width = base?.width ?: FrameLayout.LayoutParams.MATCH_PARENT
    val height = base?.height ?: FrameLayout.LayoutParams.MATCH_PARENT
    val params = layoutParams
    if (params.width != width || params.height != height) {
        params.width = width
        params.height = height
        layoutParams = params
    }
    scaleX = if (bufferSize != null && displaySize != null) displaySize.width.toFloat() / bufferSize.width else 1f
    scaleY = if (bufferSize != null && displaySize != null) displaySize.height.toFloat() / bufferSize.height else 1f
}
