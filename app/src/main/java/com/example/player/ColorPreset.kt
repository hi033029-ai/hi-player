package com.example.player

data class ColorTargetAdjustment(
    val hue: Float,
    val red: Float,
    val green: Float,
    val blue: Float,
)

data class ColorPreset(
    val name: String,
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val red: Float,
    val green: Float,
    val blue: Float,
    val vibrance: Float,
    val sharpness: Float = 0.0f,
    val targetHue: Float = -1.0f,
    val targetAdjustments: List<ColorTargetAdjustment> = emptyList(),
)

object ColorPresets {
    val NEUTRAL = ColorPreset("Neutral", 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f)
    val HDR_DISABLED = ColorPreset("Off", 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f)
    val NATURAL_HDR = ColorPreset("Natural HDR", 1.02f, 1.08f, 1.06f, 1.02f, 1.01f, 1.03f, 1.12f)
    val VIVID_HDR = ColorPreset("Vivid HDR", 1.02f, 1.12f, 1.14f, 1.03f, 1.05f, 1.08f, 1.20f)
    val CINEMATIC = ColorPreset("Cinematic", 1.00f, 1.15f, 1.04f, 1.03f, 1.00f, 1.04f, 1.08f)
    val WARM = ColorPreset("Warm", 1.02f, 1.07f, 1.07f, 1.06f, 1.02f, 0.97f, 1.10f)
    val COOL = ColorPreset("Cool", 1.02f, 1.07f, 1.06f, 0.98f, 1.02f, 1.06f, 1.10f)
    val EYE_COMFORT = ColorPreset("Eye Comfort", 1.00f, 1.04f, 1.02f, 1.03f, 1.02f, 0.96f, 1.03f)
    val CUSTOM = ColorPreset("Custom", 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 0.0f)

    val ALL = listOf(HDR_DISABLED, NATURAL_HDR, VIVID_HDR, CINEMATIC, WARM, COOL, EYE_COMFORT)
}

/** Midpoint is unchanged; only pixels in this hue band get lighter/darker, never a colour wash. */
fun selectiveExposureAdjustment(hue: Float, intensity: Float): ColorTargetAdjustment? {
    if (!hue.isFinite() || !intensity.isFinite()) return null
    val gain = 1.5f - intensity.coerceIn(0f, 1f)
    if (kotlin.math.abs(gain - 1f) < 0.001f) return null
    return ColorTargetAdjustment(((hue % 1f) + 1f) % 1f, gain, gain, gain)
}

/** Shared hue-band thresholds and CPU reference for pixel-mask regression fixtures. */
object SelectiveColourBand {
    const val HUE_FULL = 0.025f
    const val HUE_ZERO = 0.075f
    const val SATURATION_ZERO = 0.12f
    const val SATURATION_FULL = 0.30f
    const val VALUE_ZERO = 0.02f
    const val VALUE_FULL = 0.08f

    fun weight(hue: Float, target: Float, saturation: Float, value: Float): Float {
        val raw = kotlin.math.abs(hue - target) % 1f
        val distance = minOf(raw, 1f - raw)
        return (1f - smoothstep(HUE_FULL, HUE_ZERO, distance)) *
            smoothstep(SATURATION_ZERO, SATURATION_FULL, saturation) *
            smoothstep(VALUE_ZERO, VALUE_FULL, value)
    }

    private fun smoothstep(low: Float, high: Float, value: Float): Float {
        val t = ((value - low) / (high - low)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
