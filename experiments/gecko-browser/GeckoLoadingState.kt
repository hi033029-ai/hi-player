package com.example.browser.gecko

internal enum class GeckoPageError { PROTECTION, NETWORK, SECURITY, CONTENT, RENDERER }

internal object GeckoLoadingState {
    private fun canonicalUrl(url: String): String = if (url.substringBefore('#').equals("about:blank", true)) "" else url
    fun start(state: GeckoTabSnapshot, url: String): GeckoTabSnapshot {
        val page = canonicalUrl(url)
        val loading = page.isNotBlank()
        return state.copy(url = page, title = "New tab", loading = loading, progress = 0,
            error = null, secure = false, awaitingFirstPaint = loading &&
                (state.awaitingFirstPaint || state.url != page || state.loadFailed ||
                    (!state.loading && state.progress == 0)))
    }
    fun location(state: GeckoTabSnapshot, url: String?): GeckoTabSnapshot =
        if (url == null || canonicalUrl(url) == state.url) state else state.copy(url = canonicalUrl(url), secure = false)
    fun progress(state: GeckoTabSnapshot, progress: Int): GeckoTabSnapshot =
        if (state.loading) state.copy(progress = progress.coerceIn(0, 100)) else state
    fun cancel(state: GeckoTabSnapshot): GeckoTabSnapshot = state.copy(loading = false, progress = 0, awaitingFirstPaint = false)
    fun error(state: GeckoTabSnapshot, error: GeckoPageError): GeckoTabSnapshot =
        state.copy(loading = false, error = error, secure = false, awaitingFirstPaint = false)
    fun stop(state: GeckoTabSnapshot, success: Boolean, userCancelled: Boolean = false): GeckoTabSnapshot = when {
        userCancelled -> cancel(state)
        success && state.error == null -> state.copy(loading = false, progress = 100, awaitingFirstPaint = false)
        success -> state.copy(loading = false, awaitingFirstPaint = false)
        else -> state.copy(loading = false, error = state.error ?: GeckoPageError.CONTENT, secure = false, awaitingFirstPaint = false)
    }
}
