package com.example.browser.gecko

/** No backend response or exception text is exposed to the UI. */
internal enum class GeckoPageError { NETWORK, SECURITY, CONTENT, RENDERER }

internal object GeckoLoadingState {
    fun start(state: GeckoTabSnapshot, url: String): GeckoTabSnapshot =
        state.copy(url = url, loading = true, progress = 0, error = null, secure = false)

    fun location(state: GeckoTabSnapshot, url: String?): GeckoTabSnapshot =
        if (url.isNullOrBlank() || url == state.url) state else state.copy(url = url, secure = false)

    fun progress(state: GeckoTabSnapshot, progress: Int): GeckoTabSnapshot =
        if (state.loading) state.copy(progress = progress.coerceIn(0, 100)) else state

    fun cancel(state: GeckoTabSnapshot): GeckoTabSnapshot = state.copy(loading = false)

    fun error(state: GeckoTabSnapshot, error: GeckoPageError): GeckoTabSnapshot =
        state.copy(loading = false, error = error, secure = false)

    fun stop(state: GeckoTabSnapshot, success: Boolean): GeckoTabSnapshot = when {
        success && state.error == null -> state.copy(loading = false, progress = 100)
        success -> state.copy(loading = false)
        else -> state.copy(loading = false, error = state.error ?: GeckoPageError.CONTENT, secure = false)
    }
}
