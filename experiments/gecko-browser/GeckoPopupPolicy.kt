package com.example.browser.gecko

/** Popup setting blocks script-opened windows and lets normal tapped links proceed. */
internal object GeckoPopupPolicy {
    fun allows(blockPopups: Boolean, hasUserGesture: Boolean, destinationBlockedByProtection: Boolean): Boolean =
        !destinationBlockedByProtection && (!blockPopups || hasUserGesture)
}
