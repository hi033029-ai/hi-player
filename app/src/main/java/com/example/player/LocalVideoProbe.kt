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

    fun submit(context: Context, uri: Uri, result: (Boolean) -> Unit): Boolean = try {
        executor.execute {
            val extractor = MediaExtractor()
            val useMedia3 = try {
                extractor.setDataSource(context, uri, null)
                var advanced = true // unknown color metadata must use the color-aware backend
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                    if (mime.startsWith("video/")) {
                        val transfer = if (format.containsKey("color-transfer")) format.getInteger("color-transfer") else 0
                        advanced = mime != "video/avc" || transfer == 6 || transfer == 7
                        break
                    }
                }
                advanced
            } catch (_: Exception) { true }
              finally { extractor.release() }
            result(useMedia3)
        }
        true
    } catch (_: java.util.concurrent.RejectedExecutionException) { false }
}
