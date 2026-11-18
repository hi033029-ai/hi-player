package com.example.data

import com.example.player.ColorPreset
import com.example.player.ColorPresets
import com.example.player.ColorTargetAdjustment
import org.json.JSONArray
import org.json.JSONObject

internal fun encodeCustomPicture(p: ColorPreset): String = JSONObject().apply {
    put("brightness", p.brightness.toDouble())
    put("contrast", p.contrast.toDouble())
    put("saturation", p.saturation.toDouble())
    put("vibrance", p.vibrance.toDouble())
    put("sharpness", p.sharpness.toDouble())
    put("red", p.red.toDouble())
    put("green", p.green.toDouble())
    put("blue", p.blue.toDouble())
    put("targetHue", p.targetHue.toDouble())
    put("bands", JSONArray().apply {
        p.targetAdjustments.forEach { b -> put(JSONArray(listOf(b.hue, b.red, b.green, b.blue))) }
    })
}.toString()

internal fun decodeCustomPicture(raw: String?): ColorPreset {
    if (raw == null) return ColorPresets.CUSTOM
    return runCatching {
        val json = JSONObject(raw)
        fun number(key: String, default: Float, min: Float = 0f, max: Float = 2f): Float =
            json.optDouble(key, default.toDouble()).toFloat().takeIf { it.isFinite() }?.coerceIn(min, max) ?: default
        val bands = json.optJSONArray("bands") ?: JSONArray()
        ColorPresets.CUSTOM.copy(
            brightness = number("brightness", 1f), contrast = number("contrast", 1f),
            saturation = number("saturation", 1f), vibrance = number("vibrance", 1f),
            sharpness = number("sharpness", 0f), red = number("red", 1f),
            green = number("green", 1f), blue = number("blue", 1f), targetHue = number("targetHue", -1f, -1f, 1f),
            targetAdjustments = (0 until minOf(8, bands.length())).map { i ->
                val band = bands.getJSONArray(i)
                fun component(index: Int, max: Float) = band.getDouble(index).toFloat().also { require(it in 0f..max) }
                ColorTargetAdjustment(component(0, 1f), component(1, 2f), component(2, 2f), component(3, 2f))
            },
        )
    }.getOrDefault(ColorPresets.CUSTOM)
}
