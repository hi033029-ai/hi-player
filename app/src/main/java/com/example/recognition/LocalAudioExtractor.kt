package com.example.recognition

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext

/** Decoded mono PCM from a local video's audio track. */
data class PcmSample(val samples: ShortArray, val sampleRate: Int, val channels: Int = 1) {
    val durationSeconds: Int get() = (samples.size / sampleRate).coerceAtLeast(1)
}

object LocalAudioExtractor {
    private const val SAMPLE_SECONDS = 10
    private const val TIMEOUT_US = 20_000L

    suspend fun extract(context: Context, uri: Uri): PcmSample = withContext(Dispatchers.Default) {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                if (afd.length >= 0L) extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                else extractor.setDataSource(afd.fileDescriptor)
            } ?: extractor.setDataSource(context, uri, emptyMap())

            var trackIndex = -1
            var inputFormat: MediaFormat? = null
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    trackIndex = index
                    inputFormat = format
                    break
                }
            }
            check(trackIndex >= 0 && inputFormat != null) { "Video has no audio track" }
            extractor.selectTrack(trackIndex)

            val mime = inputFormat!!.getString(MediaFormat.KEY_MIME)!!
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            val output = ArrayList<Short>(SAMPLE_SECONDS * 48_000)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var outputRate = formatInteger(inputFormat!!, MediaFormat.KEY_SAMPLE_RATE, 44_100)
            var outputChannels = formatInteger(inputFormat!!, MediaFormat.KEY_CHANNEL_COUNT, 1)
            val maxSamples = outputRate * SAMPLE_SECONDS

            while (!outputDone && output.size < maxSamples) {
                coroutineContext.ensureActive()
                if (!inputDone) {
                    val inputIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputIndex)!!
                        inputBuffer.clear()
                        val size = extractor.readSampleData(inputBuffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = decoder.dequeueOutputBuffer(info, TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val format = decoder.outputFormat
                        outputRate = formatInteger(format, MediaFormat.KEY_SAMPLE_RATE, outputRate)
                        outputChannels = formatInteger(format, MediaFormat.KEY_CHANNEL_COUNT, outputChannels)
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outputIndex >= 0) {
                        val buffer = decoder.getOutputBuffer(outputIndex)
                        if (buffer != null && info.size > 0) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            appendMonoPcm(buffer.slice().order(ByteOrder.LITTLE_ENDIAN), outputChannels, output)
                        }
                        decoder.releaseOutputBuffer(outputIndex, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
                    }
                }
            }
            check(output.size >= outputRate * 2) { "Audio track is shorter than two seconds" }
            PcmSample(output.take(maxSamples).toShortArray(), outputRate, 1)
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            extractor.release()
        }
    }

    private fun appendMonoPcm(buffer: ByteBuffer, channels: Int, output: MutableList<Short>) {
        val safeChannels = channels.coerceAtLeast(1)
        while (buffer.remaining() >= safeChannels * 2) {
            var sum = 0
            repeat(safeChannels) { sum += buffer.short.toInt() }
            output += (sum / safeChannels).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private fun formatInteger(format: MediaFormat, key: String, fallback: Int): Int =
        if (format.containsKey(key)) format.getInteger(key) else fallback
}
