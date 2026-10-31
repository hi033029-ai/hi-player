package com.example.ui.components

import org.junit.Assert.*
import org.junit.Test

class BrowserProgressStateTest {
    @Test fun redirectStartDoesNotResetProgress() {
        val state = BrowserProgressState()
        state.start(); state.onProgress(68)
        val generation = state.generation
        state.start(); state.onProgress(20)
        assertEquals(.68f, state.value, .001f)
        assertEquals(generation, state.generation)
    }
    @Test fun progressWithoutPageStartedStillStarts() {
        val state = BrowserProgressState()
        state.onProgress(35)
        assertTrue(state.loading)
        assertTrue(state.visible)
        assertEquals(.35f, state.value, .001f)
    }
    @Test fun completionIsIdempotentAndNewNavigationCancelsOldFade() {
        val state = BrowserProgressState()
        state.onProgress(45); state.onProgress(100)
        val completed = state.generation
        state.finish()
        assertFalse(state.loading)
        assertEquals(1f, state.value, .001f)
        state.restart(); state.hide(completed)
        assertTrue(state.visible)
        assertTrue(state.loading)
        assertEquals(0f, state.value, .001f)
    }
    @Test fun initialCompleteCallbackDoesNotFlashBarAndCancelDoesNotMeanSuccess() {
        val state = BrowserProgressState()
        state.onProgress(100)
        assertFalse(state.visible)
        state.start(); state.onProgress(40); state.cancel()
        assertFalse(state.loading)
        assertFalse(state.visible)
        assertEquals(0f, state.value, .001f)
        state.onProgress(70) // Late callback from the stopped/error page.
        assertFalse(state.visible)
        state.start(); state.onProgress(20)
        assertTrue(state.loading)
        assertEquals(.2f, state.value, .001f)
    }
}
