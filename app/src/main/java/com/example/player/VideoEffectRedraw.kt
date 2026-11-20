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
            // A seek to exactly the current millisecond can be elided. Alternate
            // between adjacent milliseconds so repeated edits never advance the film.
            val here = player.currentPosition.coerceAtLeast(0L)
            val end = player.duration
            val adjacent = if (here % 2 == 0L && (end < 0L || here + 1 < end)) here + 1 else (here - 1).coerceAtLeast(0)
            player.seekTo(adjacent)
        }
    }
}
