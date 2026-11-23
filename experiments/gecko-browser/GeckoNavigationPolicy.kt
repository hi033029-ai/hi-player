package com.example.browser.gecko

import java.net.URI
import java.util.Locale

/** Navigation policy matches the app's existing Google/secure-site boundaries without copying cookies. */
internal sealed interface GeckoNavigationDecision {
    data object Allow : GeckoNavigationDecision
    data object Block : GeckoNavigationDecision
    data object Ignore : GeckoNavigationDecision
    data class SecureSignIn(val entryUrl: String, val returnPage: String? = null, val userGesture: Boolean = false) : GeckoNavigationDecision
    data class SecureSite(val url: String, val userGesture: Boolean) : GeckoNavigationDecision
    data class ExternalLink(val url: String) : GeckoNavigationDecision
}

/** Blocks app-store / deep-link promotion redirects (such as TikTok app-bounce links and snssdk* schemes) so the tab stays on the web page. */
internal fun isAppPromotionLink(url: String?): Boolean {
    val raw = url?.trim()?.takeIf { it.isNotEmpty() } ?: return false
    val scheme = raw.substringBefore(':', missingDelimiterValue = "").lowercase(Locale.ROOT)
    if (scheme in setOf("market", "intent", "tiktok", "musically") || scheme.startsWith("snssdk")) {
        return true
    }
    if (scheme == "http" || scheme == "https") {
        val host = runCatching { URI(raw).host?.lowercase(Locale.ROOT)?.trimEnd('.') }.getOrNull()
        if (host in setOf("app-va.tiktok.com", "app.tiktok.com", "app-m.tiktok.com")) {
            return true
        }
    }
    return false
}

/** The existing app policy supplies account recognition and origin-based routing preference. */
internal class GeckoNavigationPolicy(
    private val isGoogleAuthentication: (String) -> Boolean,
    private val secureEntryFor: (String) -> String,
    private val prefersSecureSite: (String) -> Boolean,
    /** Keep Google sign-in pages inside Hi Browser's Gecko profile so YouTube and other tabs share the signed-in session. */
    private val googleSignInInBrowser: Boolean = false,
) {
    fun decide(url: String, currentUrl: String, userGesture: Boolean): GeckoNavigationDecision {
        if (isAppPromotionLink(url)) return GeckoNavigationDecision.Ignore
        val scheme = runCatching { URI(url).scheme?.lowercase(Locale.ROOT) }.getOrNull()
            ?: return GeckoNavigationDecision.Block
        if (scheme == "https" && isGoogleAuthentication(url)) {
            val returnPage = secureEntryFor(currentUrl).takeUnless(isGoogleAuthentication)
            if (googleSignInInBrowser) {
                val rejected = runCatching { URI(url).rawPath?.lowercase(Locale.ROOT)?.contains("/signin/rejected") == true }.getOrDefault(false)
                return if (rejected) GeckoNavigationDecision.SecureSignIn(url, returnPage, userGesture)
                else GeckoNavigationDecision.Allow
            }
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
