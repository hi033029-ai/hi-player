package com.example.ui

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
fun VlcHdrVideoSurface(engine: VlcPlaybackEngine, modifier: Modifier = Modifier, onSurface: (SurfaceView) -> Unit = {}) {
    AndroidView(
        factory = { context ->
            SurfaceView(context).apply {
                isClickable = false
                isLongClickable = false
                isFocusable = false
                isFocusableInTouchMode = false
                setZOrderOnTop(false)
                setZOrderMediaOverlay(false)
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) { engine.attachSurface(this@apply) }
                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                        engine.updateWindowSize(this@apply)
                    }
                    override fun surfaceDestroyed(holder: SurfaceHolder) { engine.detachSurface(this@apply) }
                })
            }
        },
        update = { surface ->
            onSurface(surface)
            surface.isClickable = false
            surface.isLongClickable = false
            engine.attachSurface(surface)
            engine.updateWindowSize(surface)
        },
        onRelease = { surface -> engine.detachSurface(surface) },
        modifier = modifier,
    )
}
