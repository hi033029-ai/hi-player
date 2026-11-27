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
    fun allowsPrompt(blockPopups: Boolean, destinationBlockedByProtection: Boolean,
                     consumeDownloadClick: () -> Boolean): Boolean =
        !destinationBlockedByProtection && (!blockPopups || consumeDownloadClick())
}
