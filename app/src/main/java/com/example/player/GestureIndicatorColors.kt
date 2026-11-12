package com.example.player

import kotlin.math.roundToInt

/** ARGB colors shared by the native and GL gesture feedback. */
internal object GestureIndicatorColors {
    val cyan: Int = 0xFF00E5FF.toInt()
    val orange: Int = 0xFFFFA000.toInt()
    val red: Int = 0xFFFF0000.toInt()

    /** 0–100% cyan; smoothly reaches orange at 150% and red at 200%. */
    fun volume(level: Float): Int {
        val safe = if (level.isFinite()) level.coerceIn(0f, 2f) else 0f
        return when {
            safe <= 1f -> cyan
            safe <= 1.5f -> mix(cyan, orange, (safe - 1f) * 2f)
            else -> mix(orange, red, (safe - 1.5f) * 2f)
        }
    }

    private fun mix(start: Int, end: Int, amount: Float): Int {
        fun channel(shift: Int): Int {
            val a = (start ushr shift) and 0xff
            val b = (end ushr shift) and 0xff
            return (a + (b - a) * amount).roundToInt().coerceIn(0, 255)
        }
        return (0xff shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}
