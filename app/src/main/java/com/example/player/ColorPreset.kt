package com.example.player

data class ColorTargetAdjustment(
    val hue: Float,
    val red: Float,
    val green: Float,
    val blue: Float,
    val saturation: Float = 1.0f,
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

internal fun ColorPreset.isIdentityTransform(): Boolean =
    brightness == 1f &&
        contrast == 1f &&
        saturation == 1f &&
        red == 1f &&
        green == 1f &&
        blue == 1f &&
        vibrance == 1f &&
        sharpness == 0f &&
        targetHue < 0f &&
        targetAdjustments.isEmpty()

internal fun shouldApplyColorPreset(preset: ColorPreset, enhancementEnabled: Boolean): Boolean =
    enhancementEnabled && !preset.isIdentityTransform()

internal fun shouldUseNativeHdrOutput(preset: ColorPreset, enhancementEnabled: Boolean): Boolean =
    !shouldApplyColorPreset(preset, enhancementEnabled)

fun ColorPreset.valuesSummary(): String {
    if (isIdentityTransform()) return "Native 10-bit"
    return "Exp ${"%.2f".format(brightness)} · Con ${"%.2f".format(contrast)} · Sat ${"%.2f".format(saturation)} · Vib ${"%.2f".format(vibrance)}"
}

object ColorPresets {
    val NEUTRAL = ColorPreset("Neutral", 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f)
    val HDR_DISABLED = ColorPreset("Original HDR", 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f)
    val NATURAL_HDR = ColorPreset("Natural HDR", 1.02f, 1.03f, 1.06f, 1.02f, 1.01f, 1.03f, 1.12f)
    val VIVID_HDR = ColorPreset("HDR Vivid", 1.02f, 1.04f, 1.14f, 1.03f, 1.05f, 1.08f, 1.20f)
    val CINEMATIC = ColorPreset("HDR Cinema", 1.00f, 1.03f, 1.04f, 1.03f, 1.00f, 1.04f, 1.08f)
    val HDR_BRIGHT = ColorPreset("HDR Bright", 1.08f, 1.02f, 1.04f, 1.02f, 1.02f, 1.02f, 1.08f)
    val WARM = ColorPreset("Warm", 1.02f, 1.03f, 1.07f, 1.06f, 1.02f, 0.97f, 1.10f)
    val COOL = ColorPreset("Cool", 1.02f, 1.03f, 1.06f, 0.98f, 1.02f, 1.06f, 1.10f)
    val EYE_COMFORT = ColorPreset("Eye Comfort", 1.00f, 1.02f, 1.02f, 1.03f, 1.02f, 0.96f, 1.03f)
    val CUSTOM = ColorPreset("Custom", 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 0.0f)

    val ALL = listOf(
        HDR_DISABLED,
        VIVID_HDR,
        CINEMATIC,
        HDR_BRIGHT,
        NATURAL_HDR,
        WARM,
        COOL,
        EYE_COMFORT,
    )
}

internal fun toggledPicturePreset(active: Boolean, lastEnhanced: ColorPreset): ColorPreset =
    if (active) ColorPresets.HDR_DISABLED else lastEnhanced

/**
 * Maps a 0..1 selective slider (0 = Bright, 0.5 = Original, 1 = Dull) into a strictly neutral-gain
 * plus saturation target adjustment so only matching pixels brighten or dull without tinting the frame.
 */
fun selectiveExposureAdjustment(hue: Float, intensity: Float): ColorTargetAdjustment? {
    if (!hue.isFinite() || !intensity.isFinite()) return null
    val clamped = intensity.coerceIn(0f, 1f)
    val delta = 0.5f - clamped // +0.5 = Bright, 0 = Original, -0.5 = Dull
    if (kotlin.math.abs(delta) < 0.005f) return null
    val normalized = delta * 2f // -1f..1f
    val gain = 1.2f - 0.4f * clamped
    val sat = if (normalized >= 0f) 1f + 0.55f * normalized else 1f + 0.80f * normalized
    return ColorTargetAdjustment(((hue % 1f) + 1f) % 1f, gain, gain, gain, sat.coerceIn(0.15f, 1.8f))
}

fun selectiveIntensityFromAdjustment(adjustment: ColorTargetAdjustment?): Float {
    if (adjustment == null) return 0.5f
    return ((1.2f - adjustment.red) / 0.4f).coerceIn(0f, 1f)
}

object SelectiveColourBand {
    const val HUE_FULL = 0.025f
    const val HUE_ZERO = 0.075f
    const val SATURATION_ZERO = 0.14f
    const val SATURATION_FULL = 0.32f
    const val VALUE_ZERO = 0.03f
    const val VALUE_FULL = 0.10f

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
