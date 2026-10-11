package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * High-performance in-memory LRU cache and background extractor for video thumbnails.
 * Prevents main thread jank and redundant extraction during rapid LazyColumn scrolling.
 */
object VideoThumbnailCache {

    // Cache sized to 25% of available heap memory for fast scroll frame rendering
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 4

    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    private val mutex = Mutex()

    fun get(key: String): Bitmap? = memoryCache.get(key)

    fun put(key: String, bitmap: Bitmap) {
        if (key.isNotBlank()) {
            memoryCache.put(key, bitmap)
        }
    }

    suspend fun loadOrExtract(
        context: Context,
        key: String,
        uri: Uri,
        filePath: String,
        targetWidth: Int = 380,
        targetHeight: Int = 240
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (key.isBlank()) return@withContext null

        get(key)?.let { return@withContext it }

        var bitmap: Bitmap? = null

        // 1. ContentResolver.loadThumbnail for Android Q+ (fast hardware decoder)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri != Uri.EMPTY && uri.scheme == "content") {
            try {
                bitmap = context.contentResolver.loadThumbnail(uri, Size(targetWidth, targetHeight), null)
            } catch (_: Exception) {}
        }

        // 2. MediaMetadataRetriever on Uri
        if (bitmap == null && uri != Uri.EMPTY) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                bitmap = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.frameAtTime
            } catch (_: Exception) {
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
        }

        // 3. MediaMetadataRetriever on file path
        if (bitmap == null && filePath.isNotEmpty()) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(filePath)
                bitmap = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.frameAtTime
            } catch (_: Exception) {
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
        }

        bitmap?.let {
            put(key, it)
        }

        bitmap
    }
}
