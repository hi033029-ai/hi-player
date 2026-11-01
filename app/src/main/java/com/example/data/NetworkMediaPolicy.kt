package com.example.data

import java.net.URI
import java.util.Locale

/** Server/folder addresses are configuration, never credential-bearing URLs. */
internal object NetworkMediaPolicy {
    fun server(raw: String): String {
        val uri = URI(raw.trim())
        require(uri.isAbsolute && uri.scheme.lowercase(Locale.ROOT) in setOf("http", "https") && !uri.host.isNullOrBlank())
        require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null)
        require(uri.port == -1 || uri.port in 1..65535)
        require(safePath(uri))
        return uri.toASCIIString().trimEnd('/')
    }
    private fun safePath(uri: URI): Boolean {
        val path = uri.path.orEmpty()
        // Avoid alias/normalization ambiguities before sending server credentials.
        return '\\' !in path && path.split('/').none { it == "." || it == ".." } &&
            !Regex("%(25|2f|5c)", RegexOption.IGNORE_CASE).containsMatchIn(uri.rawPath.orEmpty())
    }
    fun folder(raw: String) = server(raw) + "/"
    fun sameOrigin(first: String, second: String): Boolean = runCatching {
        val a = URI(first); val b = URI(second)
        fun port(uri: URI) = if (uri.port >= 0) uri.port else if (uri.scheme.equals("https", true)) 443 else 80
        a.scheme?.lowercase(Locale.ROOT) in setOf("http", "https") && !a.host.isNullOrBlank() &&
            a.scheme.equals(b.scheme, true) && a.host.equals(b.host, true) && port(a) == port(b) && a.rawUserInfo == null && b.rawUserInfo == null
    }.getOrDefault(false)
    fun within(root: String, target: String): Boolean = runCatching {
        val base = URI(folder(root)); val child = URI(target)
        val path = child.path.orEmpty()
        sameOrigin(root, target) && child.rawQuery == null && child.rawFragment == null &&
            safePath(child) &&
            (path.trimEnd('/') == base.path.trimEnd('/') || path.startsWith(base.path))
    }.getOrDefault(false)
}
