package com.example.browser.gecko

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeckoPopupPolicyTest {
    @Test fun blocksScriptWindowsEvenWhenTheyBorrowATap() {
        assertFalse(GeckoPopupPolicy.allows(blockPopups = true, hasUserGesture = false, destinationBlockedByProtection = false))
        assertFalse(GeckoPopupPolicy.allows(blockPopups = true, hasUserGesture = true, destinationBlockedByProtection = false))
        assertTrue(GeckoPopupPolicy.allows(blockPopups = false, hasUserGesture = false, destinationBlockedByProtection = false))
    }
    @Test fun blocksAdDestinationsEvenWhenOpenedFromATap() {
        assertFalse(GeckoPopupPolicy.allows(blockPopups = true, hasUserGesture = true, destinationBlockedByProtection = true))
        assertFalse(GeckoPopupPolicy.allows(blockPopups = false, hasUserGesture = true, destinationBlockedByProtection = true))
    }
}
