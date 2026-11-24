package com.example.ui.screens

import java.net.URI

enum class BrowserPlaceholderLayout { NONE, YOUTUBE_FEED, YOUTUBE_WATCH, VERTICAL_VIDEO, SOCIAL_FEED, PHOTO_FEED, POSTER_GRID, CATALOG_LIST }

object BrowserLoadingPolicy {
    private val catalogs = mapOf("katmovie" to BrowserPlaceholderLayout.POSTER_GRID,
        "katmoviehd" to BrowserPlaceholderLayout.POSTER_GRID, "4khdhub" to BrowserPlaceholderLayout.POSTER_GRID,
        "hdhub4u" to BrowserPlaceholderLayout.CATALOG_LIST, "uhdmovies" to BrowserPlaceholderLayout.POSTER_GRID,
        "7starhd" to BrowserPlaceholderLayout.CATALOG_LIST)
    fun placeholder(url: String, desktop: Boolean): BrowserPlaceholderLayout {
        if (desktop) return BrowserPlaceholderLayout.NONE
        val uri = runCatching { URI(url) }.getOrNull() ?: return BrowserPlaceholderLayout.NONE
        val host = uri.host?.lowercase() ?: return BrowserPlaceholderLayout.NONE
        if (uri.scheme !in listOf("https", "http") || uri.rawUserInfo != null) return BrowserPlaceholderLayout.NONE
        val path = uri.path.orEmpty().trimEnd('/')
        val site = host.removePrefix("www.").removePrefix("m.").removePrefix("mobile.")
        if (path.contains("login", true) || path.contains("signin", true) || path.contains("checkpoint", true)) return BrowserPlaceholderLayout.NONE
        when (site) {
            "tiktok.com" -> return BrowserPlaceholderLayout.VERTICAL_VIDEO
            "facebook.com", "fb.com" -> return if (path.startsWith("/reel")) BrowserPlaceholderLayout.VERTICAL_VIDEO else BrowserPlaceholderLayout.SOCIAL_FEED
            "instagram.com" -> return if (path.startsWith("/reel")) BrowserPlaceholderLayout.VERTICAL_VIDEO else BrowserPlaceholderLayout.PHOTO_FEED
        }
        if (site.count { it == '.' } == 1) catalogs[site.substringBefore('.')]?.let { return it }
        if (site != "youtube.com") return BrowserPlaceholderLayout.NONE
        if (path.startsWith("/shorts/")) return BrowserPlaceholderLayout.VERTICAL_VIDEO
        return when (path) {
            "", "/feed/subscriptions", "/feed/trending" -> BrowserPlaceholderLayout.YOUTUBE_FEED
            "/watch" -> BrowserPlaceholderLayout.YOUTUBE_WATCH
            else -> BrowserPlaceholderLayout.NONE
        }
    }
}
