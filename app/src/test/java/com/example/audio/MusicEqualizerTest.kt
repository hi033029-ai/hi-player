package com.example.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicEqualizerTest {
    @Test fun flatIsFlatAtEveryDeviceFrequency() {
        listOf(20f, 60f, 100f, 230f, 910f, 3600f, 14000f, 20000f).forEach {
            assertEquals(0f, MusicEqualizer.gainAt(it, List(5) { 0f }), 0.0001f)
        }
    }
    @Test fun customBandsInterpolateInLogFrequencyAndClampEdges() {
        val gains = listOf(-12f, -6f, 0f, 6f, 12f)
        assertEquals(-12f, MusicEqualizer.gainAt(20f, gains), 0.001f)
        assertEquals(12f, MusicEqualizer.gainAt(24000f, gains), 0.001f)
        assertEquals(-9f, MusicEqualizer.gainAt(kotlin.math.sqrt(60f * 230f), gains), 0.001f)
        MusicEqualizer.frequencies.forEachIndexed { index, hz -> assertEquals(gains[index], MusicEqualizer.gainAt(hz, gains), 0.001f) }
    }
    @Test fun presetsAreRealBoundedFiveBandGains() {
        MusicEqualizer.presets.values.forEach { gains ->
            assertEquals(5, gains.size)
            assertTrue(gains.all { it.isFinite() && it in -12f..12f })
        }
    }
}
