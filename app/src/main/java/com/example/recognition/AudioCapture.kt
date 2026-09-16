package com.example.recognition

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Records raw mic audio for fingerprinting. Five seconds keeps recognition fast
 * while still providing a useful fingerprint for many clearly audible songs.
 */
object AudioCapture {

    const val SAMPLE_RATE = 44100
    const val CHANNELS = 1
    private const val RECORD_SECONDS = 5

    /** Requires RECORD_AUDIO permission already granted — caller's responsibility. */
    suspend fun recordPcm(): ShortArray = withContext(Dispatchers.IO) {
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val totalSamples = SAMPLE_RATE * RECORD_SECONDS
        val buffer = ShortArray(totalSamples)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBufferSize, totalSamples / 4),
        )

        try {
            recorder.startRecording()
            var offset = 0
            while (offset < totalSamples) {
                val read = recorder.read(buffer, offset, totalSamples - offset)
                if (read <= 0) break
                offset += read
            }
            buffer
        } finally {
            recorder.stop()
            recorder.release()
        }
    }
}
