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

private val mobileAppSchemes = setOf("market", "intent", "tiktok", "musically")
private val mobileAppHosts = setOf("app-va.tiktok.com", "app.tiktok.com", "app-m.tiktok.com")
private val mobileAppRelay = Regex("snssdk[0-9]+\\.onelink\\.me")
private val googleOAuthPath = Regex("(^|/)oauth2?(/|$)")

internal fun isAppPromotionLink(url: String?, sourceUrl: String? = null): Boolean {
    val raw = url?.trim()?.takeIf { it.isNotEmpty() } ?: return false
    val scheme = raw.substringBefore(':', missingDelimiterValue = "").lowercase(Locale.ROOT)
    if (scheme in mobileAppSchemes || scheme.startsWith("snssdk")) return true
    if (scheme != "https" && scheme != "http") return false
    val host = runCatching { URI(raw).host?.lowercase(Locale.ROOT)?.trimEnd('.') }.getOrNull() ?: return false
    // Reject the launch page before it replaces the current document, even
    // when a redirect strips or changes the initiating page URI.
    if (host in mobileAppHosts || mobileAppRelay.matches(host)) return true
    if (host != "app-va.tiktokv.com") return false
    // Only consulted on navigation callbacks, so API and media requests to this
    // host still work. The mobile site uses it as a foreground app handoff.
    val sourceHost = runCatching { sourceUrl?.let { URI(it).host?.lowercase(Locale.ROOT)?.trimEnd('.') } }.getOrNull()
    return sourceHost == "tiktok.com" || sourceHost?.endsWith(".tiktok.com") == true
}

/** The existing app policy supplies account recognition and origin-based routing preference. */
internal class GeckoNavigationPolicy(
    private val isGoogleAuthentication: (String) -> Boolean,
    private val secureEntryFor: (String) -> String,
    private val prefersSecureSite: (String) -> Boolean,
    /** Let users try regular website sign-in inside this browser profile. */
    private val googleSignInInBrowser: Boolean = false,
) {
    fun withPopupOpener(page: String): GeckoNavigationPolicy = GeckoNavigationPolicy(
        isGoogleAuthentication, { current -> secureEntryFor(current.ifBlank { page }) }, prefersSecureSite,
        googleSignInInBrowser,
    )

    fun decide(url: String, currentUrl: String, userGesture: Boolean): GeckoNavigationDecision {
        if (isAppPromotionLink(url, currentUrl)) return GeckoNavigationDecision.Ignore
        val uri = runCatching { URI(url) }.getOrNull() ?: return GeckoNavigationDecision.Block
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return GeckoNavigationDecision.Block
        if (scheme == "https" && isGoogleAuthentication(url)) {
            val returnPage = secureEntryFor(currentUrl).takeUnless(isGoogleAuthentication)
            val path = uri.path.orEmpty().lowercase(Locale.ROOT)
            if (googleSignInInBrowser && !googleOAuthPath.containsMatchIn(path)) {
                val rejected = path.contains("/signin/rejected")
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
