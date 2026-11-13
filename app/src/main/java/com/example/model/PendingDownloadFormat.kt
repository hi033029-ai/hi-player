package com.example.model

/** Shared recognition rules for browser and download-manager temporary media files. */
object PendingDownloadFormat {
    private val leadingPendingMarker = Regex("^\\.pending-[0-9]+-", RegexOption.IGNORE_CASE)

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

    fun isPending(name: String): Boolean = suffixFor(name) != null || leadingPendingMarker.containsMatchIn(name)

    fun originalName(name: String): String {
        var normalized = name
        while (true) {
            val suffix = suffixFor(normalized)
            if (suffix != null) {
                val stripped = normalized.dropLast(suffix.length)
                if (stripped.isBlank()) return name
                normalized = stripped
                continue
            }
            val marker = leadingPendingMarker.find(normalized) ?: break
            val stripped = normalized.removeRange(marker.range)
            if (stripped.isBlank()) return name
            normalized = stripped
        }
        return normalized
    }

    fun isPlaceholderTitle(name: String?): Boolean {
        val clean = name?.trim()?.takeIf(String::isNotBlank)?.let(::originalName) ?: return true
        val stem = clean.substringBeforeLast('.', clean).trim().lowercase(java.util.Locale.ROOT)
        return stem == "pending video" || stem == "untitled video"
    }

    /** Prefer a real temporary-file name when MediaStore substitutes a generic pending title. */
    fun displayName(providerName: String?, fileName: String?): String {
        val provider = providerName?.trim().orEmpty()
        val diskName = fileName?.substringAfterLast('/')?.substringAfterLast('\\')?.trim().orEmpty()
        val cleanProvider = provider.takeIf(String::isNotBlank)?.let(::originalName).orEmpty()
        val cleanDisk = diskName.takeIf(String::isNotBlank)?.let(::originalName).orEmpty()
        val isPlaceholder = isPlaceholderTitle(cleanProvider)
        return if (isPlaceholder && cleanDisk.isNotBlank()) cleanDisk
        else cleanProvider.ifBlank { cleanDisk.ifBlank { "Untitled Video" } }
    }
}
