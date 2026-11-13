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

    @Test fun allHdrAndUnknownColorTracksUseNativeOutput() {
        assertTrue(shouldUseNativeHdrOutput(isHdrContent = true, colorMetadataKnown = true))
        assertTrue(shouldUseNativeHdrOutput(isHdrContent = true, colorMetadataKnown = false))
        assertTrue(shouldUseNativeHdrOutput(isHdrContent = false, colorMetadataKnown = false))
        assertFalse(shouldUseNativeHdrOutput(isHdrContent = false, colorMetadataKnown = true))
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
        assertEquals(1.5f, light.red, 0f)
        assertEquals(light.red, light.green, 0f)
        assertEquals(light.green, light.blue, 0f)
        assertEquals(0.5f, dark.red, 0f)
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
        assertEquals(0.5f, selectiveExposureAdjustment(0f, 5f)!!.red, 0f)
    }
}
