package com.example.player

import java.util.concurrent.ConcurrentHashMap

/** Tracks files that are still being written so Media3 can wait at EOF. */
object DownloadingFilesRegistry {
    private val activePaths = ConcurrentHashMap.newKeySet<String>()

    fun markDownloading(path: String) {
        if (path.isNotBlank()) activePaths.add(path)
    }

    fun markComplete(path: String) {
        if (path.isNotBlank()) activePaths.remove(path)
    }

    fun isDownloading(path: String): Boolean = activePaths.contains(path)
}
