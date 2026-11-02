package com.example.audio

import org.junit.Assert.*
import org.junit.Test

class EqualizerCurvesTest {
    @Test fun flatHasNoArtificialBoost() {
        listOf(0, 31, 60, 125, 1000, 5000, 16000, 22050).forEach { assertEquals(0f, EqualizerCurves.gain("Flat", it), 0.001f) }
    }
    @Test fun everyCurveIsBoundedAndFitsAnyDeviceFrequency() {
        EqualizerCurves.presets.forEach { (name, points) ->
            assertEquals(EqualizerCurves.frequencies.size, points.size)
            (20..24000 step 17).forEach { hz -> assertTrue(EqualizerCurves.gain(name, hz) in -6f..6f) }
        }
    }
    @Test fun exactAnchorsAndLogInterpolationArePredictable() {
        assertEquals(5f, EqualizerCurves.gain("Bass Boost", 62), 0.001f)
        val midpoint = kotlin.math.sqrt((62 * 125).toDouble()).toInt()
        assertEquals(4.5f, EqualizerCurves.gain("Bass Boost", midpoint), 0.03f)
        assertEquals(0f, EqualizerCurves.gain("not a preset", 1000), 0.001f)
    }
}
