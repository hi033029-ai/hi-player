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

internal fun ColorPreset.isIdentityTransform(): Boolean =
    brightness == 1f && contrast == 1f && saturation == 1f &&
        red == 1f && green == 1f && blue == 1f && vibrance == 1f &&
        sharpness == 0f && targetHue < 0f && targetAdjustments.isEmpty()

/** A non-identity preset is an explicit request to grade the decoded frame in GLES. */
internal fun shouldApplyColorPreset(
    preset: ColorPreset,
    enhancementEnabled: Boolean,
): Boolean = enhancementEnabled && !preset.isIdentityTransform()

/** Original/neutral output stays on the native path; an active grade opts into GLES. */
internal fun shouldUseNativeHdrOutput(
    preset: ColorPreset,
    enhancementEnabled: Boolean,
): Boolean = !shouldApplyColorPreset(preset, enhancementEnabled)

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

    val ALL = listOf(HDR_DISABLED, VIVID_HDR, CINEMATIC, HDR_BRIGHT, NATURAL_HDR, WARM, COOL, EYE_COMFORT)
}

internal fun toggledPicturePreset(active: Boolean, lastEnhanced: ColorPreset): ColorPreset =
    if (active) ColorPresets.HDR_DISABLED else lastEnhanced

/** Midpoint is unchanged; a modest gain only affects pixels in this hue band. */
fun selectiveExposureAdjustment(hue: Float, intensity: Float): ColorTargetAdjustment? {
    if (!hue.isFinite() || !intensity.isFinite()) return null
    val gain = 1.2f - 0.4f * intensity.coerceIn(0f, 1f)
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
