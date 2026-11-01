package com.example.model

/** Session-only API data. Neither authenticated requests nor folder paths enter saved state. */
data class NetworkMediaEntry(val id: String, val title: String, val folder: Boolean, val reference: String,
    val playable: RemoteMediaMatch? = null, val subtitle: String = "")
data class NetworkMediaPage(val entries: List<NetworkMediaEntry>, val nextOffset: Int? = null, val note: String? = null)
internal data class JellyfinQuickSession(val baseUrl: String, val deviceId: String, val code: String, val secret: String)
