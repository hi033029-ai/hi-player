package com.example.player

import androidx.media3.common.Format
import org.junit.Assert.*
import org.junit.Test

class SelectiveColourBandTest {
    @Test fun neutralAndOffPresetsAreIdentityTransforms() {
        assertTrue(ColorPresets.NEUTRAL.isIdentityTransform())
        assertTrue(ColorPresets.HDR_DISABLED.isIdentityTransform())
        assertFalse(ColorPresets.NATURAL_HDR.isIdentityTransform())
    }

    @Test fun originalOrDisabledPresetKeepsNativeOutputAndActiveGradeUsesGles() {
        assertTrue(shouldUseNativeHdrOutput(ColorPresets.HDR_DISABLED, enhancementEnabled = false))
        assertTrue(shouldUseNativeHdrOutput(ColorPresets.HDR_DISABLED, enhancementEnabled = true))
        assertFalse(shouldUseNativeHdrOutput(ColorPresets.VIVID_HDR, enhancementEnabled = true))
        assertTrue(shouldUseNativeHdrOutput(ColorPresets.VIVID_HDR, enhancementEnabled = false))
    }

    @Test fun activeNonIdentityPresetAppliesRegardlessOfTrackMetadata() {
        assertTrue(shouldApplyColorPreset(ColorPresets.VIVID_HDR, enhancementEnabled = true))
        assertTrue(shouldApplyColorPreset(ColorPresets.CINEMATIC, enhancementEnabled = true))
        assertFalse(shouldApplyColorPreset(ColorPresets.VIVID_HDR, enhancementEnabled = false))
        assertFalse(shouldApplyColorPreset(ColorPresets.HDR_DISABLED, enhancementEnabled = true))
        assertFalse(shouldApplyColorPreset(ColorPresets.NEUTRAL, enhancementEnabled = true))
    }

    @Test fun requestedOriginalVividCinemaAndBrightPresetsAreAvailableFirst() {
        assertEquals(
            listOf("Original HDR", "HDR Vivid", "HDR Cinema", "HDR Bright"),
            ColorPresets.ALL.take(4).map { it.name },
        )
        assertFalse(ColorPresets.HDR_BRIGHT.isIdentityTransform())
    }

    @Test fun builtInGradesKeepContrastNearNeutralToPreserveHdrShadows() {
        assertTrue(ColorPresets.ALL.filterNot { it.isIdentityTransform() }.all { it.contrast <= 1.05f })
    }

    @Test fun togglingOffReturnsOriginalAndTogglingOnRestoresCustomGrade() {
        val custom = ColorPresets.CUSTOM.copy(saturation = 1.1f)
        assertEquals(ColorPresets.HDR_DISABLED, toggledPicturePreset(true, custom))
        assertEquals(custom, toggledPicturePreset(false, custom))
    }

    @Test fun dolbyVisionIsRecognizedFromMimeOrCodecWhenTransferMetadataIsMissing() {
        val mimeFormat = Format.Builder().setSampleMimeType("video/dolby-vision").build()
        val codecFormat = Format.Builder().setSampleMimeType("video/hevc").setCodecs("dvhe.05.06").build()
        val unknownFormat = Format.Builder().setSampleMimeType("video/avc").build()

        assertTrue(isHdrVideoFormat(mimeFormat))
        assertTrue(isDolbyVisionFormat(codecFormat))
        assertTrue(hasKnownVideoColorMetadata(codecFormat))
        assertFalse(hasKnownVideoColorMetadata(unknownFormat))
    }

    @Test fun neutralMidpointAddsNoColourAdjustment() {
        assertNull(selectiveExposureAdjustment(0f, 0.5f))
    }
    @Test fun lightAndDarkPreserveHueInsteadOfAddingARgbTint() {
        val light = selectiveExposureAdjustment(0f, 0f)!!
        val dark = selectiveExposureAdjustment(0f, 1f)!!
        assertEquals(1.2f, light.red, 0f)
        assertEquals(light.red, light.green, 0f)
        assertEquals(light.green, light.blue, 0f)
        assertEquals(0.8f, dark.red, 0.000001f)
    }
    @Test fun aRedTargetDoesNotAlterGreenBlueOrNeutralPixels() {
        assertEquals(0f, SelectiveColourBand.weight(1f / 3f, 0f, 1f, 1f), 0f)
        assertEquals(0f, SelectiveColourBand.weight(2f / 3f, 0f, 1f, 1f), 0f)
        assertEquals(0f, SelectiveColourBand.weight(0f, 0f, 0f, 0.5f), 0f)
        assertEquals(0f, SelectiveColourBand.weight(0f, 0f, 1f, 0.01f), 0f)
    }
    @Test fun redBandWrapsAcrossTheHueBoundary() {
        assertEquals(1f, SelectiveColourBand.weight(0.99f, 0.01f, 1f, 1f), 0.001f)
    }
    @Test fun distantSimilarHuesAreNotWashedAcrossTheFrame() {
        assertEquals(0f, SelectiveColourBand.weight(0.1f, 0f, 1f, 1f), 0f)
    }
    @Test fun malformedSliderValuesAreIgnoredAndFiniteValuesClamped() {
        assertNull(selectiveExposureAdjustment(Float.NaN, 0f))
        assertNull(selectiveExposureAdjustment(0f, Float.POSITIVE_INFINITY))
        assertEquals(0.8f, selectiveExposureAdjustment(0f, 5f)!!.red, 0.000001f)
    }
}
