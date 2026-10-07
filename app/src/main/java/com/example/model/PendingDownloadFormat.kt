package com.example.model

/** Shared recognition rules for browser and download-manager temporary media files. */
object PendingDownloadFormat {
    val suffixes: List<String> = listOf(
        ".partialdownload",
        ".crdownload",
        ".ucdownload",
        ".opdownload",
        ".dltemp",
        ".download",
        ".partial",
        ".pending",
        ".temp",
        ".part",
        ".tmp"
    )

    val videoExtensions: Set<String> = setOf(
        "mp4", "mkv", "webm", "mov", "avi", "ts", "m4v", "3gp", "flv", "wmv", "vob"
    )

    fun suffixFor(name: String): String? {
        val lowerName = name.lowercase()
        return suffixes.firstOrNull(lowerName::endsWith)
    }

    fun isPending(name: String): Boolean = suffixFor(name) != null

    fun originalName(name: String): String = suffixFor(name)
        ?.let { suffix -> name.dropLast(suffix.length) }
        ?.ifBlank { name }
        ?: name
}
