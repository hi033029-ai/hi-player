package com.example.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Directional scroll state shared by WebView callbacks and Gecko touch observation. */
class BrowserScrollChromeState {
    var hidden by mutableStateOf(false)
        private set
    private var travel = 0f

    /** Positive delta scrolls page content down; negative delta scrolls it back up. */
    fun onScroll(delta: Float, atTop: Boolean = false) {
        if (atTop) { show(); return }
        if (delta == 0f) return
        // Tiny direction changes and touch jitter must not flash the controls.
        travel = if (travel == 0f || (delta > 0f) == (travel > 0f)) travel + delta else delta
        if (travel >= 40f) hidden = true
        else if (travel <= -30f) hidden = false
    }

    fun endGesture() { travel = 0f }
    fun show() { hidden = false; travel = 0f }
}
