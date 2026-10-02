package com.example.player

import java.util.concurrent.ConcurrentHashMap

/** Tracks files that are still being written so Media3 can wait at EOF. */
object DownloadingFilesRegistry {
    private const val STALL_TIMEOUT_MS = 12_000L

    private data class DownloadState(
        val lastKnownSize: Long,
        val lastGrowthAtMs: Long
    )

    private val activePaths = ConcurrentHashMap<String, DownloadState>()

    fun markDownloading(path: String, sizeBytes: Long? = null) {
        if (path.isBlank()) return
        val now = System.currentTimeMillis()
        activePaths.compute(path) { _, previous ->
            val size = sizeBytes ?: previous?.lastKnownSize ?: 0L
            when {
                previous == null -> DownloadState(size, now)
                size > previous.lastKnownSize -> DownloadState(size, now)
                else -> previous
            }
        }
    }

    fun markComplete(path: String) {
        if (path.isNotBlank()) activePaths.remove(path)
    }

    fun isDownloading(path: String): Boolean = activePaths.containsKey(path)

    /** True only while the file has grown recently, preventing an indefinite player wait. */
    fun isActivelyGrowing(path: String): Boolean {
        val state = activePaths[path] ?: return false
        return System.currentTimeMillis() - state.lastGrowthAtMs <= STALL_TIMEOUT_MS
    }
}
