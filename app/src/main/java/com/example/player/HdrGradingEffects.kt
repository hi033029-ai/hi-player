package com.example.player

import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.RgbAdjustment

@UnstableApi
object HdrGradingEffects {
    fun buildHdrCompensationEffects(): List<Effect> {
        val exposure = RgbAdjustment.Builder()
            .setRedScale(1.2f)
            .setGreenScale(1.2f)
            .setBlueScale(1.2f)
            .build()
        val saturation = HslAdjustment.Builder()
            .adjustSaturation(0.20f)
            .adjustLightness(0.0f)
            .build()
        return listOf(exposure, saturation)
    }
}
