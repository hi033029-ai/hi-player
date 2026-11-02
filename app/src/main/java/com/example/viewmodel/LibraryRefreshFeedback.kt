package com.example.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Refresh work is not a reason to repeatedly replace a usable library with a scanner. */
internal class LibraryRefreshFeedback {
    private var firstPassFinished = false
    private var foregroundRequested = false
    private val _visible = MutableStateFlow(false)
    val visible = _visible.asStateFlow()
    fun request(userInitiated: Boolean) { foregroundRequested = foregroundRequested || userInitiated }
    fun begin(hasContent: Boolean) {
        _visible.value = !hasContent && (!firstPassFinished || foregroundRequested)
        foregroundRequested = false
    }
    fun finish() {
        firstPassFinished = true
        _visible.value = false
    }
}
