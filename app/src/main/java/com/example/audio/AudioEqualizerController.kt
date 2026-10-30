package com.example.audio

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

data class AudioEqBand(val index: Int, val frequencyHz: Int, val gainDb: Float)
data class AudioEqState(
    val enabled: Boolean = false,
    val available: Boolean = false,
    val preset: String = "Flat",
    val bands: List<AudioEqBand> = emptyList(),
    val minDb: Float = -12f,
    val maxDb: Float = 12f,
    val bassSupported: Boolean = false,
    val bassStrength: Int = 0,
    val status: String = "Play a track to connect the equalizer",
)

/** Music-session effects only: never attach an effect to the global audio session (0). */
internal class AudioEqualizerController(context: Context) {
    private val prefs = context.getSharedPreferences("audio_equalizer_v1", Context.MODE_PRIVATE)
    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var session = 0
    private val savedPreset = prefs.getString("preset", "Flat").orEmpty().takeIf { it in EqualizerCurves.presets || it == "Custom" } ?: "Flat"
    private val _state = MutableStateFlow(AudioEqState(enabled = prefs.getBoolean("enabled", false), preset = savedPreset,
        bassStrength = prefs.getInt("bass", 0).coerceIn(0, 1000)))
    val state = _state.asStateFlow()
    private val _preset = MutableStateFlow(savedPreset)
    val preset = _preset.asStateFlow()

    fun attach(sessionId: Int) {
        if (sessionId <= 0) { release(); return }
        if (session == sessionId && runCatching { eq?.hasControl() == true }.getOrDefault(false)) return
        releaseEffects()
        session = sessionId
        try { eq = Equalizer(0, sessionId) } catch (_: Exception) { eq = null }
        try {
            bass = BassBoost(0, sessionId)
            if (bass?.strengthSupported != true) { bass?.release(); bass = null }
        } catch (_: Exception) { runCatching { bass?.release() }; bass = null }
        try {
            val effect = eq
            val range = effect?.bandLevelRange
            val min = range?.get(0)?.div(100f)?.coerceAtLeast(-12f) ?: -12f
            val max = range?.get(1)?.div(100f)?.coerceAtMost(12f) ?: 12f
            val bands = (0 until (effect?.numberOfBands?.toInt() ?: 0)).map { index ->
                val hz = effect!!.getCenterFreq(index.toShort()) / 1000
                val gain = if (_state.value.preset == "Custom") prefs.getFloat("band_$hz", 0f)
                    else EqualizerCurves.gain(_state.value.preset, hz)
                AudioEqBand(index, hz, gain.coerceIn(min, max))
            }
            _state.value = _state.value.copy(available = effect != null && bands.isNotEmpty(), bands = bands,
                minDb = min, maxDb = max, bassSupported = bass != null, status = "")
            apply()
        } catch (_: Exception) {
            releaseEffects()
            _state.value = _state.value.copy(available = false, bands = emptyList(), bassSupported = false,
                status = "Audio effects are not available on this device or output")
        }
    }

    fun setEnabled(value: Boolean) {
        _state.value = _state.value.copy(enabled = value)
        prefs.edit().putBoolean("enabled", value).apply()
        apply()
    }
    fun setPreset(value: String) {
        if (value !in EqualizerCurves.presets) return
        _preset.value = value
        _state.value = _state.value.copy(preset = value, bands = _state.value.bands.map {
            it.copy(gainDb = EqualizerCurves.gain(value, it.frequencyHz).coerceIn(_state.value.minDb, _state.value.maxDb))
        })
        prefs.edit().putString("preset", value).apply()
        apply()
    }
    fun setBand(index: Int, gain: Float) {
        if (!gain.isFinite()) return
        val s = _state.value
        val band = s.bands.firstOrNull { it.index == index } ?: return
        val clamped = gain.coerceIn(s.minDb, s.maxDb)
        _preset.value = "Custom"
        _state.value = s.copy(preset = "Custom", bands = s.bands.map { if (it.index == index) it.copy(gainDb = clamped) else it })
        // Save all displayed bands on first custom edit so changing one band preserves the rest.
        prefs.edit().putString("preset", "Custom").also { editor ->
            _state.value.bands.forEach { editor.putFloat("band_${it.frequencyHz}", it.gainDb) }
        }.apply()
        apply()
    }
    fun setBass(value: Int) {
        val strength = value.coerceIn(0, 1000)
        _state.value = _state.value.copy(bassStrength = strength)
        prefs.edit().putInt("bass", strength).apply()
        apply()
    }
    fun reset() { setBass(0); setPreset("Flat") }

    private fun apply() {
        val s = _state.value
        if (session <= 0) return
        var error = false
        try {
            eq?.let { effect ->
                if (!effect.hasControl()) throw IllegalStateException("Effect control unavailable")
                s.bands.forEach { effect.setBandLevel(it.index.toShort(), (it.gainDb * 100).roundToInt().toShort()) }
                effect.enabled = s.enabled
            }
        } catch (_: Exception) { error = true }
        try {
            bass?.let { effect ->
                effect.setStrength(s.bassStrength.toShort())
                effect.enabled = s.enabled && s.bassStrength > 0
            }
        } catch (_: Exception) { error = true }
        _state.value = s.copy(status = when {
            error -> "This output could not apply all audio effects"
            !s.available && !s.bassSupported -> "Audio effects are not supported on this output"
            !s.enabled -> "Equalizer is off — original audio"
            else -> "Applied to this music session"
        })
    }
    private fun releaseEffects() {
        runCatching { eq?.release() }; eq = null
        runCatching { bass?.release() }; bass = null
    }
    fun release() {
        releaseEffects(); session = 0
        _state.value = _state.value.copy(available = false, bands = emptyList(), bassSupported = false,
            status = "Play a track to connect the equalizer")
    }
}
