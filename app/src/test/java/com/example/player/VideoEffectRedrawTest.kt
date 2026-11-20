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
        var seeks = 0
        val player = Proxy.newProxyInstance(ExoPlayer::class.java.classLoader, arrayOf(ExoPlayer::class.java)) { _, method, args ->
            when (method.name) {
                "getCurrentPosition" -> position
                "getDuration" -> 30_000L
                "stop", "prepare" -> error("Redrawing must keep the prepared player")
                "seekTo" -> { seeks++; position = args!!.last() as Long; null }
                "setVideoEffects" -> { redraws++; null }
                "play", "setPlayWhenReady" -> { started = true; null }
                else -> null
            }
        } as ExoPlayer
        VideoEffectRedraw.request(player)
        assertEquals("Redraw or seek to the adjacent frame time", 1, redraws + seeks)
        assertTrue(position in 7_350L..7_351L)
        VideoEffectRedraw.request(player)
        assertEquals(7_350L, position)
        assertFalse("A preset change must not start paused audio", started)
        assertEquals(2, redraws + seeks)
    }
}
