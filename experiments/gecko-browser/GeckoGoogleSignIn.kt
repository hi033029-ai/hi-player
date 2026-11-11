package com.example.browser.gecko

import java.net.URLEncoder

/** Builds a Google account-chooser URL; website authentication itself stays in a Custom Tab. */
internal object GeckoGoogleSignIn {
    const val ACCOUNT_HOME = "https://myaccount.google.com/"

    private fun validEmail(value: String?): String? = value?.trim()?.takeIf {
        it.length in 3..254 && it.count { c -> c == '@' } == 1 && it.none { c -> c.isWhitespace() || c.isISOControl() }
    }

    /** Preselects an account hint only; Google still handles credentials and consent. */
    fun entryUrl(email: String?): String = buildString {
        append("https://accounts.google.com/AccountChooser?continue=")
        append(URLEncoder.encode(ACCOUNT_HOME, "UTF-8"))
        validEmail(email)?.let { append("&Email=").append(URLEncoder.encode(it, "UTF-8")) }
    }
}
