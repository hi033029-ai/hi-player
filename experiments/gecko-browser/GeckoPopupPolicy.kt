package com.example.browser.gecko

/** Allow user-initiated windows unless an enabled filter identifies the destination. */
internal object GeckoPopupPolicy {
    fun allows(blockPopups: Boolean, hasUserGesture: Boolean, destinationBlockedByProtection: Boolean): Boolean =
        !destinationBlockedByProtection && (!blockPopups || hasUserGesture)
}
