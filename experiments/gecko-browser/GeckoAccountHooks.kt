package com.example.browser.gecko

/** Host callbacks for the app account menu and website Google sign-in hint. */
internal data class GeckoAccountHooks(
    val email: () -> String?,
    val signedIn: () -> Boolean,
    val openAccount: () -> Unit,
)
