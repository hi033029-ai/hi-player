package com.example.player

import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.RgbAdjustment

/**
 * Compensates for devices that tone-map HDR (BT.2020/PQ) content down to SDR at the
 * hardware level even after window.colorMode is correctly set to HDR — on those
 * devices the picture still looks flat/desaturated because the panel never actually
 * receives HDR signal, just a dim SDR conversion of it. This applies a post-process
 * grading pass matching what you asked for: exposure +20%, saturation +20%, contrast
 * left untouched (contrast is deliberately NOT included below — omitting it is what
 * "no contrast change" means, since the default is a no-op).
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
            .setRedScale(1.2f)
            .setGreenScale(1.2f)
            .setBlueScale(1.2f)
            .build()

        val saturationBoost = HslAdjustment.Builder()
            .adjustSaturation(20f) // +20%
            .build()

        return listOf(exposureBoost, saturationBoost)
    }
}
