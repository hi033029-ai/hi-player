package com.example.browser.gecko

/** Allow user-initiated windows unless an enabled filter identifies the destination. */
internal object GeckoPopupPolicy {
    fun allows(blockPopups: Boolean, hasUserGesture: Boolean, destinationBlockedByProtection: Boolean): Boolean =
        !destinationBlockedByProtection && (!blockPopups || hasUserGesture)

    /**
     * Gecko only calls onPopupPrompt for windows it considers unsolicited.
     * A verified, short-lived download tap can authorize one such window.
     * Do not consume the tap for ads or when popup blocking is disabled.
     */
    fun requiresReview(blockPopups: Boolean, userInitiated: Boolean, flaggedDestination: Boolean): Boolean =
        flaggedDestination || (blockPopups && !userInitiated)

    /** A delayed label-based click is not proof of trust for a cross-origin URL. */
    fun isTrustedDownloadTarget(page: String, target: String): Boolean {
        val origin = originKey(page) ?: return false
        return target == "about:blank" || origin == originKey(target)
    }

    private fun originKey(value: String): Triple<String, String, Int>? = runCatching {
        val uri = java.net.URI(value)
        val scheme = uri.scheme?.lowercase(java.util.Locale.ROOT)
        val host = uri.host?.lowercase(java.util.Locale.ROOT)
        if (scheme !in setOf("https", "http") || scheme == null || host.isNullOrBlank() || uri.userInfo != null) null
        else Triple(scheme, host, if (uri.port == -1) if (scheme == "https") 443 else 80 else uri.port)
    }.getOrNull()

    fun allowsPrompt(blockPopups: Boolean, destinationBlockedByProtection: Boolean,
                     consumeDownloadClick: () -> Boolean): Boolean =
        !destinationBlockedByProtection && (!blockPopups || consumeDownloadClick())
}
