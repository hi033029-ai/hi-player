package com.example.ui

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.playback.VideoAspectRatioMode

/**
 * Fixes: black screen during / after resize (Fit / Crop / IMAX / 16:9 / 4:3 / Stretch).
 *
 * ROOT CAUSE: the previous version resized the video with a Compose `graphicsLayer`
 * scale on the AndroidView wrapping the SurfaceView. SurfaceView does NOT composite
 * through Compose's normal RenderNode/layer pipeline — it "hole-punches" through the
 * window and is positioned/sized directly by the OS compositor based on the View's
 * real measured layout bounds, tracked via SurfaceHolder callbacks. A `graphicsLayer`
 * transform is a compositor-side visual effect on top of that — it doesn't trigger a
 * real layout pass, so it doesn't tell the OS where the actual surface should be. That
 * mismatch between "what Compose is visually drawing" and "where the real surface
 * actually lives" is what caused the black screen — on some devices/mid-animation,
 * this state is invalid enough that the surface gets torn down.
 *
 * FIX: resize the video by changing the SurfaceView's REAL layout size (an actual
 * Modifier.size(width, height) with animated Dp values), the same way classic
 * Android's AspectRatioFrameLayout (used inside Media3's own PlayerView) does it —
 * a genuine measure/layout pass, so SurfaceHolder.Callback and the OS compositor stay
 * in sync throughout the animation. Cropped/oversized modes rely on the outer Box's
 * clipToBounds() to clip real, correctly-sized content — not a scaled transform.
 */
@UnstableApi
@Composable
fun ResizableVideoSurface(
    player: ExoPlayer,
    aspectMode: VideoAspectRatioMode,
    modifier: Modifier = Modifier,
) {
    var videoWidth by remember { mutableStateOf(0) }
    var videoHeight by remember { mutableStateOf(0) }
    var firstFrameRendered by remember { mutableStateOf(false) }

    LaunchedEffect(player.currentMediaItem) { firstFrameRendered = false }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                videoWidth = videoSize.width
                videoHeight = videoSize.height
            }
            override fun onRenderedFirstFrame() {
                firstFrameRendered = true
            }
        }
        player.addListener(listener)
        player.videoSize.let {
            videoWidth = it.width
            videoHeight = it.height
        }
        onDispose { player.removeListener(listener) }
    }

    BoxWithConstraints(
        modifier = modifier
            .background(Color.Black)
            .clipToBounds(), // clips real oversized content in Crop/IMAX/16:9/4:3 modes
        contentAlignment = Alignment.Center,
    ) {
        val (targetWidth, targetHeight) = remember(maxWidth, maxHeight, videoWidth, videoHeight, aspectMode) {
            computeTargetSize(maxWidth, maxHeight, videoWidth, videoHeight, aspectMode)
        }

        // Animating real Dp size values (not a scale factor) — this drives genuine
        // Compose measure/layout passes each frame, which is what keeps SurfaceView
        // correctly in sync with the OS compositor throughout the transition.
        val animatedWidth by animateDpAsState(targetWidth, tween(260), label = "videoWidth")
        val animatedHeight by animateDpAsState(targetHeight, tween(260), label = "videoHeight")

        AndroidView(
            modifier = Modifier.size(animatedWidth, animatedHeight),
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            player.setVideoSurfaceHolder(holder)
                        }
                        override fun surfaceChanged(
                            holder: SurfaceHolder, format: Int, width: Int, height: Int,
                        ) = Unit
                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            player.clearVideoSurfaceHolder(holder)
                        }
                    })
                }
            },
        )

        val maskAlpha by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (firstFrameRendered) 0f else 1f,
            animationSpec = tween(durationMillis = 150),
            label = "videoMaskAlpha",
        )
        if (maskAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = maskAlpha)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

/**
 * Returns the actual width/height (Dp) the SurfaceView should be laid out at for the
 * given mode — real dimensions, not a scale multiplier. Oversized results (Crop/IMAX/
 * 16:9/4:3 when the target ratio doesn't match the container) are intentional; the
 * caller's clipToBounds() trims them to the visible area.
 */
private fun computeTargetSize(
    containerWidth: Dp,
    containerHeight: Dp,
    videoWidth: Int,
    videoHeight: Int,
    mode: VideoAspectRatioMode,
): Pair<Dp, Dp> {
    if (containerWidth <= 0.dp || containerHeight <= 0.dp) return 0.dp to 0.dp

    if (mode == VideoAspectRatioMode.STRETCH) {
        return containerWidth to containerHeight
    }

    val containerRatio = containerWidth.value / containerHeight.value
    val videoRatio = if (videoWidth > 0 && videoHeight > 0) {
        videoWidth.toFloat() / videoHeight.toFloat()
    } else {
        containerRatio // no video size known yet — avoid a divide-by-zero / NaN flash
    }

    return when (mode) {
        VideoAspectRatioMode.FIT -> fitWithinContainer(containerWidth, containerHeight, containerRatio, videoRatio)
        VideoAspectRatioMode.CROP -> fillContainer(containerWidth, containerHeight, containerRatio, videoRatio)
        VideoAspectRatioMode.IMAX_FULL, VideoAspectRatioMode.RATIO_16_9, VideoAspectRatioMode.RATIO_4_3 -> {
            val targetRatio = mode.ratio ?: videoRatio
            fillContainer(containerWidth, containerHeight, containerRatio, targetRatio)
        }
        VideoAspectRatioMode.STRETCH -> containerWidth to containerHeight // unreachable, handled above
    }
}

/** Whole frame visible, letterboxed/pillarboxed — shrinks to fit inside the container. */
private fun fitWithinContainer(containerW: Dp, containerH: Dp, containerRatio: Float, ratio: Float): Pair<Dp, Dp> {
    return if (ratio > containerRatio) {
        containerW to (containerW.value / ratio).dp
    } else {
        (containerH.value * ratio).dp to containerH
    }
}

/** Fills the container edge-to-edge at the given ratio — oversizes and lets the caller clip. */
private fun fillContainer(containerW: Dp, containerH: Dp, containerRatio: Float, ratio: Float): Pair<Dp, Dp> {
    return if (ratio > containerRatio) {
        (containerH.value * ratio).dp to containerH
    } else {
        containerW to (containerW.value / ratio).dp
    }
}
