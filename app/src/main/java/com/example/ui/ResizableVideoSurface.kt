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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * Resizes the real SurfaceView layout instead of applying a Compose graphics
 * transform. This keeps SurfaceHolder and the OS compositor synchronized while
 * changing Fit/Crop/fixed-ratio modes.
 */
@UnstableApi
@Composable
fun ResizableVideoSurface(
    player: ExoPlayer,
    aspectMode: VideoAspectRatioMode,
    modifier: Modifier = Modifier,
) {
    var videoGeometry by remember { mutableStateOf(VideoGeometry()) }
    var firstFrameRendered by remember { mutableStateOf(false) }

    LaunchedEffect(player.currentMediaItem) {
        firstFrameRendered = false
        videoGeometry = VideoGeometry()
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                videoGeometry = videoSize.toVideoGeometry()
            }

            override fun onRenderedFirstFrame() {
                firstFrameRendered = true
            }
        }
        player.addListener(listener)
        videoGeometry = player.videoSize.toVideoGeometry()
        onDispose { player.removeListener(listener) }
    }

    BoxWithConstraints(
        modifier = modifier
            .background(Color.Black)
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        // A bounded parent is required to calculate a meaningful display size.
        // Falling back to fill avoids invalid/unbounded SurfaceView allocation.
        val hasBoundedConstraints = maxWidth.value.isFinite() &&
            maxHeight.value.isFinite() &&
            maxWidth > 0.dp &&
            maxHeight > 0.dp

        if (!hasBoundedConstraints) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx -> buildSurfaceView(ctx, player) },
            )
            FirstFrameMask(firstFrameRendered)
            return@BoxWithConstraints
        }

        val (targetWidth, targetHeight) = remember(maxWidth, maxHeight, videoGeometry, aspectMode) {
            computeTargetSize(maxWidth, maxHeight, videoGeometry, aspectMode)
        }

        val animatedWidth by animateDpAsState(
            targetWidth,
            tween(260),
            label = "videoWidth",
        )
        val animatedHeight by animateDpAsState(
            targetHeight,
            tween(260),
            label = "videoHeight",
        )

        AndroidView(
            modifier = Modifier.size(animatedWidth, animatedHeight),
            factory = { ctx -> buildSurfaceView(ctx, player) },
        )

        FirstFrameMask(firstFrameRendered)
    }
}

/** Complete display geometry reported by Media3. */
private data class VideoGeometry(
    val width: Int = 0,
    val height: Int = 0,
    val pixelWidthHeightRatio: Float = 1f,
    val unappliedRotationDegrees: Int = 0,
)

private fun VideoSize.toVideoGeometry(): VideoGeometry = VideoGeometry(
    width = width,
    height = height,
    pixelWidthHeightRatio = pixelWidthHeightRatio,
    unappliedRotationDegrees = unappliedRotationDegrees,
)

private fun buildSurfaceView(ctx: android.content.Context, player: ExoPlayer): SurfaceView {
    return SurfaceView(ctx).apply {
        holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                player.setVideoSurfaceHolder(holder)
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int,
            ) = Unit

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                player.clearVideoSurfaceHolder(holder)
            }
        })
    }
}

@Composable
private fun FirstFrameMask(firstFrameRendered: Boolean) {
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

/**
 * Returns the real SurfaceView width/height for the selected mode. The video
 * ratio is normalized into display space before any mode calculation.
 */
private fun computeTargetSize(
    containerWidth: Dp,
    containerHeight: Dp,
    videoGeometry: VideoGeometry,
    mode: VideoAspectRatioMode,
): Pair<Dp, Dp> {
    if (containerWidth <= 0.dp || containerHeight <= 0.dp) return 0.dp to 0.dp

    if (mode == VideoAspectRatioMode.STRETCH) {
        return containerWidth to containerHeight
    }

    val containerRatio = containerWidth.value / containerHeight.value
    val videoRatio = videoGeometry.displayAspectRatio(containerRatio)

    return when (mode) {
        VideoAspectRatioMode.FIT -> fitWithinContainer(
            containerWidth,
            containerHeight,
            containerRatio,
            videoRatio,
        )
        VideoAspectRatioMode.CROP -> fillContainer(
            containerWidth,
            containerHeight,
            containerRatio,
            videoRatio,
        )
        VideoAspectRatioMode.IMAX_FULL,
        VideoAspectRatioMode.RATIO_16_9,
        VideoAspectRatioMode.RATIO_4_3 -> {
            val targetRatio = mode.ratio ?: videoRatio
            fillContainer(containerWidth, containerHeight, containerRatio, targetRatio)
        }
        VideoAspectRatioMode.STRETCH -> containerWidth to containerHeight
    }
}

/** Whole frame visible, letterboxed/pillarboxed. */
private fun fitWithinContainer(
    containerW: Dp,
    containerH: Dp,
    containerRatio: Float,
    ratio: Float,
): Pair<Dp, Dp> {
    return if (ratio > containerRatio) {
        containerW to (containerW.value / ratio).dp
    } else {
        (containerH.value * ratio).dp to containerH
    }
}

/** Fills the container at the requested ratio and lets the parent clip it. */
private fun fillContainer(
    containerW: Dp,
    containerH: Dp,
    containerRatio: Float,
    ratio: Float,
): Pair<Dp, Dp> {
    return if (ratio > containerRatio) {
        (containerH.value * ratio).dp to containerH
    } else {
        containerW to (containerW.value / ratio).dp
    }
}

/**
 * Converts encoded video geometry into the aspect ratio visible to the user.
 * Non-square pixels are corrected with pixelWidthHeightRatio. For 90/270 degree
 * metadata, the display axes swap, so the normalized ratio is inverted.
 */
private fun VideoGeometry.displayAspectRatio(fallback: Float): Float {
    if (width <= 0 || height <= 0) return fallback

    val pixelRatio = pixelWidthHeightRatio
        .takeIf { it.isFinite() && it > 0f }
        ?: 1f
    val encodedRatio = (width.toFloat() * pixelRatio) / height.toFloat()
    if (!encodedRatio.isFinite() || encodedRatio <= 0f) return fallback

    val normalizedRotation = ((unappliedRotationDegrees % 360) + 360) % 360
    val displayRatio = if (normalizedRotation == 90 || normalizedRotation == 270) {
        1f / encodedRatio
    } else {
        encodedRatio
    }
    return displayRatio.takeIf { it.isFinite() && it > 0f } ?: fallback
}
