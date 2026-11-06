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

/** Guards against dependency upgrades silently removing the existing software fallbacks. */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SoftwareDecoderRegistrationTest {
    @Test fun bundledSoftwareRenderersRemainRegistered() {
        val factory = DefaultRenderersFactory(ApplicationProvider.getApplicationContext<Context>())
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        val renderers = factory.createRenderers(
            Handler(Looper.getMainLooper()),
            object : VideoRendererEventListener {},
            object : AudioRendererEventListener {},
            { _ -> },
            { _ -> },
        )
        val names = renderers.map { it.javaClass.name }.toSet()
        assertTrue("The bundled Libgav1 AV1 renderer is not registered: $names",
            "androidx.media3.decoder.av1.Libgav1VideoRenderer" in names)
        assertTrue("The bundled FFmpeg audio renderer is not registered: $names",
            "androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer" in names)
    }
}
