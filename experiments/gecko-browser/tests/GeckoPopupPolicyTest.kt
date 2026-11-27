package com.example.browser.gecko

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeckoPopupPolicyTest {
    @Test fun allowsUserGestureButBlocksUnsolicitedWindows() {
        assertFalse(GeckoPopupPolicy.allows(blockPopups = true, hasUserGesture = false, destinationBlockedByProtection = false))
        assertTrue(GeckoPopupPolicy.allows(blockPopups = true, hasUserGesture = true, destinationBlockedByProtection = false))
        assertTrue(GeckoPopupPolicy.allows(blockPopups = false, hasUserGesture = false, destinationBlockedByProtection = false))
    }
    @Test fun uncertainAndFlaggedDestinationsRequireReviewInsteadOfSilentDenial() {
        assertFalse(GeckoPopupPolicy.requiresReview(true, true, false))
        assertTrue(GeckoPopupPolicy.requiresReview(true, false, false))
        assertTrue(GeckoPopupPolicy.requiresReview(true, true, true))
        assertTrue(GeckoPopupPolicy.requiresReview(false, true, true))
        assertFalse(GeckoPopupPolicy.requiresReview(false, false, false))
    }
    @Test fun promptHonorsSettingAndRecentDownloadIntent() {
        assertFalse(GeckoPopupPolicy.allowsPrompt(true, false) { false })
        assertTrue(GeckoPopupPolicy.allowsPrompt(true, false) { true })
        assertTrue(GeckoPopupPolicy.allowsPrompt(false, false) { error("Must not consume intent") })
        assertFalse(GeckoPopupPolicy.allowsPrompt(true, true) { error("Ads must not consume intent") })
        assertFalse(GeckoPopupPolicy.allowsPrompt(false, true) { error("Ads remain blocked") })
    }
    @Test fun promptConsumesOneDownloadAuthorization() {
        var pending = true
        val consume = { val result = pending; pending = false; result }
        assertTrue(GeckoPopupPolicy.allowsPrompt(true, false, consume))
        assertFalse(GeckoPopupPolicy.allowsPrompt(true, false, consume))
    }
    @Test fun blocksAdDestinationsEvenWhenOpenedFromATap() {
        assertFalse(GeckoPopupPolicy.allows(blockPopups = true, hasUserGesture = true, destinationBlockedByProtection = true))
        assertFalse(GeckoPopupPolicy.allows(blockPopups = false, hasUserGesture = true, destinationBlockedByProtection = true))
    }
}
