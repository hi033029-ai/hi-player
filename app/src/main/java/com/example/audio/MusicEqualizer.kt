package com.example.audio

import android.media.audiofx.Equalizer
import kotlin.math.ln

/** Session-scoped Android DSP. No promises of surround sound, loudness doubling or HDR audio. */
class MusicEqualizer {
    private var effect: Equalizer? = null
    private var session = 0
    companion object {
        val presets = linkedMapOf(
            "Flat" to listOf(0f, 0f, 0f, 0f, 0f),
            "Bass" to listOf(5f, 3f, 0f, -1f, 0f),
            "Voice" to listOf(-2f, 0f, 3f, 3f, 0f),
            "Rock" to listOf(4f, 1f, -1f, 2f, 4f),
            "Electronic" to listOf(4f, 2f, 0f, 2f, 3f),
        )
        val frequencies = listOf(60f, 230f, 910f, 3600f, 14000f)
        fun gainAt(hz: Float, gains: List<Float>): Float {
            if (hz <= frequencies.first()) return gains.first()
            if (hz >= frequencies.last()) return gains.last()
            val index = (0..3).first { hz <= frequencies[it + 1] }
            val fraction = ln(hz / frequencies[index]) / ln(frequencies[index + 1] / frequencies[index])
            return gains[index] + (gains[index + 1] - gains[index]) * fraction
        }
    }
    fun apply(audioSession: Int, bands: List<Float>): Boolean {
        if (audioSession <= 0 || bands.size != 5 || bands.any { !it.isFinite() }) { close(); return false }
        return try {
            if (session != audioSession || effect == null) {
                close()
                effect = Equalizer(0, audioSession)
                session = audioSession
            }
            val eq = requireNotNull(effect)
            if (!eq.hasControl() || eq.numberOfBands <= 0) { close(); return false }
            val range = eq.bandLevelRange
            for (index in 0 until eq.numberOfBands.toInt()) {
                val band = index.toShort()
                val hz = eq.getCenterFreq(band) / 1000f
                eq.setBandLevel(band, (gainAt(hz, bands) * 100).toInt().coerceIn(range[0].toInt(), range[1].toInt()).toShort())
            }
            eq.enabled = bands.any { it != 0f }
            true
        } catch (_: RuntimeException) { close(); false }
    }
    fun close() {
        runCatching { effect?.release() }
        effect = null
        session = 0
    }
}
