package com.example.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Builds a real peak waveform by decoding the track's PCM once. The result is
 * cached in memory and in the app cache directory so replays are instant.
 * Returns null when the file cannot be decoded; callers keep a neutral bar.
 */
object AudioWaveformExtractor {
    const val BUCKETS = 72
    private const val MAX_DURATION_US = 30L * 60L * 1_000_000L
    private val memory = LruCache<String, FloatArray>(48)

    fun cached(key: String): FloatArray? = memory.get(key)

    suspend fun load(context: Context, uri: Uri, key: String, durationMs: Long): FloatArray? {
        memory.get(key)?.let { return it }
        return withContext(Dispatchers.Default) {
            val file = cacheFile(context, key)
            readCache(file)?.let { memory.put(key, it); return@withContext it }
            val scheme = uri.scheme.orEmpty().lowercase()
            if (scheme != "content" && scheme != "file" && scheme.isNotEmpty()) return@withContext null
            val peaks = runCatching { decode(context, uri, durationMs) }.getOrNull() ?: return@withContext null
            memory.put(key, peaks)
            runCatching { writeCache(file, peaks) }
            peaks
        }
    }

    /** Peaks are normalized to 0..1 against the loudest bucket of this track. */
    internal fun normalize(raw: FloatArray): FloatArray {
        val loudest = raw.maxOrNull()?.takeIf { it > 0f } ?: return FloatArray(raw.size)
        return FloatArray(raw.size) { sqrt((raw[it] / loudest).coerceIn(0f, 1f)) }
    }

    private suspend fun decode(context: Context, uri: Uri, durationHintMs: Long): FloatArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val trackIndex = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val durationUs = (if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L)
                .takeIf { it > 0 } ?: (durationHintMs * 1000L)
            if (durationUs <= 0L || durationUs > MAX_DURATION_US) return null
            extractor.selectTrack(trackIndex)
            val decoder = MediaCodec.createDecoderByType(mime).also { codec = it }
            decoder.configure(format, null, null, 0)
            decoder.start()
            val peaks = FloatArray(BUCKETS)
            var floatPcm = false
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                coroutineContext.ensureActive()
                if (!inputDone) {
                    val inIndex = decoder.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buffer = decoder.getInputBuffer(inIndex) ?: continue
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime.coerceAtLeast(0L), 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = decoder.dequeueOutputBuffer(info, 10_000)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val out = decoder.outputFormat
                        floatPcm = out.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            out.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    }
                    outIndex >= 0 -> {
                        if (info.size > 0) {
                            val bucket = ((info.presentationTimeUs.toDouble() / durationUs) * BUCKETS).toInt().coerceIn(0, BUCKETS - 1)
                            val data = decoder.getOutputBuffer(outIndex)
                            if (data != null) {
                                data.position(info.offset); data.limit(info.offset + info.size)
                                data.order(ByteOrder.nativeOrder())
                                var peak = peaks[bucket]
                                if (floatPcm) {
                                    val floats = data.asFloatBuffer()
                                    // Every 4th sample is enough for a visual peak and keeps decoding light.
                                    var i = 0
                                    while (i < floats.limit()) { peak = max(peak, abs(floats.get(i))); i += 4 }
                                } else {
                                    val shorts = data.asShortBuffer()
                                    var i = 0
                                    while (i < shorts.limit()) { peak = max(peak, abs(shorts.get(i).toInt()) / 32768f); i += 4 }
                                }
                                peaks[bucket] = peak
                            }
                        }
                        decoder.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
            return normalize(peaks).takeIf { values -> values.any { it > 0f } }
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun cacheFile(context: Context, key: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(32)
        return File(File(context.cacheDir, "waveforms").apply { mkdirs() }, "$digest.wf")
    }

    private fun readCache(file: File): FloatArray? {
        if (!file.isFile) return null
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        if (bytes.size != BUCKETS) return null
        return FloatArray(BUCKETS) { (bytes[it].toInt() and 0xFF) / 255f }
    }

    private fun writeCache(file: File, peaks: FloatArray) {
        file.writeBytes(ByteArray(peaks.size) { (peaks[it].coerceIn(0f, 1f) * 255).toInt().toByte() })
    }
}
