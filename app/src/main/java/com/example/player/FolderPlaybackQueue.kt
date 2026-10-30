package com.example.player

import com.example.model.VideoItem

/** Queue order is the existing library order, scoped to a full parent path, not its label. */
internal object FolderPlaybackQueue {
    private fun parent(video: VideoItem): String? {
        if (video.uri.scheme in listOf("http", "https")) return null
        val path = video.path.takeIf { it.startsWith("/") }
            ?: video.uri.takeIf { it.scheme == "file" }?.path
            ?: return null
        // Normalize without disk I/O; do not match two folders with the same display name.
        return java.io.File(path).toPath().normalize().parent?.toString()
    }

    fun adjacent(current: VideoItem, library: List<VideoItem>, step: Int): VideoItem? {
        if (step != -1 && step != 1) return null
        val folder = parent(current) ?: return null
        val queue = library.filter { parent(it) == folder }.distinctBy { it.uri }
        val index = queue.indexOfFirst { it.uri == current.uri }
        if (index < 0) return null
        return queue.getOrNull(index + step) // End of folder means stop, not wrap or shuffle.
    }
}
