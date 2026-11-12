package com.example.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureIndicatorColorsTest {
    @Test fun volumeUsesCyanAtNormalOrangeAt150AndRedAt200() {
        assertEquals(GestureIndicatorColors.cyan, GestureIndicatorColors.volume(0f))
        assertEquals(GestureIndicatorColors.cyan, GestureIndicatorColors.volume(1f))
        assertEquals(GestureIndicatorColors.orange, GestureIndicatorColors.volume(1.5f))
        assertEquals(GestureIndicatorColors.red, GestureIndicatorColors.volume(2f))
    }

    @Test fun boostColorsBlendContinuouslyAndClampOutsideRange() {
        val betweenCyanAndOrange = GestureIndicatorColors.volume(1.25f)
        val betweenOrangeAndRed = GestureIndicatorColors.volume(1.75f)
        assertTrue(betweenCyanAndOrange != GestureIndicatorColors.cyan && betweenCyanAndOrange != GestureIndicatorColors.orange)
        assertTrue(betweenOrangeAndRed != GestureIndicatorColors.orange && betweenOrangeAndRed != GestureIndicatorColors.red)
        assertEquals(GestureIndicatorColors.cyan, GestureIndicatorColors.volume(-1f))
        assertEquals(GestureIndicatorColors.red, GestureIndicatorColors.volume(3f))
        assertEquals(GestureIndicatorColors.cyan, GestureIndicatorColors.volume(Float.NaN))
        assertEquals(GestureIndicatorColors.orange, GestureIndicatorColors.volume(1.4999f))
        assertEquals(GestureIndicatorColors.orange, GestureIndicatorColors.volume(1.5001f))
    }
}
