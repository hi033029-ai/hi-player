package com.example.player

/**
 * Policy for the player cover used while a new video surface is negotiating.
 * Growing downloads need a READY-gated fallback in case Media3 misses the
 * rendered-first-frame callback during a surface handoff.
 */
internal object FirstFrameRevealPolicy {
    const val GROWING_READY_GRACE_MS = 2_500L

    fun canFallbackReveal(isGrowingDownload: Boolean, playerReady: Boolean): Boolean =
        !isGrowingDownload || playerReady
}
