package com.example.browser.gecko

import com.example.ui.screens.OrbitSavedTab
import java.net.URI
import java.util.Locale

/** Adapter to the existing browser store; it excludes private tabs and URLs with known credential parameters. */
internal object GeckoTabPersistence {
    data class DisplayState(val url: String, val title: String, val desktop: Boolean)
    data class Restored(
        val tabs: List<GeckoTabRegistry.Tab>,
        val activeId: Int?,
        val states: Map<Int, DisplayState>,
    )
    data class Saved(val tabs: List<OrbitSavedTab>, val activeId: Int)

    fun restore(saved: List<OrbitSavedTab>, activeId: Int): Restored {
        val entries = saved.asSequence().filter { it.id > 0 }.distinctBy { it.id }
            .take(GeckoTabRegistry.MAX_TABS).toList()
        val ids = entries.map { it.id }.toSet()
        return Restored(
            tabs = entries.map { GeckoTabRegistry.Tab(it.id, false, it.openerTabId?.takeIf { id -> id in ids && id != it.id }) },
            activeId = activeId.takeIf { it in ids },
            states = entries.associate { it.id to DisplayState(safeUrl(it.url), safeTitle(it.title), it.desktop) },
        )
    }

    fun save(tabs: List<GeckoTabRegistry.Tab>, activeId: Int, states: Map<Int, DisplayState>): Saved {
        val regular = tabs.asSequence().filter { !it.privateTab && it.id > 0 }.distinctBy { it.id }
            .take(GeckoTabRegistry.MAX_TABS).toList()
        val ids = regular.map { it.id }.toSet()
        val entries = regular.map { tab ->
            val state = states[tab.id] ?: DisplayState("", "New tab", false)
            OrbitSavedTab(tab.id, safeUrl(state.url), safeTitle(state.title), state.desktop,
                tab.openerId?.takeIf { it in ids && it != tab.id })
        }
        // The store expects an integer. -1 means no restored selection if only private tabs remain.
        return Saved(entries, activeId.takeIf { it in ids } ?: -1)
    }

    private fun safeTitle(title: String): String = title.take(500).ifBlank { "New tab" }

    private fun safeUrl(url: String): String {
        if (url.isBlank()) return ""
        if (url.length > 4096) return ""
        val uri = runCatching { URI(url) }.getOrNull() ?: return ""
        if (uri.scheme?.lowercase(Locale.ROOT) !in setOf("http", "https") || uri.host.isNullOrBlank() || uri.userInfo != null) return ""
        // Authentication links and fragments may contain bearer tokens or authorization codes.
        val sensitive = setOf("access_token", "id_token", "refresh_token", "token", "code", "password", "passwd", "client_secret", "signature", "sig", "x-amz-signature", "x-goog-signature")
        fun hasSensitiveParameter(value: String?): Boolean = value.orEmpty().split('&', ';').any { part ->
            val name = part.substringBefore('=').lowercase(Locale.ROOT)
            // Percent-encoded keys are refused rather than incorrectly treated as safe.
            '%' in name || name in sensitive
        }
        if (hasSensitiveParameter(uri.rawQuery) || hasSensitiveParameter(uri.rawFragment)) return ""
        return url
    }
}
