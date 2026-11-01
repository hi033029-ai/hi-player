package com.example.player

/** A playback session follows the captured display order, not a refreshed library scan. */
object PlaybackQueue {
    fun <T> snapshot(items: List<T>, key: (T) -> String): List<T> = items.distinctBy(key).toList()

    fun <T> adjacent(current: T, items: List<T>, step: Int, key: (T) -> String): T? {
        if (step != -1 && step != 1) return null
        val index = items.indexOfFirst { key(it) == key(current) }
        return if (index < 0) null else items.getOrNull(index + step)
    }
}
