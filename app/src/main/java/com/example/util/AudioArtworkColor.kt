package com.example.util

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.HiThemePalette
import kotlin.math.abs
import kotlin.math.floor

/** A dark, low-glare palette derived from a small cached album-art bitmap. */
internal object AudioArtworkColor {
    private const val HUE_BUCKETS = 24
    private const val MAX_SAMPLES_PER_SIDE = 48

    fun palette(base: HiThemePalette, bitmap: Bitmap): HiThemePalette? {
        val swatch = dominantSwatch(bitmap) ?: return null
        val hue = swatch[0]
        val saturation = swatch[1].coerceIn(0.35f, 0.82f)
        fun color(value: Float, sat: Float = saturation): Color = Color(
            AndroidColor.HSVToColor(floatArrayOf(hue, sat.coerceIn(0f, 1f), value.coerceIn(0f, 1f)))
        )
        val background = color(0.105f, saturation * 0.72f)
        val surface = color(0.145f, saturation * 0.70f)
        val elevated = color(0.195f, saturation * 0.66f)
        val border = color(0.31f, saturation * 0.58f)
        val accent = color(0.92f, maxOf(0.52f, saturation))
        val secondary = Color(AndroidColor.HSVToColor(
            floatArrayOf((hue + 24f) % 360f, maxOf(0.40f, saturation * 0.82f), 0.82f)
        ))
        return base.copy(
            background = background,
            backgroundGradient = listOf(background, surface),
            surface = surface,
            surfaceElevated = elevated,
            surfaceBorder = border,
            primary = accent,
            secondary = secondary,
            textPrimary = Color(0xFFF7F8FC),
            textSecondary = Color(0xFFC7CCD8),
            textTertiary = Color(0xFF9299A9),
            isDark = true,
        )
    }

    /** Returns hue, saturation, and value for the strongest chromatic hue family. */
    internal fun dominantSwatch(bitmap: Bitmap): FloatArray? {
        if (bitmap.width <= 0 || bitmap.height <= 0) return null
        val step = maxOf(1, maxOf(bitmap.width, bitmap.height) / MAX_SAMPLES_PER_SIDE)
        val hueWeight = FloatArray(HUE_BUCKETS)
        val hueSum = FloatArray(HUE_BUCKETS)
        val saturationSum = FloatArray(HUE_BUCKETS)
        val valueSum = FloatArray(HUE_BUCKETS)
        val hsv = FloatArray(3)
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                if (AndroidColor.alpha(pixel) >= 160) {
                    AndroidColor.colorToHSV(pixel, hsv)
                    val saturation = hsv[1]
                    val value = hsv[2]
                    if (saturation >= 0.18f && value in 0.10f..0.96f) {
                        val bucket = floor(hsv[0] / 360f * HUE_BUCKETS).toInt().coerceIn(0, HUE_BUCKETS - 1)
                        val weight = saturation * (1f - abs(value - 0.55f) * 0.55f)
                        hueWeight[bucket] += weight
                        hueSum[bucket] += hsv[0] * weight
                        saturationSum[bucket] += saturation * weight
                        valueSum[bucket] += value * weight
                    }
                }
                x += step
            }
            y += step
        }
        val best = hueWeight.indices.maxByOrNull { hueWeight[it] } ?: return null
        val weight = hueWeight[best]
        if (weight <= 0f) return null
        return floatArrayOf(
            hueSum[best] / weight,
            saturationSum[best] / weight,
            valueSum[best] / weight,
        )
    }
}
