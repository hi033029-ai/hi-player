package com.example.browser.gecko

import androidx.annotation.MainThread
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Match positions are one-based; total is -1 when Gecko has not counted all matches. */
internal data class GeckoFindResult(val found: Boolean, val current: Int, val total: Int)
internal interface GeckoFindable {
    fun find(query: String?, backwards: Boolean, completion: (GeckoFindResult?) -> Unit)
    fun clearFind()
}

/** Owns the find bar and ignores callbacks from old tabs, queries and closed sessions. */
internal class GeckoFindController(
    private val find: (Int, String?, Boolean, (GeckoFindResult?) -> Unit) -> Unit,
    private val clear: (Int) -> Unit,
) : AutoCloseable {
    var visible by mutableStateOf(false)
        private set
    var query by mutableStateOf("")
        private set
    var label by mutableStateOf("")
        private set
    private var tabId: Int? = null
    private var pageUrl = ""
    private var generation = 0L
    private var closed = false

    @MainThread fun open(tab: Int, url: String) {
        if (closed || url.isBlank()) return
        dismiss()
        tabId = tab; pageUrl = url; visible = true
    }
    @MainThread fun changeQuery(value: String) {
        if (!visible || closed) return
        query = value; label = ""; generation++
        val id = tabId ?: return
        if (value.isBlank()) { runCatching { clear(id) }; return }
        search(id, value, false)
    }
    @MainThread fun next(backwards: Boolean) {
        if (!visible || closed || query.isBlank()) return
        val id = tabId ?: return
        generation++
        search(id, null, backwards)
    }
    private fun search(id: Int, text: String?, backwards: Boolean) {
        val request = generation
        val completion: (GeckoFindResult?) -> Unit = result@{ result ->
            if (closed || !visible || tabId != id || request != generation) return@result
            label = when {
                result == null -> "Unavailable"
                !result.found -> "0/0"
                result.total < 0 -> "${result.current}/…"
                else -> "${result.current}/${result.total}"
            }
        }
        try { find(id, text, backwards, completion) } catch (_: Exception) { completion(null) }
    }
    @MainThread fun pageChanged(tab: Int, url: String, loading: Boolean) {
        if (visible && (tabId != tab || pageUrl != url || loading)) dismiss()
    }
    @MainThread fun dismiss() {
        val id = tabId
        generation++; visible = false; tabId = null; query = ""; label = ""; pageUrl = ""
        if (id != null) runCatching { clear(id) }
    }
    @MainThread override fun close() { if (!closed) { dismiss(); closed = true } }
}
