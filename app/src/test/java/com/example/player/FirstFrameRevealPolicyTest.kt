package com.example.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstFrameRevealPolicyTest {
    @Test
    fun `growing download stays covered until player is ready`() {
        assertFalse(FirstFrameRevealPolicy.canFallbackReveal(isGrowingDownload = true, playerReady = false))
    }

    @Test
    fun `ready growing download can use bounded fallback if first frame callback is missed`() {
        assertTrue(FirstFrameRevealPolicy.canFallbackReveal(isGrowingDownload = true, playerReady = true))
    }

    @Test
    fun `completed media retains existing fallback behavior`() {
        assertTrue(FirstFrameRevealPolicy.canFallbackReveal(isGrowingDownload = false, playerReady = false))
    }
}
