package com.example.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

@androidx.annotation.OptIn(UnstableApi::class)
class VideoEffectRedrawTest {
    @Test fun pausedRedrawPreservesPositionAndNeverStartsAudioOnBothMedia3Versions() {
        var position = 7_350L
        var started = false
        var redraws = 0
        var prepares = 0
        val player = Proxy.newProxyInstance(ExoPlayer::class.java.classLoader, arrayOf(ExoPlayer::class.java)) { _, method, args ->
            when (method.name) {
                "getCurrentPosition" -> position
                "stop" -> { position = 0; null }
                "seekTo" -> { position = args!!.last() as Long; null }
                "setVideoEffects" -> { redraws++; null }
                "prepare" -> { prepares++; null }
                "play", "setPlayWhenReady" -> { started = true; null }
                else -> null
            }
        } as ExoPlayer
        VideoEffectRedraw.request(player)
        assertEquals(7_350L, position)
        assertFalse("A preset change must not start paused audio", started)
        assertEquals("Use either native redraw or one older-API re-prepare", 1, redraws + prepares)
    }
}
