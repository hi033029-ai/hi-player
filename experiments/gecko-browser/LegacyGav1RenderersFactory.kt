package com.example.player

import android.content.Context
import android.os.Handler
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.av1.Libgav1VideoRenderer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.VideoRendererEventListener

/** UNSAFE probe only: real decoding aborts on Media3 1.11 because the bundled JNI expects an int decoderPrivate field, now long. Never ship. */
@UnstableApi
internal class LegacyGav1RenderersFactory(context: Context) : DefaultRenderersFactory(context) {
    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: ArrayList<Renderer>,
    ) {
        val videoStart = out.size
        super.buildVideoRenderers(context, extensionRendererMode, mediaCodecSelector, enableDecoderFallback,
            eventHandler, eventListener, allowedVideoJoiningTimeMs, out)
        if (extensionRendererMode != EXTENSION_RENDERER_MODE_OFF) {
            val renderer = Libgav1VideoRenderer(allowedVideoJoiningTimeMs, eventHandler, eventListener, MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY)
            // ON preserves hardware-first selection. PREFER places software first.
            val index = if (extensionRendererMode == EXTENSION_RENDERER_MODE_PREFER) videoStart else out.size
            out.add(index, renderer)
        }
    }
}
