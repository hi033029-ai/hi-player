package com.example.util

internal object ArtworkMemoryPolicy {
    /** No fixed 24MB floor on small heaps; video plus audio budgets stay bounded. */
    fun cacheBudgetKb(heapKb: Long, audio: Boolean = false): Int {
        val divisor = if (audio) 32L else 16L
        val ceiling = if (audio) 12L * 1024 else 32L * 1024
        return (heapKb / divisor).coerceAtLeast(1).coerceAtMost(ceiling).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
    fun sampleSize(width: Int, height: Int, target: Int): Int {
        if (target <= 0) return 1
        var sample = 1
        while (sample <= Int.MAX_VALUE / 2 && maxOf(width, height) / (sample * 2) >= target) sample *= 2
        return sample
    }
}
