package com.example.ui.screens

import java.net.URI

enum class BrowserPlaceholderLayout { NONE, YOUTUBE_FEED, YOUTUBE_WATCH }

object BrowserLoadingPolicy {
    fun placeholder(url: String, desktop: Boolean): BrowserPlaceholderLayout {
        if (desktop) return BrowserPlaceholderLayout.NONE
        val uri = runCatching { URI(url) }.getOrNull() ?: return BrowserPlaceholderLayout.NONE
        val host = uri.host?.lowercase() ?: return BrowserPlaceholderLayout.NONE
        if (uri.scheme !in listOf("https", "http") || uri.rawUserInfo != null) return BrowserPlaceholderLayout.NONE
        if (host != "youtube.com" && !host.endsWith(".youtube.com")) return BrowserPlaceholderLayout.NONE
        return when (uri.path.orEmpty().trimEnd('/')) {
            "", "/feed/subscriptions", "/feed/trending" -> BrowserPlaceholderLayout.YOUTUBE_FEED
            "/watch" -> BrowserPlaceholderLayout.YOUTUBE_WATCH
            else -> BrowserPlaceholderLayout.NONE
        }
    }
}
