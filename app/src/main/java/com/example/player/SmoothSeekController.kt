package com.example.player

import androidx.compose.runtime.*
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Double-tap skip that acts on the first tap.
 *
 * The old controller waited for 280 ms of quiet before seeking, so every skip
 * felt late and a single tap appeared to do nothing for a moment. Now each tap
 * seeks at once. Rapid taps add up from the last target (not from the
 * still-moving playhead), so three quick "+10s" taps land on +30s. ExoPlayer
 * replaces a pending seek with the newest one, so taps never queue up behind
 * each other's decoding.
 */
@UnstableApi
class SmoothSeekController(
    private val player: ExoPlayer,
    private val scope: CoroutineScope,
) {
    /** Accumulated offset of the current tap burst — drives the on-screen "+20s" indicator. */
    var pendingOffsetMs by mutableStateOf(0L)
        private set

    var isPending by mutableStateOf(false)
        private set

    private var debounceJob: Job? = null
    private var burstTargetMs: Long? = null

    fun seekBy(deltaMs: Long) {
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0L }
        val base = burstTargetMs ?: player.currentPosition.coerceAtLeast(0L)
        var target = (base + deltaMs).coerceAtLeast(0L)
        if (duration != null) target = target.coerceAtMost(duration)
        burstTargetMs = target
        pendingOffsetMs += deltaMs
        isPending = true
        player.seekTo(target)
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(BURST_WINDOW_MS)
            burstTargetMs = null
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
        burstTargetMs = null
        player.seekTo(targetMs)
    }

    private companion object {
        const val BURST_WINDOW_MS = 650L
    }
}

@UnstableApi
@Composable
fun rememberSmoothSeekController(player: ExoPlayer): SmoothSeekController {
    val scope = rememberCoroutineScope()
    return remember(player) { SmoothSeekController(player, scope) }
}
