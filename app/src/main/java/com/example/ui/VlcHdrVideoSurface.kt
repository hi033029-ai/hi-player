package com.example.ui

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.player.VlcPlaybackEngine

/**
 * No GLES intermediate framebuffer: MediaCodec renders directly into SurfaceView,
 * preserving hardware 10-bit/HDR output when supported by the device/display.
 * Compose's GestureOverlay sits above this non-interactive native surface.
 */
@Composable
fun VlcHdrVideoSurface(engine: VlcPlaybackEngine, modifier: Modifier = Modifier) {
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
            surface.isClickable = false
            surface.isLongClickable = false
            engine.attachSurface(surface)
            engine.updateWindowSize(surface)
        },
        onRelease = { surface -> engine.detachSurface(surface) },
        modifier = modifier,
    )
}
