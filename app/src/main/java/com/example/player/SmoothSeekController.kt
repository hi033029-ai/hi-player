package com.example.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@UnstableApi
class SmoothSeekController(private val player: ExoPlayer, private val scope: CoroutineScope) {
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
            val duration = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
            player.seekTo((player.currentPosition + pendingOffsetMs).coerceIn(0, duration))
            pendingOffsetMs = 0L
            isPending = false
        }
    }

    fun seekWhileDragging(targetMs: Long) {
        debounceJob?.cancel()
        debounceJob = scope.launch { delay(150); player.seekTo(targetMs) }
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
