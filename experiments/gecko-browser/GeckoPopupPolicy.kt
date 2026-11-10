package com.example.browser.gecko

/** A borrowed click gesture does not authorize a script-created popup. */
internal object GeckoPopupPolicy {
    @Suppress("UNUSED_PARAMETER")
    fun allows(blockPopups: Boolean, hasUserGesture: Boolean, destinationBlockedByProtection: Boolean): Boolean =
        !destinationBlockedByProtection && !blockPopups
}
