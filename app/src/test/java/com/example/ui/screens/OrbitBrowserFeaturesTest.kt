package com.example.ui.screens

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.model.StreamUrlClassifier
import org.json.JSONArray
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OrbitBrowserFeaturesTest {
    @Test
    fun bundledUrlRulesCanBlockRequestsAndBeDisabled() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        OrbitBrowserFilterEngine.load(context)
        val adEndpoint = Uri.parse("https://www.youtube.com/youtubei/v1/player/ad_break")

        assertTrue(OrbitBrowserFilterEngine.shouldBlock(adEndpoint, enabled = true))
        assertFalse(OrbitBrowserFilterEngine.shouldBlock(adEndpoint, enabled = false))
        assertFalse(
            OrbitBrowserFilterEngine.shouldBlock(
                Uri.parse("https://www.youtube.com/youtubei/v1/player/next"),
                enabled = true,
            ),
        )
    }

    @Test
    fun cosmeticRulesAreValidSeparateCssRules() {
        OrbitBrowserFilterEngine.load(ApplicationProvider.getApplicationContext())
        val css = OrbitBrowserFilterEngine.cosmeticCss(host = null, enabled = true)

        assertTrue(css.contains(".ad-banner{display:none!important}"))
        assertTrue(css.contains(".adsbygoogle{display:none!important}"))
        assertFalse(css.contains("},"))
    }

    @Test
    fun readerModeEscapesPageControlledMarkup() {
        val html = OrbitReaderMode.html(
            "<script>alert(1)</script>",
            JSONArray().put("p:<img src=x onerror=alert(1)>")
        )

        assertFalse(html.contains("<script>alert(1)</script>"))
        assertFalse(html.contains("<img src=x onerror"))
        assertTrue(html.contains("&lt;script&gt;"))
        assertTrue(html.contains("&lt;img"))
    }

    @Test
    fun youtubeHostsStayInBrowserAndDirectMediaIsPlayableInHiPlayer() {
        listOf(
            "https://www.youtube.com/watch?v=abc",
            "https://youtu.be/abc",
            "https://www.youtube-nocookie.com/embed/abc",
        ).forEach { assertTrue(isOrbitYoutube(it)) }
        assertFalse(isOrbitYoutube("https://videos.example.org/watch/123"))

        val mp4 = StreamUrlClassifier.classify("https://cdn.example.org/media/clip.mp4?token=abc")
        val hls = StreamUrlClassifier.classify("https://cdn.example.org/live/playlist.m3u8")
        assertTrue(mp4.isPlayableInHiPlayer)
        assertFalse(mp4.requiresWebResolution)
        assertTrue(hls.isPlayableInHiPlayer)
        assertFalse(hls.requiresWebResolution)
        assertFalse(StreamUrlClassifier.classify("https://www.youtube.com/watch?v=abc").isPlayableInHiPlayer)
    }
}
