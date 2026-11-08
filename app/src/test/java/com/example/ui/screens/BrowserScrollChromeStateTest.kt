package com.example.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserScrollChromeStateTest {
    @Test fun deliberateScrollDownHidesAndScrollUpRestores() {
        val state = BrowserScrollChromeState()
        state.onScroll(20f)
        assertFalse(state.hidden)
        state.onScroll(25f)
        assertTrue(state.hidden)
        state.onScroll(-15f)
        assertTrue(state.hidden)
        state.onScroll(-20f)
        assertFalse(state.hidden)
    }

    @Test fun tinyJitterDoesNotHideControls() {
        val state = BrowserScrollChromeState()
        repeat(10) { state.onScroll(8f); state.onScroll(-6f) }
        assertFalse(state.hidden)
    }

    @Test fun topAndNavigationAlwaysRestoreControls() {
        val state = BrowserScrollChromeState()
        state.onScroll(100f)
        assertTrue(state.hidden)
        state.onScroll(1f, atTop = true)
        assertFalse(state.hidden)
        state.onScroll(100f)
        state.show()
        assertFalse(state.hidden)
    }

    @Test fun newGestureStartsWithFreshTravel() {
        val state = BrowserScrollChromeState()
        state.onScroll(35f)
        state.endGesture()
        state.onScroll(10f)
        assertFalse(state.hidden)
    }
}
