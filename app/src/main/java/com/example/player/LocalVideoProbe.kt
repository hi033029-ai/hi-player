package com.example.player

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** A bounded metadata probe. A slow provider never occupies the UI or spawns more threads. */
internal object LocalVideoProbe {
    private val executor = ThreadPoolExecutor(1, 1, 20, TimeUnit.SECONDS,
        LinkedBlockingQueue<Runnable>(1), { task -> Thread(task, "HiPlayer-VideoProbe") }, ThreadPoolExecutor.AbortPolicy())
        .apply { allowCoreThreadTimeOut(true) }

    data class Result(val useMedia3: Boolean = true, val displaySize: Pair<Int, Int> = 0 to 0)

    fun submit(context: Context, uri: Uri, result: (Result) -> Unit): Boolean = try {
        executor.execute {
            val extractor = MediaExtractor()
            val useMedia3 = try {
                extractor.setDataSource(context, uri, null)
                var video = Result()
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                    if (mime.startsWith("video/")) {
                        val transfer = if (format.containsKey("color-transfer")) format.getInteger("color-transfer") else 0
                        fun dimension(key: String, fallback: Int) =
                            if (format.containsKey(key)) format.getInteger(key) else fallback
                        val sarWidth = dimension("sar-width", 1).coerceAtLeast(1)
                        val sarHeight = dimension("sar-height", 1).coerceAtLeast(1)
                        val width = Math.round(dimension(MediaFormat.KEY_WIDTH, 0) * sarWidth.toDouble() / sarHeight).toInt()
                        val height = dimension(MediaFormat.KEY_HEIGHT, 0)
                        val size = if (dimension("rotation-degrees", 0) % 180 != 0) height to width else width to height
                        video = Result(mime != "video/avc" || transfer == 6 || transfer == 7, size)
                        break
                    }
                }
                video
            } catch (_: Exception) { Result() }
              finally { extractor.release() }
            result(useMedia3)
        }
        true
    } catch (_: java.util.concurrent.RejectedExecutionException) { false }
}
