package com.example.player

import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.RgbAdjustment

/**
 * Compensates for devices that tone-map HDR (BT.2020/PQ) content down to SDR at the
 * hardware level even after window.colorMode is correctly set to HDR — on those
 * devices the picture still looks flat/desaturated because the panel never actually
 * receives HDR signal, just a dim SDR conversion of it.
 *
 * Current grading: exposure is scaled to 1.5x and saturation to 1.1x.
 * Sharpness is neutral (no sharpness effect is installed). Contrast is deliberately
 * left untouched.
 *
 * NOTE: media3-effect's Builder method names have shifted a little across 1.x
 * releases — if `adjustSaturation` doesn't match your media3-effect version, check
 * its current HslAdjustment.Builder signature; the shape (RgbAdjustment for
 * exposure, HslAdjustment for saturation) is stable, only exact method names move.
 */
@UnstableApi
object HdrGradingEffects {

    fun buildHdrCompensationEffects(): List<Effect> {
        val exposureBoost = RgbAdjustment.Builder()
            .setRedScale(1.5f)
            .setGreenScale(1.5f)
            .setBlueScale(1.5f)
            .build()

        val saturationBoost = HslAdjustment.Builder()
            .adjustSaturation(10f) // 1.1x saturation
            .build()

        // Sharpness is neutral: do not install SharpnessReductionEffect.
        return listOf(exposureBoost, saturationBoost)
    }
}
