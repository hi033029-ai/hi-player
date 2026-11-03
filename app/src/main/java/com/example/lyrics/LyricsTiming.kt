package com.example.lyrics

/** Last timestamp at or before the actual player position, including duplicate timestamps. */
object LyricsTiming {
    fun activeLineIndex(lines: List<Pair<Long, String>>, positionMs: Long): Int {
        var low = 0
        var high = lines.size
        while (low < high) {
            val middle = low + (high - low) / 2
            if (lines[middle].first <= positionMs) low = middle + 1 else high = middle
        }
        return low - 1
    }
}
