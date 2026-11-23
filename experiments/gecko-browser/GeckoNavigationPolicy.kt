package com.example.browser.gecko

import java.net.URI
import java.util.Locale

/** Navigation outcomes are delivered to UI, never used to copy browser cookies or credentials. */
internal sealed interface GeckoNavigationDecision {
    data object Allow : GeckoNavigationDecision
    data object Block : GeckoNavigationDecision
    data object Ignore : GeckoNavigationDecision
    data class SecureSignIn(val entryUrl: String, val returnPage: String? = null, val userGesture: Boolean = false) : GeckoNavigationDecision
    data class SecureSite(val url: String, val userGesture: Boolean) : GeckoNavigationDecision
    data class ExternalLink(val url: String) : GeckoNavigationDecision
}

/** The existing app policy supplies account recognition and origin-based routing preference. */
internal fun isAppPromotionLink(url: String?): Boolean =
    url?.substringBefore(':')?.lowercase(Locale.ROOT) in setOf("market", "intent", "snssdk1233")

internal class GeckoNavigationPolicy(
    private val isGoogleAuthentication: (String) -> Boolean,
    private val secureEntryFor: (String) -> String,
    private val prefersSecureSite: (String) -> Boolean,
) {
    fun decide(url: String, currentUrl: String, userGesture: Boolean): GeckoNavigationDecision {
        if (isAppPromotionLink(url)) return GeckoNavigationDecision.Ignore
        val scheme = runCatching { URI(url).scheme?.lowercase(Locale.ROOT) }.getOrNull()
            ?: return GeckoNavigationDecision.Block
        if (scheme == "https" && isGoogleAuthentication(url)) {
            val returnPage = secureEntryFor(currentUrl).takeUnless(isGoogleAuthentication)
            if (!userGesture && returnPage == null) return GeckoNavigationDecision.Ignore
            return GeckoNavigationDecision.SecureSignIn(url, returnPage, userGesture)
        }
        if (scheme == "https" && prefersSecureSite(url)) {
            // Script redirects must not launch an external browser without a user tap.
            return GeckoNavigationDecision.SecureSite(url, userGesture)
        }
        return when (scheme) {
            "https", "http" -> GeckoNavigationDecision.Allow
            "mailto", "tel", "sms" -> if (userGesture) GeckoNavigationDecision.ExternalLink(url) else GeckoNavigationDecision.Block
            else -> GeckoNavigationDecision.Block
        }
    }
}
