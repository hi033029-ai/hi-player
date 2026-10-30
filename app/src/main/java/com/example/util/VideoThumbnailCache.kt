package com.example.util

import android.content.Context
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
    private val cacheSizeKb = ArtworkMemoryPolicy.cacheBudgetKb(Runtime.getRuntime().maxMemory() / 1024L)
    private val registered = AtomicBoolean(false)
    private val failures = LruCache<String, Long>(128)

    private val memoryCache = object : LruCache<String, Bitmap>(cacheSizeKb) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int = ((bitmap.byteCount.toLong() + 1023) / 1024).toInt().coerceAtLeast(1)
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
        // Remote thumbnails must use Coil/artwork URLs, not a network retriever
        // that can stall every local thumbnail behind the serial decoder.
        if (uri.scheme !in setOf("content", "file") && !filePath.startsWith('/')) return@withContext null
        registerTrim(context)
        get(key)?.let { return@withContext it }

        extractorMutex.withLock {
            currentCoroutineContext().ensureActive()
            get(key)?.let { return@withLock it }
            if (failures.get(key)?.let { SystemClock.elapsedRealtime() - it < 30_000L } == true) return@withLock null

            val bitmap = loadFromContentResolver(context, uri, targetWidth, targetHeight)
                ?: if (uri.scheme in setOf("content", "file")) extractFromUri(context, uri, targetWidth, targetHeight) else null
            val resolved = bitmap ?: if (filePath.startsWith('/')) extractFromPath(filePath, targetWidth, targetHeight) else null
            currentCoroutineContext().ensureActive()
            if (resolved == null) failures.put(key, SystemClock.elapsedRealtime())
            resolved?.let { put(key, it) }
            resolved
        }
    }

    private fun registerTrim(context: Context) {
        if (!registered.compareAndSet(false, true)) return
        context.applicationContext.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
            override fun onLowMemory() { memoryCache.evictAll(); failures.evictAll() }
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) memoryCache.evictAll()
                else if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) memoryCache.trimToSize(cacheSizeKb / 2)
            }
        })
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
            if (!isMostlyBlack(frame)) {
                if (fallback !== frame) fallback?.recycle()
                return frame
            }
            if (fallback == null) fallback = frame else if (fallback !== frame) frame.recycle()
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
