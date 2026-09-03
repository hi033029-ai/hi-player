package com.example.util

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import com.example.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** Result returned by the local ShazamIO recognition backend. */
data class RecognizedSong(
    val title: String,
    val artist: String,
    val album: String?,
    val songLink: String?
)

/**
 * Recognizes music from the currently playing video. Local videos are reduced to a
 * short audio-only M4A sample so the request stays within the backend's 10 MB limit.
 */
suspend fun recognizeVideoSong(
    context: Context,
    video: VideoItem,
    positionMs: Long = 0L
): Result<RecognizedSong?> = withContext(Dispatchers.IO) {
    runCatching {
        val source = video.uri
        val remoteUrl = source.toString().takeIf { it.startsWith("http://") || it.startsWith("https://") }
        val sample = if (remoteUrl == null) extractAudioSample(context, source, positionMs) else null
        try {
            val response = postRecognition(remoteUrl, sample)
            if (!response.optBoolean("matched", false)) return@runCatching null
            RecognizedSong(
                title = response.optString("title").trim(),
                artist = response.optString("artist").trim(),
                album = response.optString("album").trim().ifBlank { null },
                songLink = response.optString("songLink").trim().ifBlank { null }
            ).takeIf { it.title.isNotBlank() }
        } finally {
            sample?.delete()
        }
    }
}

private fun extractAudioSample(context: Context, uri: Uri, positionMs: Long): File {
    val extractor = MediaExtractor()
    val output = File(context.cacheDir, "song-recognition-${UUID.randomUUID()}.m4a")
    var muxer: MediaMuxer? = null
    try {
        extractor.setDataSource(context, uri, null)
        var audioTrack = -1
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                audioTrack = index
                break
            }
        }
        require(audioTrack >= 0) { "This video has no audio track." }
        extractor.selectTrack(audioTrack)
        val format = extractor.getTrackFormat(audioTrack)
        muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val outputTrack = muxer.addTrack(format)
        muxer.start()

        val startUs = (positionMs.coerceAtLeast(0L) * 1000L)
        val endUs = startUs + 20_000_000L
        extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        val buffer = java.nio.ByteBuffer.allocate(1024 * 1024)
        val info = MediaCodecBufferInfoCompat()
        while (true) {
            buffer.clear()
            val sampleSize = extractor.readSampleData(buffer, 0)
            val timeUs = extractor.sampleTime
            if (sampleSize < 0 || timeUs < 0 || timeUs > endUs) break
            info.offset = 0
            info.size = sampleSize
            info.presentationTimeUs = (timeUs - startUs).coerceAtLeast(0L)
            info.flags = extractor.sampleFlags
            muxer.writeSampleData(outputTrack, buffer, info.toMediaCodecBufferInfo())
            extractor.advance()
        }
        muxer.stop()
        require(output.exists() && output.length() > 0) { "Could not extract audio from this video." }
        require(output.length() <= 10L * 1024L * 1024L) { "The audio sample is larger than the backend's 10 MB limit." }
        return output
    } catch (error: Throwable) {
        output.delete()
        throw error
    } finally {
        runCatching { muxer?.release() }
        extractor.release()
    }
}

private fun postRecognition(remoteUrl: String?, sample: File?): JSONObject {
    val boundary = "----HiPlayer${UUID.randomUUID()}"
    val connection = (URL("http://127.0.0.1:8765/recognize").openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        doOutput = true
        connectTimeout = 15_000
        readTimeout = 30_000
        setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        setRequestProperty("User-Agent", "HiPlayer/1.0")
    }
    connection.outputStream.use { output ->
        fun field(name: String, value: String) {
            output.write("--$boundary\r\n".toByteArray())
            output.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray())
            output.write(value.toByteArray(Charsets.UTF_8))
            output.write("\r\n".toByteArray())
        }
        if (remoteUrl != null) {
            // The local backend currently recognizes uploaded samples only.
            // Keep the URL in the multipart request for future remote support.
            field("source_url", remoteUrl)
        } else {
            requireNotNull(sample) { "No audio sample available." }
            output.write("--$boundary\r\n".toByteArray())
            output.write("Content-Disposition: form-data; name=\"file\"; filename=\"sample.m4a\"\r\n".toByteArray())
            output.write("Content-Type: audio/mp4\r\n\r\n".toByteArray())
            BufferedInputStream(FileInputStream(sample)).use { input -> input.copyTo(output) }
            output.write("\r\n".toByteArray())
        }
        output.write("--$boundary--\r\n".toByteArray())
    }
    val body = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
        ?.bufferedReader()?.use { it.readText() } ?: "{}"
    connection.disconnect()
    return JSONObject(body)
}

/** Small adapter keeping MediaMuxer code compatible with the Android SDK's buffer type. */
private class MediaCodecBufferInfoCompat {
    var offset: Int = 0
    var size: Int = 0
    var presentationTimeUs: Long = 0L
    var flags: Int = 0
    fun toMediaCodecBufferInfo() = android.media.MediaCodec.BufferInfo().also {
        it.set(offset, size, presentationTimeUs, flags)
    }
}
