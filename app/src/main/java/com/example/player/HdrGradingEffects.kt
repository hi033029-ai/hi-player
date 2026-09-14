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
 * Current grading: exposure boosted further (+40%), saturation pulled down (-15%)
 * to compensate for the extra exposure reading as oversaturated, sharpness eased
 * off slightly via a custom blur blend (see SharpnessReductionEffect.kt — no
 * built-in Media3 Builder exists for this one). Contrast is still deliberately
 * left out — omitting it is what "no contrast change" means.
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
            .setRedScale(1.4f)
            .setGreenScale(1.4f)
            .setBlueScale(1.4f)
            .build()

        val saturationCut = HslAdjustment.Builder()
            .adjustSaturation(-15f) // was +20%, now pulled down to -15%
            .build()

        // The attached SharpnessReductionEffect contains a version-dependent
        // shader TODO, so it is kept in the repository but not installed in
        // the live HDR pipeline until its GlProgram binding is implemented.
        return listOf(exposureBoost, saturationCut)
    }
}
