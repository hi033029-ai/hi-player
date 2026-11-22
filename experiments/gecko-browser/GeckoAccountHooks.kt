package com.example.browser.gecko

/** Host callbacks for the app account menu. */
internal data class GeckoAccountHooks(
    val signedIn: () -> Boolean,
    val openAccount: () -> Unit,
    val photoUrl: () -> String? = { null },
)
