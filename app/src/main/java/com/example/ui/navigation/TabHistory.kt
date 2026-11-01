package com.example.ui.navigation

/** Tab origins are explicit, never guessed from a default tab or an effect. */
object TabHistory {
    fun open(history: List<String>, destination: String): List<String> {
        val existing = history.indexOfLast { it == destination }
        // Selecting Home (or another visited root) unwinds its old children.
        return if (existing >= 0) history.take(existing + 1) else (history + destination).takeLast(32)
    }

    fun back(history: List<String>): List<String> =
        if (history.size > 1) history.dropLast(1) else history
}
