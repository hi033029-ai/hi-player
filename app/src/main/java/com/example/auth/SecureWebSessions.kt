package com.example.auth

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent

/** Browser-managed, non-ephemeral sessions. Never import/export account cookies. */
internal object SecureWebSessions {
    const val GOOGLE_ACCOUNT = "https://accounts.google.com/"
    const val YOUTUBE = "https://www.youtube.com/"
    const val GMAIL = "https://mail.google.com/"
    fun isGoogleAuthentication(url: String): Boolean = runCatching {
        val uri = java.net.URI(url)
        uri.scheme.equals("https", true) && uri.host?.lowercase() in setOf("accounts.google.com", "accounts.youtube.com")
    }.getOrDefault(false)
    fun validPage(url: String): Boolean = runCatching {
        val uri = java.net.URI(url)
        uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null
    }.getOrDefault(false)
    fun entryForGoogleAuth(sourcePage: String): String =
        sourcePage.takeIf { validPage(it) && !isGoogleAuthentication(it) } ?: GOOGLE_ACCOUNT

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences("secure_website_routing", Context.MODE_PRIVATE)

    fun prefersBrowser(context: Context, url: String): Boolean {
        val key = SecureWebsitePolicy.originKey(url) ?: return false
        return key in preferences(context).getStringSet("origins", emptySet()).orEmpty()
    }

    fun rememberBrowser(context: Context, url: String) {
        val key = SecureWebsitePolicy.originKey(url) ?: return
        val prefs = preferences(context)
        val sites = (prefs.getStringSet("origins", emptySet()).orEmpty().toList().filterNot { it == key } + key).takeLast(64).toSet()
        prefs.edit().putStringSet("origins", sites).apply()
    }

    fun forgetBrowser(context: Context, url: String) {
        val key = SecureWebsitePolicy.originKey(url) ?: return
        val prefs = preferences(context)
        prefs.edit().putStringSet("origins", prefs.getStringSet("origins", emptySet()).orEmpty() - key).apply()
    }

    // Only routing metadata is cleared here, never another browser's cookies.
    fun clearRouting(context: Context) { preferences(context).edit().clear().apply() }

    fun open(context: Context, url: String, toolbarColor: Int): Result<Unit> = runCatching {
        require(validPage(url)) { "Secure sign-in needs an HTTPS website address." }
        val prefs = preferences(context)
        val previous = prefs.getString("provider", null)
        val availablePrevious = previous?.let { CustomTabsClient.getPackageName(context, listOf(it), true) }
        val browser = availablePrevious
            ?: CustomTabsClient.getPackageName(context, listOf("com.android.chrome", "com.chrome.beta", "com.chrome.dev"))
            ?: error("Install or update Chrome or another browser that supports secure Custom Tabs.")
        if (previous != null && previous != browser) android.widget.Toast.makeText(context,
            "The previous browser is unavailable. You may need to sign in again in this browser.", android.widget.Toast.LENGTH_LONG).show()
        // An explicit provider avoids routing back into Hi Player when it is the default browser.
        val tab = CustomTabsIntent.Builder().setShowTitle(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
            .setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(toolbarColor).build())
            .build()
        tab.intent.setPackage(browser)
        tab.launchUrl(context, Uri.parse(url))
        prefs.edit().putString("provider", browser).apply()
    }
}
