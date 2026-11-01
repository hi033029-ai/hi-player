package com.example.player

import android.net.Uri
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Holds request headers in memory only while remote media is used. Player
 * history stores the clean URL, never a Basic credential or Jellyfin token.
 */
object RemoteStreamHeadersRegistry {
    private data class HeaderScope(
        val baseUri: Uri,
        val headers: Map<String, String>
    )

    private val scopes = CopyOnWriteArrayList<HeaderScope>()

    fun register(baseUrl: String, headers: Map<String, String>) {
        if (headers.isEmpty()) return
        val baseUri = runCatching { Uri.parse(baseUrl.trimEnd('/')) }.getOrNull() ?: return
        if (baseUri.scheme?.lowercase() !in setOf("http", "https") || baseUri.host.isNullOrBlank() || baseUri.userInfo != null) return
        if (baseUri.path.orEmpty().split('/').any { it == "." || it == ".." } || '\\' in baseUri.path.orEmpty()) return
        scopes.removeAll { it.baseUri.toString() == baseUri.toString() }
        scopes += HeaderScope(baseUri, headers.toMap())
    }

    fun headersFor(uri: Uri): Map<String, String> = scopes
        .asReversed()
        .firstOrNull { scope -> scope.matches(uri) }
        ?.headers
        .orEmpty()

    fun clear(baseUrl: String) {
        val root = runCatching { Uri.parse(baseUrl.trimEnd('/')) }.getOrNull() ?: return
        // Also revoke legacy per-file scopes underneath this server/folder.
        val revoked = HeaderScope(root, emptyMap())
        scopes.removeAll { revoked.matches(it.baseUri) }
    }

    private fun HeaderScope.matches(target: Uri): Boolean {
        if (target.userInfo != null || target.path.orEmpty().split('/').any { it == "." || it == ".." } || '\\' in target.path.orEmpty()) return false
        if (!baseUri.scheme.equals(target.scheme, ignoreCase = true)) return false
        if (!baseUri.host.equals(target.host, ignoreCase = true)) return false
        if (effectivePort(baseUri) != effectivePort(target)) return false
        val basePath = baseUri.path.orEmpty().trimEnd('/')
        val targetPath = target.path.orEmpty()
        return basePath.isBlank() || targetPath == basePath || targetPath.startsWith("$basePath/")
    }

    private fun effectivePort(uri: Uri): Int = when {
        uri.port != -1 -> uri.port
        uri.scheme.equals("https", ignoreCase = true) -> 443
        else -> 80
    }
}
