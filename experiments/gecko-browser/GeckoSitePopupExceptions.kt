package com.example.browser.gecko

import java.net.URI
import java.util.Locale
import java.util.WeakHashMap
import org.mozilla.geckoview.GeckoSession

/** A temporary, exact-origin exception scoped to one tab session; never persisted. */
internal class GeckoSitePopupExceptions<K : Any> {
    private val exceptions = WeakHashMap<K, String>()

    fun origin(url: String): String? = runCatching {
        val uri = URI(url)
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return@runCatching null
        val host = uri.host?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return@runCatching null
        if (scheme !in setOf("http", "https") || host.isBlank() || uri.userInfo != null ||
            (uri.port != -1 && uri.port !in 1..65535)) return@runCatching null
        val port = if (uri.port == -1) { if (scheme == "https") 443 else 80 } else uri.port
        "$scheme://$host:$port"
    }.getOrNull()

    fun allow(key: K, url: String): Boolean {
        val origin = origin(url) ?: return false
        exceptions[key] = origin
        return true
    }
    fun isAllowed(key: K?, url: String): Boolean = key != null && origin(url)?.let { exceptions[key] == it } == true
    fun revoke(key: K) { exceptions.remove(key) }
}

internal val geckoPopupExceptions = GeckoSitePopupExceptions<GeckoSession>()
