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

    fun open(context: Context, url: String, toolbarColor: Int): Result<Unit> = runCatching {
        require(validPage(url)) { "Secure sign-in needs an HTTPS website address." }
        val browser = CustomTabsClient.getPackageName(context, listOf("com.android.chrome", "com.chrome.beta", "com.chrome.dev"))
            ?: error("Install or update Chrome or another browser that supports secure Custom Tabs.")
        // An explicit provider avoids routing back into Hi Player when it is the default browser.
        val tab = CustomTabsIntent.Builder().setShowTitle(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
            .setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(toolbarColor).build())
            .build()
        tab.intent.setPackage(browser)
        tab.launchUrl(context, Uri.parse(url))
    }
}
