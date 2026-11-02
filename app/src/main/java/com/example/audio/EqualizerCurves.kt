package com.example.audio

import kotlin.math.ln

/** Presets interpolate onto the frequencies actually exposed by this device. */
object EqualizerCurves {
    val frequencies = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
    val presets = linkedMapOf(
        "Flat" to List(10) { 0f },
        "Bass Boost" to listOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f),
        "Vocal" to listOf(-3f, -2f, -1f, 0f, 2f, 3f, 3f, 2f, 0f, -1f),
        "Rock" to listOf(4f, 3f, 2f, 0f, -2f, -1f, 1f, 3f, 4f, 3f),
        "Electronic" to listOf(5f, 4f, 2f, 0f, -1f, 1f, 2f, 1f, 3f, 4f),
        "Acoustic" to listOf(2f, 1f, 0f, 1f, 2f, 2f, 1f, 2f, 2f, 1f),
    )
    fun gain(preset: String, frequencyHz: Int): Float {
        val values = presets[preset] ?: presets.getValue("Flat")
        if (frequencyHz <= frequencies.first()) return values.first()
        if (frequencyHz >= frequencies.last()) return values.last()
        val high = frequencies.indexOfFirst { it >= frequencyHz }
        val low = high - 1
        val fraction = (ln(frequencyHz.toDouble()) - ln(frequencies[low].toDouble())) /
            (ln(frequencies[high].toDouble()) - ln(frequencies[low].toDouble()))
        return values[low] + (values[high] - values[low]) * fraction.toFloat()
    }
}
