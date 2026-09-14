package com.example.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters

/**
 * Fixes: black screen stuck on "Continue Watching" resume.
 *
 * OptimizedPlayerBuilder.build() sets SeekParameters.EXACT on the player, which is
 * correct and necessary for interactive seeks DURING playback (see the earlier fix —
 * CLOSEST_SYNC there caused audio to race ahead of video after every scrub/skip).
 *
 * But EXACT has a real cost: it forces the video renderer to decode every frame from
 * the nearest keyframe up to the exact requested timestamp before showing anything.
 * A "Continue Watching" resume seeks straight to a saved position — often deep into
 * a long file — before playback has started at all. On a 4K/HDR x265 remux with
 * sparse keyframes, that decode-forward can take a long time, and the black-screen
 * mask just sits there waiting for onRenderedFirstFrame, which is what reads as
 * "still stuck black."
 *
 * The two situations aren't the same, though: EXACT matters because audio is
 * ALREADY PLAYING when an interactive seek happens, and could race ahead of a video
 * seek landing imprecisely. On startup, nothing is playing yet — there is no audio
 * to race against — so a fast, approximate seek is completely safe here and costs
 * nothing. This uses CLOSEST_SYNC only for the one-time resume seek, then switches
 * back to EXACT the moment the first frame is actually on screen and real playback
 * (with audio) begins.
 */
@UnstableApi
fun preparePlayerWithResume(
    player: ExoPlayer,
    mediaItem: MediaItem,
    resumePositionMs: Long,
) {
    if (resumePositionMs <= 0L) {
        // Fresh start (position 0) never hits the slow-decode problem — no special
        // handling needed, EXACT is already fine (and irrelevant, since there's no seek).
        player.setMediaItem(mediaItem)
        player.prepare()
        return
    }

    player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
    player.setMediaItem(mediaItem, resumePositionMs)
    player.prepare()

    // Self-removing listener — without this, repeated Continue Watching launches on
    // a reused/singleton player instance would accumulate one listener per resume
    // over the app's lifetime.
    lateinit var resumeListener: Player.Listener
    resumeListener = object : Player.Listener {
        override fun onRenderedFirstFrame() {
            // Real playback has started — switch back to EXACT so any further
            // interactive seek (scrub, double-tap ±10s) stays desync-free.
            player.setSeekParameters(SeekParameters.EXACT)
            player.removeListener(resumeListener)
        }
    }
    player.addListener(resumeListener)
}
