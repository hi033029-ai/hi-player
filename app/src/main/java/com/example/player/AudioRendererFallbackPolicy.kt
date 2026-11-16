package com.example.player

import java.util.Locale

/** Policies shared by the audio-renderer recovery path and its regression tests. */
internal object AudioRendererFallbackPolicy {
    fun shouldTryPlayableTrack(details: String): Boolean {
        val normalized = details.lowercase(Locale.ROOT)
        val audioRenderer = normalized.contains("mediacodecaudiorenderer") ||
            normalized.contains("ffmpegaudiorenderer") ||
            (normalized.contains("ffmpeg") && normalized.contains("audio"))
        val losslessCodec = normalized.contains("audio/true-hd") ||
            normalized.contains("audio/vnd.dts") ||
            normalized.contains("audio/dts")
        val unsupportedFailure = normalized.contains("unsupported") ||
            normalized.contains("not supported") ||
            normalized.contains("exceeds capabilities") ||
            normalized.contains("format_supported=no")
        val decoderFailure = normalized.contains("decoder") || normalized.contains("codec")

        // FFmpeg may report a decoder initialization error without MediaCodec's
        // NO_UNSUPPORTED_TYPE marker. A failed TrueHD/DTS renderer is still a
        // reason to try another explicitly supported audio track.
        return (audioRenderer && (unsupportedFailure || decoderFailure)) ||
            (losslessCodec && (audioRenderer || unsupportedFailure || decoderFailure))
    }

    /** Prefer common compatible bitstream tracks, then other supported tracks. */
    fun fallbackPriority(sampleMimeType: String?): Int {
        val mime = sampleMimeType.orEmpty().lowercase(Locale.ROOT)
        return when {
            mime.startsWith("audio/eac3") || mime.startsWith("audio/e-ac3") -> 0
            mime == "audio/ac3" -> 1
            mime == "audio/mp4a-latm" || mime == "audio/aac" -> 2
            mime == "audio/opus" -> 3
            mime.contains("audio/true-hd") || mime.contains("audio/vnd.dts") || mime.contains("audio/dts") -> 5
            else -> 4
        }
    }
}
