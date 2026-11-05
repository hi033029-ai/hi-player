package com.example.browser.gecko

import java.net.URI
import java.util.Locale

/** Navigation outcomes are delivered to UI, never used to copy browser cookies or credentials. */
internal sealed interface GeckoNavigationDecision {
    data object Allow : GeckoNavigationDecision
    data object Block : GeckoNavigationDecision
    data class SecureSignIn(val entryUrl: String) : GeckoNavigationDecision
    data class SecureSite(val url: String, val userGesture: Boolean) : GeckoNavigationDecision
    data class ExternalLink(val url: String) : GeckoNavigationDecision
}

/** The existing app policy supplies account recognition and origin-based routing preference. */
internal class GeckoNavigationPolicy(
    private val isGoogleAuthentication: (String) -> Boolean,
    private val secureEntryFor: (String) -> String,
    private val prefersSecureSite: (String) -> Boolean,
) {
    fun decide(url: String, currentUrl: String, userGesture: Boolean): GeckoNavigationDecision {
        val scheme = runCatching { URI(url).scheme?.lowercase(Locale.ROOT) }.getOrNull()
            ?: return GeckoNavigationDecision.Block
        if (scheme == "https" && prefersSecureSite(url)) {
            // Script redirects must not launch an external browser without a user tap.
            return GeckoNavigationDecision.SecureSite(url, userGesture)
        }
        if (scheme == "https" && isGoogleAuthentication(url)) {
            return GeckoNavigationDecision.SecureSignIn(secureEntryFor(currentUrl))
        }
        return when (scheme) {
            "https", "http" -> GeckoNavigationDecision.Allow
            "mailto", "tel", "sms" -> if (userGesture) GeckoNavigationDecision.ExternalLink(url) else GeckoNavigationDecision.Block
            else -> GeckoNavigationDecision.Block
        }
    }
}
