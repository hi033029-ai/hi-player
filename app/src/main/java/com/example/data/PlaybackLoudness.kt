package com.example.data

import android.content.Context
import android.media.AudioManager
import kotlin.math.roundToInt

/** Last-used player volume, independent of optional named profiles. Never applied on app launch. */
internal object PlaybackLoudness {
    private const val KEY = "last_media_volume_percent"
    private fun prefs(context: Context) = context.getSharedPreferences("playback_last_used", Context.MODE_PRIVATE)
    fun savedPercent(context: Context): Int? = prefs(context).let {
        if (it.contains(KEY)) it.getInt(KEY, 0).coerceIn(0, 100) else null
    }
    fun remember(context: Context, percent: Int) {
        val normalized = percent.coerceIn(0, 100)
        if (savedPercent(context) != normalized) prefs(context).edit().putInt(KEY, normalized).apply()
    }
    fun capture(context: Context) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max > 0) remember(context, (audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / max).roundToInt())
    }
    fun restoreForPlayback(context: Context, legacyPercent: Int = -1) {
        val saved = savedPercent(context) ?: legacyPercent.takeIf { it >= 0 } ?: return
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) return
        // Keep Android's own output/safe-volume restrictions authoritative.
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, (saved * max / 100f).roundToInt().coerceIn(0, max), 0) }
        capture(context)
    }
}
