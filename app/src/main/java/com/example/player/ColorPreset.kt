package com.example.player

data class ColorPreset(
    val name: String,
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val red: Float,
    val green: Float,
    val blue: Float,
    val vibrance: Float,
)

object ColorPresets {
    val NEUTRAL = ColorPreset("Neutral", 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f)
    val HDR_DISABLED = ColorPreset("HDR Disabled (Normal)", 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f)
    val NATURAL_HDR = ColorPreset("Natural HDR", 1.02f, 1.08f, 1.06f, 1.02f, 1.01f, 1.03f, 1.12f)
    val VIVID_HDR = ColorPreset("Vivid HDR", 1.02f, 1.12f, 1.14f, 1.03f, 1.05f, 1.08f, 1.20f)
    val CINEMATIC = ColorPreset("Cinematic", 1.00f, 1.15f, 1.04f, 1.03f, 1.00f, 1.04f, 1.08f)
    val WARM = ColorPreset("Warm", 1.02f, 1.07f, 1.07f, 1.06f, 1.02f, 0.97f, 1.10f)
    val COOL = ColorPreset("Cool", 1.02f, 1.07f, 1.06f, 0.98f, 1.02f, 1.06f, 1.10f)
    val EYE_COMFORT = ColorPreset("Eye Comfort", 1.00f, 1.04f, 1.02f, 1.03f, 1.02f, 0.96f, 1.03f)

    val ALL = listOf(HDR_DISABLED, NATURAL_HDR, VIVID_HDR, CINEMATIC, WARM, COOL, EYE_COMFORT)
}
