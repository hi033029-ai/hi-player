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

    private val mediaStorePendingName = Regex("^\\.pending-[0-9]+-.+", RegexOption.IGNORE_CASE)

    /** Android's private on-disk name for an IS_PENDING MediaStore item, not a playable library path. */
    fun isMediaStorePendingName(name: String): Boolean = mediaStorePendingName.matches(name)

    fun suffixFor(name: String): String? {
        val lowerName = name.lowercase()
        return suffixes.firstOrNull(lowerName::endsWith)
    }

    fun isPending(name: String): Boolean = suffixFor(name) != null

    fun originalName(name: String): String {
        var normalized = name
        while (true) {
            val suffix = suffixFor(normalized) ?: break
            val stripped = normalized.dropLast(suffix.length)
            if (stripped.isBlank()) return name
            normalized = stripped
        }
        return normalized
    }
}
