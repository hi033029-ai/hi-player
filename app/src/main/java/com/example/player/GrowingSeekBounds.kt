package com.example.player

/** Bounds forward seeks for files whose container duration/index is still changing. */
internal object GrowingSeekBounds {
    private const val BUFFER_SAFETY_MARGIN_MS = 750L

    fun clamp(
        requestedPositionMs: Long,
        currentPositionMs: Long,
        bufferedPositionMs: Long,
        isGrowing: Boolean,
    ): Long {
        val requested = requestedPositionMs.coerceAtLeast(0L)
        val current = currentPositionMs.coerceAtLeast(0L)
        if (!isGrowing || requested <= current) return requested

        val buffered = bufferedPositionMs.takeIf { it > 0L } ?: current
        val safeForwardLimit = (buffered - BUFFER_SAFETY_MARGIN_MS).coerceAtLeast(current)
        return requested.coerceAtMost(safeForwardLimit)
    }
}
