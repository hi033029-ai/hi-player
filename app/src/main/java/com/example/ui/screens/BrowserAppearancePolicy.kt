package com.example.ui.screens

/** Pure policy shared by retained/new tabs; contains no account or website state. */
internal object BrowserAppearancePolicy {
    fun textZoom(percent: Int): Int = percent.coerceIn(80, 200)

    /** Keep the installed WebView's Chromium version instead of advertising an old build. */
    fun desktopUserAgent(mobileUserAgent: String): String {
        val chromium = Regex("Chrome/[0-9.]+").find(mobileUserAgent)?.value
        // Preserve the native UA rather than fabricate a Chromium version if unavailable.
        if (chromium == null) return mobileUserAgent.replace(" Mobile", "")
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) $chromium Safari/537.36"
    }
}
