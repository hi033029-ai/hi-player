package com.example.player

private val PLAYBACK_SPEEDS = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

fun nextPlaybackSpeed(current: Float): Float {
    val index = PLAYBACK_SPEEDS.indexOfFirst { kotlin.math.abs(it - current) < 0.01f }
    return PLAYBACK_SPEEDS[if (index < 0) 2 else (index + 1) % PLAYBACK_SPEEDS.size]
}

fun formatSpeedLabel(speed: Float): String {
    val text = if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString()
    return "${text}x"
}
