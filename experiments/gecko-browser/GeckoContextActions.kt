package com.example.browser.gecko

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.net.URI

/** Long-press actions are explicit, tab-scoped and never persist the selected address. */
internal class GeckoContextActions(
    private val activeTab: () -> Pair<Int, Boolean>?,
    private val openTab: (Boolean, String) -> Boolean,
    private val copy: (String) -> Boolean,
    private val share: (String) -> Boolean,
) {
    internal data class Request(val tabId: Int, val privateTab: Boolean, val url: String, val image: Boolean)
    var request by mutableStateOf<Request?>(null)
        private set
    var failed by mutableStateOf(false)
        private set

    fun offer(tabId: Int, linkUrl: String?, imageUrl: String?) {
        val (activeId, privateTab) = activeTab() ?: return
        if (activeId != tabId) return
        val validLink = linkUrl?.takeIf(::isSafeWebUrl)
        val validImage = imageUrl?.takeIf(::isSafeWebUrl)
        val url = validLink ?: validImage ?: return
        request = Request(tabId, privateTab, url, validLink == null)
        failed = false
    }

    fun dismiss() { request = null }
    fun dismissFailure() { failed = false }
    fun open() = perform { openTab(it.privateTab, it.url) }
    fun copy() = perform { copy(it.url) }
    fun share() = perform { share(it.url) }

    private fun perform(action: (Request) -> Boolean) {
        val pending = request ?: return
        if (activeTab() != (pending.tabId to pending.privateTab)) { dismiss(); return }
        request = null
        failed = !runCatching { action(pending) }.getOrDefault(false)
    }
    private fun isSafeWebUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        (uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) &&
            !uri.host.isNullOrBlank() && uri.userInfo == null && (uri.port == -1 || uri.port in 1..65535)
    }.getOrDefault(false)
}
