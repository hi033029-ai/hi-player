package com.example.browser.gecko

import org.junit.Assert.*
import org.junit.Test

class GeckoPopupGateTest {
    @Test fun ticketMatchesOnceAndExpires() {
        var now = 100L
        val gate = GeckoPopupGate { now }
        assertFalse(gate.consume("https://example.org"))
        gate.permit("https://example.org")
        assertFalse(gate.consume("https://other.example"))
        assertFalse(gate.consume("https://example.org"))
        gate.permit("https://example.org")
        assertTrue(gate.consume("https://example.org"))
        assertFalse(gate.consume("https://example.org"))
        gate.permit("about:blank")
        now += 5_001
        assertFalse(gate.consume("about:blank"))
    }
    @Test fun clearAndClockRollbackDeny() {
        var now = 100L
        val gate = GeckoPopupGate { now }
        gate.permit("about:blank")
        gate.clear()
        assertFalse(gate.consume("about:blank"))
        gate.permit("about:blank")
        now--
        assertFalse(gate.consume("about:blank"))
    }
}
