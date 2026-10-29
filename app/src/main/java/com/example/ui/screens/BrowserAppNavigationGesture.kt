package com.example.ui.screens

import kotlin.math.abs

/**
 * Identifies the deliberate upward swipe from the bottom of a browser page that
 * temporarily reveals Hi Player's primary navigation. The WebView still receives
 * every touch event because its listener always returns false.
 */
internal object BrowserAppNavigationGesture {
    fun isRevealSwipe(
        viewHeightPx: Int,
        downX: Float,
        downY: Float,
        upX: Float,
        upY: Float,
        bottomEdgePx: Float,
        minimumUpwardDistancePx: Float,
    ): Boolean {
        if (viewHeightPx <= 0 || bottomEdgePx <= 0f || minimumUpwardDistancePx <= 0f) return false
        if (downY < viewHeightPx - bottomEdgePx) return false

        val upwardDistance = downY - upY
        val horizontalDistance = abs(upX - downX)
        return upwardDistance >= minimumUpwardDistancePx && upwardDistance > horizontalDistance
    }
}
