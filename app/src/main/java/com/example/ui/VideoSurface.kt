package com.example.ui

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

// NOTE: superseded by ResizableVideoSurface.kt (adds smooth aspect-ratio resizing).
// HDR color-mode handling was moved OUT of this file on purpose: owning a second,
// independent HdrColorModeManager here (in addition to the one in PlayerScreen.kt)
// was exactly the kind of double-writer race that caused the HDR-click freeze.
// There must be exactly one HdrColorModeManager per player, owned by the screen
// that hosts the controls — never one per surface composable.

/**
 * Fixes both reported bugs:
 *
 * 1. Black screen on first play, fine on replay
 *    -> We now (a) only attach the player to the SurfaceView's surface after
 *       SurfaceHolder.Callback#surfaceCreated actually fires (previously the surface
 *       could be handed to the player before it truly existed), and (b) keep a poster
 *       frame on top, faded out only on Player.Listener#onRenderedFirstFrame, so any
 *       remaining black frame during surface/HDR-mode setup is covered instead of
 *       flashing on screen.
 *
 * 2. HDR colors distorted
 *    -> HdrColorModeManager (see HdrColorModeManager.kt) flips the window into
 *       COLOR_MODE_HDR proactively as soon as the selected track's ColorInfo says
 *       it's HDR, instead of relying on the OS's reactive auto-negotiation.
 */
@UnstableApi
@Composable
fun VideoSurface(
    player: ExoPlayer,
    posterPainter: Painter? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var surfaceReady by remember { mutableStateOf(false) }
    var firstFrameRendered by remember { mutableStateOf(false) }

    // Reset the poster whenever the media item changes (new file = new possible black flash).
    LaunchedEffect(player.currentMediaItem) {
        firstFrameRendered = false
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                firstFrameRendered = true
            }
        }
        player.addListener(listener)

        onDispose {
            player.removeListener(listener)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            // Only now does a real, usable Surface exist — safe to attach.
                            player.setVideoSurfaceHolder(holder)
                            surfaceReady = true
                        }

                        override fun surfaceChanged(
                            holder: SurfaceHolder,
                            format: Int,
                            width: Int,
                            height: Int,
                        ) = Unit

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            player.clearVideoSurfaceHolder(holder)
                            surfaceReady = false
                            firstFrameRendered = false
                        }
                    })
                }
            },
        )

        // Poster/black mask: covers the surface until the first decoded frame is
        // actually on screen, so the setup flash from bug #1 is never visible.
        val maskAlpha by animateFloatAsState(
            targetValue = if (firstFrameRendered) 0f else 1f,
            animationSpec = tween(durationMillis = 150),
            label = "videoMaskAlpha",
        )
        if (maskAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(maskAlpha)
                    .background(Color.Black),
            ) {
                posterPainter?.let {
                    Image(painter = it, contentDescription = null, modifier = Modifier.fillMaxSize())
                }
                if (!surfaceReady) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White,
                    )
                }
            }
        }
    }
}
