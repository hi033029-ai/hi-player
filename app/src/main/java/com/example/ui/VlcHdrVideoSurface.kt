package com.example.ui

import android.view.Gravity
import android.widget.FrameLayout
import androidx.compose.ui.unit.IntSize
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.player.VlcPlaybackEngine

/**
 * Direct VLC SurfaceView with no application color transform. Color-sensitive
 * HEVC/HDR items are routed through the metadata-aware Media3 path by the engine.
 * Compose's GestureOverlay sits above this non-interactive native surface.
 */
@Composable
fun VlcHdrVideoSurface(
    engine: VlcPlaybackEngine,
    modifier: Modifier = Modifier,
    displaySize: IntSize,
    sourceSize: IntSize?,
    onSurface: (SurfaceView) -> Unit = {},
) {
    AndroidView(
        factory = { context ->
            val host = FrameLayout(context)
            val video = SurfaceView(context).apply {
                isClickable = false
                isFocusable = false
                setZOrderOnTop(false)
                setZOrderMediaOverlay(false)
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) = Unit
                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                        engine.attachSurface(this@apply, width, height)
                    }
                    override fun surfaceDestroyed(holder: SurfaceHolder) = engine.detachSurface(this@apply)
                })
            }
            host.addView(video, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
            host
        },
        update = { host ->
            val video = host.getChildAt(0) as SurfaceView
            // SurfaceFlinger maps the source buffer into the centered view. Rotation
            // and aspect selection change the destination without reloading the media.
            if (video.tag != sourceSize) {
                if (sourceSize != null) video.holder.setFixedSize(sourceSize.width, sourceSize.height)
                else video.holder.setSizeFromLayout()
                video.tag = sourceSize
            }
            video.setVideoDisplaySize(displaySize, null)
            onSurface(video)
        },
        onRelease = { host -> engine.detachSurface(host.getChildAt(0) as SurfaceView) },
        modifier = modifier,
    )
}
