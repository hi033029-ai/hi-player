package com.example.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioRendererFallbackPolicyTest {
    @Test
    fun `unsupported TrueHD MediaCodec failure requests a playable track fallback`() {
        assertTrue(
            AudioRendererFallbackPolicy.shouldTryPlayableTrack(
                "MediaCodecAudioRenderer: NO_UNSUPPORTED_TYPE for audio/true-hd",
            ),
        )
    }

    @Test
    fun `DTS-HD FFmpeg initialization failure requests a playable track fallback`() {
        assertTrue(
            AudioRendererFallbackPolicy.shouldTryPlayableTrack(
                "FfmpegAudioDecoderException: decoder initialization failed for audio/vnd.dts.hd",
            ),
        )
    }

    @Test
    fun `unrelated network and video renderer errors do not request audio fallback`() {
        assertFalse(AudioRendererFallbackPolicy.shouldTryPlayableTrack("HTTP unsupported URL"))
        assertFalse(
            AudioRendererFallbackPolicy.shouldTryPlayableTrack(
                "MediaCodecVideoRenderer unsupported video/hevc profile",
            ),
        )
    }

    @Test
    fun `compatibility codecs rank before lossless TrueHD and DTS`() {
        assertTrue(AudioRendererFallbackPolicy.fallbackPriority("audio/eac3") < AudioRendererFallbackPolicy.fallbackPriority("audio/true-hd"))
        assertTrue(AudioRendererFallbackPolicy.fallbackPriority("audio/eac3-joc") < AudioRendererFallbackPolicy.fallbackPriority("audio/true-hd"))
        assertTrue(AudioRendererFallbackPolicy.fallbackPriority("audio/ac3") < AudioRendererFallbackPolicy.fallbackPriority("audio/vnd.dts.hd"))
        assertTrue(AudioRendererFallbackPolicy.fallbackPriority("audio/mp4a-latm") < AudioRendererFallbackPolicy.fallbackPriority("audio/vnd.dts"))
    }
}
