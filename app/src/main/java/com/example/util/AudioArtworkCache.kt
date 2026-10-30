package com.example.util

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import android.util.LruCache
import com.example.model.AudioItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/** Shared bounded/sampled art, rather than a full ID3 decoder per visible Compose row. */
internal object AudioArtworkCache {
    private const val TARGET = 192
    private val budgetKb = ArtworkMemoryPolicy.cacheBudgetKb(Runtime.getRuntime().maxMemory() / 1024, audio = true)
    private val cache = object : LruCache<String, Bitmap>(budgetKb) {
        override fun sizeOf(key: String, value: Bitmap): Int = ((value.byteCount.toLong() + 1023) / 1024).toInt().coerceAtLeast(1)
    }
    private val failures = LruCache<String, Long>(128)
    private val decoder = Mutex()
    private val registered = AtomicBoolean(false)
    fun key(track: AudioItem): String = "${track.path.ifBlank { track.uri.toString() }}:${track.sizeBytes}:${track.albumArtUri}"
    fun get(key: String): Bitmap? = cache.get(key)
    private fun registerTrim(context: Context) {
        if (!registered.compareAndSet(false, true)) return
        context.applicationContext.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
            override fun onLowMemory() { cache.evictAll(); failures.evictAll() }
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) cache.evictAll()
                else if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) cache.trimToSize(budgetKb / 2)
            }
        })
    }
    suspend fun load(context: Context, track: AudioItem): Bitmap? = withContext(Dispatchers.IO) {
        registerTrim(context)
        val key = key(track)
        get(key)?.let { return@withContext it }
        decoder.withLock {
            currentCoroutineContext().ensureActive()
            get(key)?.let { return@withLock it }
            if (failures.get(key)?.let { SystemClock.elapsedRealtime() - it < 30_000L } == true) return@withLock null
            var bitmap: Bitmap? = track.albumArtUri?.let { art ->
                if (art.scheme !in setOf("content", "file")) null else runCatching {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(art)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    val options = BitmapFactory.Options().apply { inSampleSize = ArtworkMemoryPolicy.sampleSize(bounds.outWidth, bounds.outHeight, TARGET) }
                    context.contentResolver.openInputStream(art)?.use { BitmapFactory.decodeStream(it, null, options) }
                }.getOrNull()
            }
            if (bitmap == null && track.uri.scheme in setOf("content", "file")) bitmap = embedded {
                setDataSource(context, track.uri)
            }
            if (bitmap == null && track.path.startsWith('/')) bitmap = embedded { setDataSource(track.path) }
            currentCoroutineContext().ensureActive()
            bitmap?.let { decoded ->
                val scale = minOf(1f, TARGET.toFloat() / maxOf(decoded.width, decoded.height))
                val result = if (scale < 1f) Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt().coerceAtLeast(1), (decoded.height * scale).toInt().coerceAtLeast(1), true) else decoded
                if (result !== decoded) decoded.recycle()
                cache.put(key, result)
                return@withLock result
            }
            failures.put(key, SystemClock.elapsedRealtime())
            null
        }
    }
    private fun embedded(source: MediaMetadataRetriever.() -> Unit): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.source()
            val data = retriever.embeddedPicture ?: return null
            // Reject pathological embedded artwork rather than decoding it unbounded.
            if (data.size > 16 * 1024 * 1024) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
            val options = BitmapFactory.Options().apply { inSampleSize = ArtworkMemoryPolicy.sampleSize(bounds.outWidth, bounds.outHeight, TARGET) }
            BitmapFactory.decodeByteArray(data, 0, data.size, options)
        } catch (_: Exception) { null }
        finally { runCatching { retriever.release() } }
    }
}
