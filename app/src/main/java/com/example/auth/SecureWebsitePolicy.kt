package com.example.auth

import java.net.URI
import java.util.Locale

/** Routing preferences identify HTTPS origins, never an account or authenticated session. */
internal object SecureWebsitePolicy {
    fun originKey(url: String): String? = runCatching {
        val uri = URI(url)
        if (!uri.scheme.equals("https", true) || uri.userInfo != null) return null
        val host = uri.host?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() } ?: return null
        if (uri.port != -1 && uri.port !in 1..65535) return null
        val authority = if (':' in host && !host.startsWith("[")) "[$host]" else host
        "https://$authority" + if (uri.port == -1 || uri.port == 443) "" else ":${uri.port}"
    }.getOrNull()
}
