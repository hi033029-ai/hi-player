package com.example.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayAwareHdrToneMappingTest {
    @Test
    fun requestsToneMappingOnlyForHdrOnUnsupportedDisplayApi31OrNewer() {
        assertTrue(
            shouldRequestMediaCodecHdrToneMapping(
                apiLevel = 31,
                displaySupportsHdr = false,
                isHdrContent = true,
            ),
        )
        assertTrue(
            shouldRequestMediaCodecHdrToneMapping(
                apiLevel = 35,
                displaySupportsHdr = false,
                isHdrContent = true,
            ),
        )
        assertFalse(
            shouldRequestMediaCodecHdrToneMapping(
                apiLevel = 30,
                displaySupportsHdr = false,
                isHdrContent = true,
            ),
        )
        assertFalse(
            shouldRequestMediaCodecHdrToneMapping(
                apiLevel = 35,
                displaySupportsHdr = true,
                isHdrContent = true,
            ),
        )
        assertFalse(
            shouldRequestMediaCodecHdrToneMapping(
                apiLevel = 35,
                displaySupportsHdr = false,
                isHdrContent = false,
            ),
        )
    }
}
