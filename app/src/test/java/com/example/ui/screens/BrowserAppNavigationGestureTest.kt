package com.example.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserAppNavigationGestureTest {
    private fun reveal(
        downX: Float = 180f,
        downY: Float = 980f,
        upX: Float = 188f,
        upY: Float = 850f,
    ): Boolean = BrowserAppNavigationGesture.isRevealSwipe(
        viewHeightPx = 1_000,
        downX = downX,
        downY = downY,
        upX = upX,
        upY = upY,
        bottomEdgePx = 96f,
        minimumUpwardDistancePx = 72f,
    )

    @Test
    fun bottomEdgeUpwardSwipeRevealsNavigation() {
        assertTrue(reveal())
    }

    @Test
    fun swipeStartingAwayFromBottomEdgeDoesNotRevealNavigation() {
        assertFalse(reveal(downY = 880f, upY = 720f))
    }

    @Test
    fun shortOrMostlyHorizontalGestureDoesNotRevealNavigation() {
        assertFalse(reveal(upY = 930f))
        assertFalse(reveal(upX = 420f, upY = 850f))
    }

    @Test
    fun downwardSwipeDoesNotRevealNavigation() {
        assertFalse(reveal(upY = 995f))
    }
}
