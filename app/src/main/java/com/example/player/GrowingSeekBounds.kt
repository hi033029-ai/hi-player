package com.example.player

/** Bounds forward seeks for files whose container duration/index is still changing. */
internal object GrowingSeekBounds {
    private const val BUFFERED_AHEAD_SAFETY_DIVISOR = 10L

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
        val bufferedAhead = (buffered - current).coerceAtLeast(0L)
        val safetyMargin = bufferedAhead / BUFFERED_AHEAD_SAFETY_DIVISOR +
            if (bufferedAhead % BUFFERED_AHEAD_SAFETY_DIVISOR == 0L) 0L else 1L
        val safeForwardLimit = (buffered - safetyMargin).coerceAtLeast(current)
        return requested.coerceAtMost(safeForwardLimit)
    }
}
