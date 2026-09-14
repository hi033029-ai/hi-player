package com.example.player

import androidx.compose.runtime.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Fixes "backward/forward not smooth on remux/HEVC files".
 *
 * Root cause: every seekTo() forces MediaCodec to flush and reconfigure, then find
 * and decode from the nearest keyframe. On remuxed MKV/HEVC files that often have
 * sparse or missing seek indexes (Cues), that round trip is genuinely expensive —
 * and if the user taps rewind/FF a few times quickly (or drags the seek bar), each
 * tap fired its own immediate seekTo(), so the codec was interrupted mid-seek by
 * the next one before finishing the last, which is what actually reads as
 * "stuttering" or "not smooth" — it's seek requests piling up, not decode speed.
 *
 * Fix: coalesce rapid seek requests into a single call. Taps accumulate an offset;
 * only after a short quiet period (280ms of no further taps) does one seekTo() fire
 * with the total offset — so five quick "-10s" taps do exactly one keyframe seek to
 * -50s, not five sequential ones fighting each other.
 *
 * Combined with SeekParameters.CLOSEST_SYNC (set once, in OptimizedPlayerBuilder),
 * which snaps to the nearest keyframe instead of decoding forward to an exact
 * frame, this is the actual fix for "any type of file, remux, HEVC" — it doesn't
 * depend on the file having a full seek index.
 */
@UnstableApi
class SmoothSeekController(
    private val player: ExoPlayer,
    private val scope: CoroutineScope,
) {
    /** Accumulated but not-yet-applied offset — drive an on-screen "-20s" indicator with this. */
    var pendingOffsetMs by mutableStateOf(0L)
        private set

    var isPending by mutableStateOf(false)
        private set

    private var debounceJob: Job? = null

    fun seekBy(deltaMs: Long) {
        pendingOffsetMs += deltaMs
        isPending = true
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(280)
            val durationMs = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
            val target = (player.currentPosition + pendingOffsetMs).coerceIn(0, durationMs)
            player.seekTo(target)
            pendingOffsetMs = 0L
            isPending = false
        }
    }

    /**
     * For continuous scrubbing (dragging the seek bar): throttles to ~1 seek per
     * 150ms instead of one per pixel of drag, using the same coalescing idea.
     * Call this on every drag delta; call commitFinalSeek() on release for an exact
     * final position instead of the last throttled one.
     */
    fun seekWhileDragging(targetMs: Long) {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(150)
            player.seekTo(targetMs)
        }
    }

    fun commitFinalSeek(targetMs: Long) {
        debounceJob?.cancel()
        player.seekTo(targetMs)
    }
}

@UnstableApi
@Composable
fun rememberSmoothSeekController(player: ExoPlayer): SmoothSeekController {
    val scope = rememberCoroutineScope()
    return remember(player) { SmoothSeekController(player, scope) }
}
