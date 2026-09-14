package com.example.player

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters

/**
 * Applies a fast seek only for one-time resume playback.
 *
 * Interactive seeks remain EXACT in [HiPlayerEngine]. A resume position is
 * different: there is no audio clock running yet, so landing on the nearest
 * sync frame avoids a long black startup while the decoder walks through a
 * long-GOP HEVC stream. Once the first frame is rendered, EXACT is restored.
 */
@UnstableApi
fun ExoPlayer.configureResumeSeek(resumePositionMs: Long) {
    setSeekParameters(if (resumePositionMs > 0L) SeekParameters.CLOSEST_SYNC else SeekParameters.EXACT)
    if (resumePositionMs <= 0L) return

    addListener(object : Player.Listener {
        override fun onRenderedFirstFrame() {
            setSeekParameters(SeekParameters.EXACT)
            removeListener(this)
        }
    })
}
