package com.example.recognition

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Records raw mic audio for fingerprinting. AcoustID/Chromaprint don't need long
 * clips — 10-12 seconds is the commonly recommended window, long enough for a
 * reliable fingerprint without asking the user to hold still forever.
 */
object AudioCapture {

    const val SAMPLE_RATE = 44100
    const val CHANNELS = 1
    private const val RECORD_SECONDS = 5 // matches short-form content (TikTok clips are often 6-7s)
    private const val MIN_USABLE_SECONDS = 2 // below this, the fingerprint is too unreliable to bother sending

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

            // BUG FIX: previously always returned the full fixed-size `buffer`
            // regardless of how much was actually captured — if recording was
            // interrupted early (coroutine cancelled, lifecycle event, etc.), the
            // caller would see a full-length array padded with silent zeros and
            // wrongly compute a full 12s duration from it. AcoustID then either
            // rejects the resulting mismatched/mostly-silent fingerprint outright
            // (HTTP 400) or fails to match anything meaningful. Trimming to what
            // was actually read makes `pcm.size` — and everything downstream that
            // derives duration from it — honest about what was really captured.
            val actual = buffer.copyOf(offset)

            if (offset < SAMPLE_RATE * MIN_USABLE_SECONDS) {
                throw IOException(
                    "Recording was cut short (only ${offset / SAMPLE_RATE.toFloat()}s captured) " +
                        "— try again and make sure nothing interrupts listening early.",
                )
            }

            actual
        } finally {
            recorder.stop()
            recorder.release()
        }
    }
}
