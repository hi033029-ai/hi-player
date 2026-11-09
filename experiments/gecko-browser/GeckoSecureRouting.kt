package com.example.browser.gecko

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.auth.SecureWebsitePolicy
import com.example.auth.SecureWebSessions
import java.net.URI
import java.util.Locale

/** Only origin choices are persisted. Browser sessions and authentication stay outside the app. */
internal class GeckoSecureRouting(
    private val tabPrivacy: (Int) -> Boolean?,
    private val prefers: (String) -> Boolean,
    private val rememberOrigin: (String) -> Unit,
    private val forgetOrigin: (String) -> Unit,
    private val openWebsite: (String) -> Boolean,
    private val openExternal: (String) -> Boolean,
) {
    internal data class Request(val tabId: Int, val url: String, val isPrivate: Boolean, val external: Boolean)
    private val privateOrigins = mutableSetOf<String>()
    private val inBrowserGoogleTabs = mutableSetOf<Int>()
    private val routes = mutableStateMapOf<Int, String>()
    var pending by mutableStateOf<Request?>(null)
        private set
    var failure by mutableStateOf(false)
        private set

    fun policy(isPrivate: Boolean) = createPolicy(isPrivate) { false }
    fun policyForTab(tabId: Int, isPrivate: Boolean) = createPolicy(
        isPrivate,
    ) { !isPrivate && tabId in inBrowserGoogleTabs }
    private fun createPolicy(isPrivate: Boolean, allowGoogleSignInInBrowser: () -> Boolean) = GeckoNavigationPolicy(
        SecureWebSessions::isGoogleAuthentication,
        SecureWebSessions::entryForGoogleAuth,
        { url -> val origin = SecureWebsitePolicy.originKey(url); origin != null &&
            if (isPrivate) origin in privateOrigins else prefers(url) },
        googleSignInInBrowser = allowGoogleSignInInBrowser,
    )
    fun allowGoogleSignInInBrowser(tabId: Int) {
        if (tabPrivacy(tabId) == false) inBrowserGoogleTabs.add(tabId)
    }

    fun decision(tabId: Int, decision: GeckoNavigationDecision) {
        val isPrivate = tabPrivacy(tabId) ?: return
        val url: String
        val external: Boolean
        when (decision) {
            is GeckoNavigationDecision.SecureSignIn -> { url = decision.entryUrl; external = false }
            is GeckoNavigationDecision.SecureSite -> { url = decision.url; external = false }
            is GeckoNavigationDecision.ExternalLink -> { url = decision.url; external = true }
            else -> return
        }
        if (external) {
            val scheme = runCatching { URI(url).scheme?.lowercase(Locale.ROOT) }.getOrNull()
            if (scheme !in setOf("mailto", "tel", "sms")) return
        } else if (SecureWebsitePolicy.originKey(url) == null) return
        // Even a script-initiated request needs an explicit tap before leaving Hi Browser.
        pending = Request(tabId, url, isPrivate, external)
        failure = false
    }

    fun dismiss() { pending = null; failure = false }
    fun dismissFailure() { failure = false }
    fun confirm() {
        val request = pending ?: return
        if (tabPrivacy(request.tabId) != request.isPrivate) { dismiss(); return }
        pending = null
        val opened = runCatching { if (request.external) openExternal(request.url) else openWebsite(request.url) }.getOrDefault(false)
        if (!opened) { failure = true; return }
        if (!request.external) {
            if (request.isPrivate) SecureWebsitePolicy.originKey(request.url)?.let(privateOrigins::add)
            else rememberOrigin(request.url)
            routes[request.tabId] = request.url
        }
    }
    fun route(tabId: Int): String? = routes[tabId]
    fun continueWebsite(tabId: Int) {
        val url = routes[tabId] ?: return
        val privacy = tabPrivacy(tabId) ?: return
        pending = Request(tabId, url, privacy, false)
    }
    fun useHiBrowser(tabId: Int) {
        val url = routes.remove(tabId) ?: return
        if (tabPrivacy(tabId) == true) SecureWebsitePolicy.originKey(url)?.let(privateOrigins::remove)
        else forgetOrigin(url)
    }
    fun leaveTab(tabId: Int) {
        routes.remove(tabId)
        inBrowserGoogleTabs.remove(tabId)
        if (pending?.tabId == tabId) dismiss()
    }
    fun prune(validIds: Set<Int>) {
        routes.keys.toList().filter { it !in validIds }.forEach(routes::remove)
        inBrowserGoogleTabs.retainAll(validIds)
        if (pending != null && pending?.tabId !in validIds) dismiss()
        if (validIds.none { tabPrivacy(it) == true }) privateOrigins.clear()
    }
    fun clearPrivateRouting() { privateOrigins.clear(); routes.clear(); inBrowserGoogleTabs.clear(); dismiss() }
}
