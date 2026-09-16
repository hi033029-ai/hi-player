package com.example.recognition

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/** Stable in-app YouTube IFrame player. The URL is loaded once per video ID. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeEmbedPlayer(videoId: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val embedUrl = remember(videoId) {
        "https://www.youtube.com/embed/$videoId?autoplay=1&playsinline=1&rel=0"
    }
    val webView = remember(videoId) {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.loadsImagesAutomatically = true
            settings.setSupportZoom(false)
            setBackgroundColor(android.graphics.Color.BLACK)
            webChromeClient = WebChromeClient()
            loadUrl(embedUrl)
        }
    }

    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.clearHistory()
            webView.removeAllViews()
            webView.destroy()
        }
    }

    AndroidView(
        modifier = modifier.fillMaxWidth().height(220.dp),
        factory = { webView },
        // The WebView is keyed by videoId above and loads exactly once. Do not
        // reload or call onResume from update: both can interrupt playback.
        update = {},
    )
}
