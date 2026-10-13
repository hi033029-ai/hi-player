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
 * Memory-bounded thumbnail cache and background extractor for video lists.
 *
 * Frames are scaled before caching so browsing a folder of 4K videos cannot
 * fill the heap with full-resolution bitmaps or cause scroll-time GC stalls.
 */
object VideoThumbnailCache {

    private const val TARGET_WIDTH = 380
    private const val TARGET_HEIGHT = 240
    private const val MIN_CACHE_KB = 24 * 1024
    private const val MAX_CACHE_KB = 96 * 1024

    private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024L).toInt()
    private val cacheSizeKb = (maxMemoryKb / 8).coerceIn(MIN_CACHE_KB, MAX_CACHE_KB)

    private val memoryCache = object : LruCache<String, Bitmap>(cacheSizeKb) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int = bitmap.byteCount / 1024
    }

    // Serializing retrieval avoids several hardware decoders competing while a
    // LazyColumn is flung quickly. Work remains on Dispatchers.IO.
    private val extractorMutex = Mutex()

    fun get(key: String): Bitmap? = memoryCache.get(key)

    fun put(key: String, bitmap: Bitmap) {
        if (key.isNotBlank() && !bitmap.isRecycled) memoryCache.put(key, bitmap)
    }

    suspend fun loadOrExtract(
        context: Context,
        key: String,
        uri: Uri,
        filePath: String,
        targetWidth: Int = TARGET_WIDTH,
        targetHeight: Int = TARGET_HEIGHT
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (key.isBlank()) return@withContext null
        get(key)?.let { return@withContext it }

        extractorMutex.withLock {
            get(key)?.let { return@withLock it }

            val bitmap = loadFromContentResolver(context, uri, targetWidth, targetHeight)
                ?: extractFromUri(context, uri, targetWidth, targetHeight)
                ?: extractFromPath(filePath, targetWidth, targetHeight)

            bitmap?.let { put(key, it) }
            bitmap
        }
    }

    private fun loadFromContentResolver(
        context: Context,
        uri: Uri,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || uri == Uri.EMPTY || uri.scheme != "content") return null
        return runCatching {
            context.contentResolver.loadThumbnail(uri, Size(targetWidth, targetHeight), null)
        }.getOrNull()
    }

    private fun extractFromUri(
        context: Context,
        uri: Uri,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? {
        if (uri == Uri.EMPTY) return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            extractUsefulFrame(retriever, targetWidth, targetHeight)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun extractFromPath(
        filePath: String,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? {
        if (filePath.isBlank()) return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(filePath)
            extractUsefulFrame(retriever, targetWidth, targetHeight)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * A frame at exactly one second is often a studio-black intro. Prefer a
     * small, scaled frame a little further into the media and retain a valid
     * fallback when the source only exposes one keyframe.
     */
    private fun extractUsefulFrame(
        retriever: MediaMetadataRetriever,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? {
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
            ?.coerceAtLeast(0L)
            ?: 0L
        val preferredMs = when {
            durationMs <= 0L -> 2_000L
            else -> (durationMs / 10L).coerceIn(1_500L, 6_000L)
        }
        val candidateTimesUs = linkedSetOf(
            preferredMs * 1_000L,
            2_000_000L,
            1_000_000L
        )
        var fallback: Bitmap? = null

        for (timeUs in candidateTimesUs) {
            val frame = scaledFrameAt(retriever, timeUs, targetWidth, targetHeight) ?: continue
            if (fallback == null) fallback = frame
            if (!isMostlyBlack(frame)) return frame
        }
        return fallback
    }

    private fun scaledFrameAt(
        retriever: MediaMetadataRetriever,
        timeUs: Long,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? {
        val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            retriever.getScaledFrameAtTime(
                timeUs,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                targetWidth,
                targetHeight
            )
        } else {
            retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } ?: return null

        if (frame.width <= targetWidth && frame.height <= targetHeight) return frame
        val scale = minOf(targetWidth.toFloat() / frame.width, targetHeight.toFloat() / frame.height)
        val width = (frame.width * scale).toInt().coerceAtLeast(1)
        val height = (frame.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(frame, width, height, true)
        if (scaled !== frame) frame.recycle()
        return scaled
    }

    private fun isMostlyBlack(bitmap: Bitmap): Boolean {
        val sampleWidth = bitmap.width.coerceAtMost(12)
        val sampleHeight = bitmap.height.coerceAtMost(8)
        if (sampleWidth == 0 || sampleHeight == 0) return true
        val xStep = (bitmap.width / sampleWidth).coerceAtLeast(1)
        val yStep = (bitmap.height / sampleHeight).coerceAtLeast(1)
        var brightnessTotal = 0L
        var samples = 0
        for (y in 0 until bitmap.height step yStep) {
            for (x in 0 until bitmap.width step xStep) {
                val color = bitmap.getPixel(x, y)
                brightnessTotal += (android.graphics.Color.red(color) + android.graphics.Color.green(color) + android.graphics.Color.blue(color)) / 3
                samples++
                if (samples >= 96) break
            }
            if (samples >= 96) break
        }
        return samples == 0 || brightnessTotal / samples < 14L
    }
}
