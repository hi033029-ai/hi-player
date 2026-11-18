package com.example.player

/** A completed video reopens from the start; a growing file retains its frontier. */
internal fun playbackResumePosition(savedPositionMs: Long, durationMs: Long, growing: Boolean): Long {
    val saved = savedPositionMs.coerceAtLeast(0L)
    if (growing || durationMs <= 0L) return saved
    // Allow for the final progress sample arriving just before the end event.
    // Scale the window down for short clips rather than treating all of them as watched.
    val endWindowMs = minOf(2_000L, durationMs / 20)
    return if (saved >= durationMs - endWindowMs) 0L else saved
}
