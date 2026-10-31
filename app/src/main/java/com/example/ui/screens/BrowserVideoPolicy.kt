package com.example.ui.screens

/** Web redirects and unsolicited events never authorize native playback; only user-started videos and taps do. */
internal object BrowserVideoPolicy {
    private val adDomains = setOf("doubleclick.net", "googlesyndication.com", "googleadservices.com", "imasdk.googleapis.com", "adnxs.com", "pubmatic.com", "rubiconproject.com", "adsrvr.org", "adform.net", "2mdn.net", "exoclick.com", "trafficjunky.net", "juicyads.com")
    fun isAdUrl(url: String): Boolean = runCatching {
        val uri = java.net.URI(url)
        val host = uri.host?.lowercase().orEmpty()
        val path = uri.path?.lowercase().orEmpty()
        val labels = host.split('.')
        adDomains.any { host == it || host.endsWith(".$it") } || labels.any { it in setOf("ad", "ads", "adserver", "adservice", "adtrack") } ||
            Regex("(^|[/_.-])(ad|ads|advert|advertisement|preroll|midroll|postroll|vast|vpaid)([/_.-]|$)").containsMatchIn(path) ||
            uri.query.orEmpty().split('&').any { it.substringBefore('=').lowercase() in setOf("adtag", "ad_tag", "adunit", "ad_unit", "vast", "vpaid") }
    }.getOrDefault(true)
    fun offerNavigation(hasGesture: Boolean, redirect: Boolean, ad: Boolean) = hasGesture && !redirect && !ad
}
