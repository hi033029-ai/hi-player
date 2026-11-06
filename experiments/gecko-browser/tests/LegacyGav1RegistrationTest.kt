package com.example.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.video.VideoRendererEventListener
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Probe registration and ordering only; these tests do not decode video. */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LegacyGav1RegistrationTest {
    private val gav1 = "androidx.media3.decoder.av1.Libgav1VideoRenderer"
    private val hardware = "androidx.media3.exoplayer.video.MediaCodecVideoRenderer"
    private val ffmpeg = "androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer"

    private fun renderers(mode: Int): List<String> = LegacyGav1RenderersFactory(
        ApplicationProvider.getApplicationContext<Context>()
    ).setExtensionRendererMode(mode).createRenderers(
        Handler(Looper.getMainLooper()), object : VideoRendererEventListener {},
        object : AudioRendererEventListener {}, { _ -> }, { _ -> },
    ).map { it.javaClass.name }

    @Test fun enabledKeepsHardwareFirstAndAddsBothBundledFallbacks() {
        val names = renderers(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        assertEquals(1, names.count { it == gav1 })
        assertTrue(names.indexOf(hardware) in 0 until names.indexOf(gav1))
        assertTrue(ffmpeg in names)
    }
    @Test fun disabledOmitsExtensions() {
        val names = renderers(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
        assertTrue(hardware in names)
        assertFalse(gav1 in names)
        assertFalse(ffmpeg in names)
    }
    @Test fun preferredSoftwarePrecedesHardware() {
        val names = renderers(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        assertEquals(1, names.count { it == gav1 })
        assertTrue(names.indexOf(gav1) in 0 until names.indexOf(hardware))
    }
}
