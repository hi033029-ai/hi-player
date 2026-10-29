package com.example.cast

import java.net.URI
import java.net.URLDecoder

internal object CastStreamPolicy {
    fun rejection(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return "Enter a valid media URL."
        if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank())
            return "Casting needs an HTTP or HTTPS media link that the TV can reach."
        if (uri.rawUserInfo != null) return "URLs containing account credentials cannot be sent to a TV."
        val host = uri.host.lowercase()
        if (host == "localhost" || host == "127.0.0.1" || host == "[::1]" || host == "::1")
            return "The TV cannot access a media server on this phone's loopback address."
        if (host == "www.googleapis.com" && uri.path.orEmpty().startsWith("/drive/"))
            return "Account-protected Drive files play in Hi Player, not on the public Cast receiver."
        val protectedKeys = setOf("access_token", "id_token", "refresh_token", "authorization")
        val keys = uri.rawQuery.orEmpty().split('&').map {
            runCatching { URLDecoder.decode(it.substringBefore('='), "UTF-8").lowercase() }.getOrDefault("")
        }
        if (keys.any { it in protectedKeys }) return "Account tokens cannot be included in a Cast URL."
        return null
    }
}
