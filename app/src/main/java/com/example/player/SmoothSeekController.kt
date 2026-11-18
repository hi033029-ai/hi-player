package com.example.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Rapid skips accumulate even while a decoder is still confirming the previous seek. */
internal class SmoothSeekController(
    private val scope: CoroutineScope,
    private val position: () -> Long,
    private val clamp: (Long) -> Long,
    private val seek: (Long) -> Unit,
) {
    var targetMs by mutableStateOf<Long?>(null)
        private set
    var offsetMs by mutableStateOf(0L)
        private set
    private var originMs = 0L
    private var dismissJob: Job? = null

    fun seekBy(deltaMs: Long) {
        val base = targetMs ?: position().coerceAtLeast(0L).also { originMs = it }
        val target = clamp(base + deltaMs)
        targetMs = target
        offsetMs = target - originMs
        if (target != base) seek(target)
        dismissJob?.cancel()
        dismissJob = scope.launch {
            delay(650L)
            targetMs = null
        }
    }

    fun clear() {
        dismissJob?.cancel()
        targetMs = null
    }
}
