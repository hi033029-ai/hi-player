package com.example.browser.gecko
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.net.URI
import java.net.URLEncoder
import java.util.Locale

/**
 * Google website sign-in that happens on Google's own pages inside Hi Browser's regular
 * Gecko profile. Google sets its own cookies there, so every regular tab, Google service
 * and third-party "Sign in with Google" button can reuse that session. Hi Player never
 * reads, copies, injects or exports Google cookies, passwords or tokens.
 */
internal object GeckoGoogleSignIn {
    const val ACCOUNT_HOME = "https://myaccount.google.com/"
    private val authHosts = setOf("accounts.google.com", "accounts.youtube.com")
    private fun parts(url: String): Pair<String, String>? = runCatching {
        val uri = URI(url)
        if (!uri.scheme.equals("https", true) || uri.userInfo != null) return null
        val host = uri.host?.lowercase(Locale.ROOT)?.trimEnd('.')?.takeIf { it.isNotBlank() } ?: return null
        host to (uri.rawPath ?: "")
    }.getOrNull()
    fun isAuthPage(url: String): Boolean = parts(url)?.first in authHosts
    /** Google's "This browser or app may not be secure" refusal page. */
    fun isRejected(url: String): Boolean {
        val (host, path) = parts(url) ?: return false
        return host in authHosts && path.lowercase(Locale.ROOT).contains("/signin/rejected")
    }
    /** Google lands here (the requested continue page) after a completed or existing sign-in. */
    fun isComplete(url: String): Boolean = parts(url)?.first == "myaccount.google.com"
    private fun validEmail(value: String?): String? = value?.trim()?.takeIf {
        it.length in 3..254 && it.count { c -> c == '@' } == 1 && it.none { c -> c.isWhitespace() || c.isISOControl() }
    }
    /** Pre-selects the Hi Player Google account when known; Google still asks for consent/password. */
    fun entryUrl(email: String?): String = buildString {
        append("https://accounts.google.com/AccountChooser?continue=")
        append(URLEncoder.encode(ACCOUNT_HOME, "UTF-8"))
        validEmail(email)?.let { append("&Email=").append(URLEncoder.encode(it, "UTF-8")) }
    }
}

/** Host-supplied Hi Player account access; only the email is used, as a Google account hint. */
internal class GeckoAccountHooks(
    val email: () -> String?,
    val signedIn: () -> Boolean,
    val openAccount: () -> Unit,
)

/** Tracks the helper tab so Hi Browser returns to the page the user started from. */
internal class GeckoGoogleSignInFlow {
    var signInTabId by mutableStateOf<Int?>(null)
        private set
    var returnTabId: Int? = null
        private set
    var notice by mutableStateOf<String?>(null)
    fun begin(returnTo: Int, signInTab: Int) { returnTabId = returnTo; signInTabId = signInTab; notice = null }
    /** Returns the tab to reopen once the sign-in tab reports completion; null otherwise. */
    fun onUrl(tabId: Int, url: String): Int? {
        if (tabId != signInTabId || !GeckoGoogleSignIn.isComplete(url)) return null
        val back = returnTabId
        clear()
        notice = "Signed in to Google in Hi Browser. Websites that offer Google sign-in can now use this account."
        return back
    }
    fun forgetMissing(validIds: Set<Int>) { if (signInTabId != null && signInTabId !in validIds) clear() }
    fun clear() { signInTabId = null; returnTabId = null }
}
