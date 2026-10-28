package com.example.player

import com.example.model.PendingDownloadFormat
import java.util.concurrent.ConcurrentHashMap

/** Tracks files that are still being written so Media3 can wait at EOF. */
object DownloadingFilesRegistry {
    private const val STALL_TIMEOUT_MS = 12_000L
    private const val FRESH_FINAL_NAME_WINDOW_MS = 90_000L

    private data class DownloadState(
        val lastKnownSize: Long,
        val lastGrowthAtMs: Long,
        val speedBytesPerSecond: Long = 0L
    )

    private data class SettledFinalName(
        val sizeBytes: Long,
        val lastModifiedMs: Long
    )

    data class ProgressSnapshot(
        val downloadedBytes: Long,
        val speedBytesPerSecond: Long
    )

    private val ownedPaths = ConcurrentHashMap.newKeySet<String>()
    private val completedAliases = ConcurrentHashMap<String, String>()
    fun markOwnedDownloading(path: String, bytes: Long) { ownedPaths.add(path); markDownloading(path, bytes) }
    fun completeOwned(path: String, uri: String? = null) {
        if (uri != null) completedAliases[path] = uri
        ownedPaths.remove(path); markComplete(path)
    }
    fun completedUri(path: String): String? = completedAliases[path]

    private val activePaths = ConcurrentHashMap<String, DownloadState>()
    private val settledFinalNames = ConcurrentHashMap<String, SettledFinalName>()

    fun markDownloading(path: String, sizeBytes: Long? = null) {
        if (path.isBlank()) return
        val now = System.currentTimeMillis()
        activePaths.compute(path) { _, previous ->
            val size = sizeBytes ?: previous?.lastKnownSize ?: 0L
            when {
                previous == null -> DownloadState(size, now)
                size > previous.lastKnownSize -> {
                    val elapsedMs = (now - previous.lastGrowthAtMs).coerceAtLeast(1L)
                    val speed = ((size - previous.lastKnownSize) * 1_000L / elapsedMs)
                        .coerceAtLeast(0L)
                    DownloadState(size, now, speed)
                }
                else -> previous
            }
        }
    }

    fun markComplete(path: String) {
        if (path.isNotBlank()) activePaths.remove(path)
    }

    /**
     * Some downloaders write straight to `movie.mp4` instead of using a
     * temporary suffix. Treat a recently modified Download video as growing
     * until its writes have settled, then let normal local playback take over.
     */
    fun observeFinalNameDownload(key: String, sizeBytes: Long, lastModifiedMs: Long): Boolean {
        if (key.isBlank()) return false
        val now = System.currentTimeMillis()
        val settled = settledFinalNames[key]
        if (settled != null) {
            val resumed = sizeBytes > settled.sizeBytes || lastModifiedMs > settled.lastModifiedMs
            if (!resumed) {
                if (now - lastModifiedMs > FRESH_FINAL_NAME_WINDOW_MS) {
                    settledFinalNames.remove(key, settled)
                }
                return false
            }
            settledFinalNames.remove(key, settled)
        }
        val existing = activePaths[key]
        if (existing == null) {
            if (now - lastModifiedMs > FRESH_FINAL_NAME_WINDOW_MS) return false
            activePaths[key] = DownloadState(sizeBytes, now)
            return true
        }
        if (sizeBytes > existing.lastKnownSize) {
            val elapsedMs = (now - existing.lastGrowthAtMs).coerceAtLeast(1L)
            val speed = ((sizeBytes - existing.lastKnownSize) * 1_000L / elapsedMs)
                .coerceAtLeast(0L)
            activePaths[key] = DownloadState(sizeBytes, now, speed)
            return true
        }
        if (now - existing.lastGrowthAtMs <= STALL_TIMEOUT_MS) return true
        // The file has stopped growing long enough to be treated as an ordinary
        // local video. Remember its final observation so the next periodic
        // scan does not re-register the same recently created file.
        activePaths.remove(key, existing)
        settledFinalNames[key] = SettledFinalName(sizeBytes, lastModifiedMs)
        return false
    }

    /** Removes content-URI entries that disappeared from the current pending scan. */
    fun markMissingContentDownloadsComplete(activeContentUris: Set<String>) {
        activePaths.keys
            .filter { key -> key.startsWith("content://") && key !in ownedPaths && key !in activeContentUris }
            .forEach(activePaths::remove)
    }

    /**
     * Temporary browser files are often atomically renamed to their final
     * media name. Remove the old path-only entry when the next scan no longer
     * sees it, so a completed file cannot leave stale growing state behind.
     */
    fun markMissingPendingFileDownloadsComplete(activeFilePaths: Set<String>) {
        activePaths.keys
            .filter { key ->
                key.startsWith("/") && key !in ownedPaths &&
                    PendingDownloadFormat.isPending(key.substringAfterLast('/')) &&
                    key !in activeFilePaths
            }
            .forEach(activePaths::remove)
    }

    fun isDownloading(path: String): Boolean = activePaths.containsKey(path)

    fun progress(path: String): ProgressSnapshot? = activePaths[path]?.let {
        ProgressSnapshot(
            downloadedBytes = it.lastKnownSize,
            speedBytesPerSecond = it.speedBytesPerSecond
        )
    }

    /** True only while the file has grown recently, preventing an indefinite player wait. */
    fun isActivelyGrowing(path: String): Boolean {
        val state = activePaths[path] ?: return false
        return System.currentTimeMillis() - state.lastGrowthAtMs <= STALL_TIMEOUT_MS
    }
}
