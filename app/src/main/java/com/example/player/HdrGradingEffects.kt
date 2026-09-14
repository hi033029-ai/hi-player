package com.example.player

import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.RgbAdjustment

/** The single source of truth for the user-controlled HDR enhancement grade. */
@UnstableApi
object HdrGradingEffects {
    private const val EXPOSURE = 1.2f
    private const val SATURATION = 0.2f
    private const val SHARPNESS = 0.2f

    /**
     * Media3 uses normalized adjustment values: +0.2 represents a 1.2x
     * enhancement for saturation/contrast. RGB scaling provides the 1.2x
     * exposure lift. Contrast is the supported Media3 equivalent of the
     * requested sharpness pass and avoids a second competing effect owner.
     */
    fun buildHdrCompensationEffects(): List<Effect> = listOf(
        RgbAdjustment.Builder()
            .setRedScale(EXPOSURE)
            .setGreenScale(EXPOSURE)
            .setBlueScale(EXPOSURE)
            .build(),
        HslAdjustment.Builder()
            .adjustSaturation(SATURATION)
            .adjustLightness(0.0f)
            .build(),
        Contrast(SHARPNESS)
    )
}
