package com.example.browser.gecko

internal data class GeckoTabSnapshot(
    val id: Int,
    val privateTab: Boolean,
    val url: String = "",
    val title: String = "New tab",
    val loading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val error: GeckoPageError? = null,
    val secure: Boolean = false,
    val fullscreen: Boolean = false,
    val mediaUrl: String? = null,
    val mediaMime: String? = null,
    val mediaTitle: String? = null,
    val readerText: String? = null,
) {
    val loadFailed: Boolean get() = error != null
}
