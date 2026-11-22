package com.example.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

@UnstableApi
internal object VideoEffectRedraw {
    fun request(player: ExoPlayer) {
        // Media3's default playback graph has no replayable frame cache.
        // Seek to a nearby position to redraw without resuming playback.
        // Alternate between adjacent milliseconds so repeated edits stay near the same frame.
        val here = player.currentPosition.coerceAtLeast(0L)
        val end = player.duration
        val adjacent = if (here % 2 == 0L && (end < 0L || here + 1 < end)) here + 1 else (here - 1).coerceAtLeast(0)
        player.seekTo(adjacent)
    }
}
