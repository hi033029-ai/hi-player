package com.example.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.video.VideoRendererEventListener
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Guards against dependency upgrades silently removing or reordering software fallbacks. */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SoftwareDecoderRegistrationTest {
    private fun rendererNames(extensionMode: Int): List<String> {
        val factory = DefaultRenderersFactory(ApplicationProvider.getApplicationContext<Context>())
            .setExtensionRendererMode(extensionMode)
        return factory.createRenderers(
            Handler(Looper.getMainLooper()),
            object : VideoRendererEventListener {},
            object : AudioRendererEventListener {},
            { _ -> },
            { _ -> },
        ).map { it.javaClass.name }
    }

    @Test
    fun bundledSoftwareRenderersRemainRegistered() {
        val names = rendererNames(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON).toSet()
        assertTrue("The bundled Libgav1 AV1 renderer is not registered: $names",
            "androidx.media3.decoder.av1.Libgav1VideoRenderer" in names)
        assertTrue("The bundled FFmpeg audio renderer is not registered: $names",
            "androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer" in names)
    }

    @Test
    fun ffmpegAudioRendererPrecedesPlatformAudioInPreferMode() {
        val names = rendererNames(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        val ffmpegIndex = names.indexOf("androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer")
        val platformAudioIndex = names.indexOf("androidx.media3.exoplayer.audio.MediaCodecAudioRenderer")
        assertTrue("FFmpeg audio renderer is missing: $names", ffmpegIndex >= 0)
        assertTrue("FFmpeg should be attempted before MediaCodec audio: $names",
            ffmpegIndex < platformAudioIndex)
    }
}
