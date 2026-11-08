package com.example.downloads

/** Measures actual transferred bytes over a sliding window, not a single bursty sample. */
internal class DownloadSpeedMeter(private val windowNanos: Long = 3_000_000_000L) {
    private data class Sample(val time: Long, val bytes: Long)
    private val samples = ArrayDeque<Sample>()

    @Synchronized fun reset(bytes: Long = 0L, now: Long = 0L) {
        samples.clear()
        samples.addLast(Sample(now, bytes.coerceAtLeast(0L)))
    }

    @Synchronized fun sample(bytes: Long, now: Long): Long {
        val last = samples.lastOrNull()
        if (last == null || bytes < last.bytes || now <= last.time) reset(bytes, now)
        else samples.addLast(Sample(now, bytes))
        val cutoff = now - windowNanos
        while (samples.size > 2 && samples.first().time < cutoff) samples.removeFirst()
        val first = samples.first()
        val elapsed = now - first.time
        if (elapsed <= 0L) return 0L
        return ((bytes - first.bytes).coerceAtLeast(0L).toDouble() * 1_000_000_000.0 / elapsed)
            .toLong().coerceAtLeast(0L)
    }
}
