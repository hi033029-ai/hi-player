package com.example.player

/** A conservative high-water mark of media actually buffered by the player.
 * Byte counts cannot be converted reliably to time for variable-rate media. */
internal class GrowingSeekWindow {
    var bufferedThroughMs: Long = 0L
        private set

    fun reset() { bufferedThroughMs = 0L }

    fun observe(positionMs: Long, bufferedMs: Long, ready: Boolean, seekable: Boolean) {
        if (ready && seekable) {
            bufferedThroughMs = maxOf(bufferedThroughMs, positionMs.coerceAtLeast(0L), bufferedMs.coerceAtLeast(0L))
        }
    }

    fun clamp(targetMs: Long, durationMs: Long?, growing: Boolean, seekable: Boolean, currentMs: Long): Long {
        if (growing && !seekable) return currentMs.coerceAtLeast(0L)
        var result = targetMs.coerceAtLeast(0L)
        if (durationMs != null && durationMs > 0L) result = result.coerceAtMost(durationMs)
        if (growing) result = result.coerceAtMost(bufferedThroughMs)
        return result
    }
}
