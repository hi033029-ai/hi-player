package com.example.player

import androidx.media3.common.Effect
import androidx.media3.common.VideoFrameProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/** Media3 1.11 adds REDRAW; the retained 1.5 validation build has no such field. */
@UnstableApi
internal object VideoEffectRedraw {
    @Suppress("UNCHECKED_CAST")
    private val redraw: List<Effect>? by lazy {
        try {
            VideoFrameProcessor::class.java.getField("REDRAW").get(null) as List<Effect>
        } catch (_: NoSuchFieldException) {
            null // Known older Media3 API, not a failed rendering operation.
        }
    }

    fun request(player: ExoPlayer) {
        val command = redraw
        if (command != null) {
            player.setVideoEffects(command)
        } else {
            // Older Media3 cannot replay a processed frame. Re-prepare the SAME
            // item/renderer once for this explicit paused preset change. Retain
            // the exact position, track overrides, volume and paused state.
            val position = player.currentPosition
            player.stop()
            player.seekTo(position)
            player.prepare()
        }
    }
}
