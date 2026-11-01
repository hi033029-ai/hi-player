package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.JellyfinMediaSourceClient
import com.example.data.WebDavMediaSourceClient
import com.example.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class NetworkFolder(val reference: String?, val title: String)
internal data class NetworkBrowserState(val kind: String = "", val path: List<NetworkFolder> = emptyList(),
    val entries: List<NetworkMediaEntry> = emptyList(), val busy: Boolean = false, val message: String? = null,
    val nextOffset: Int? = null, val note: String? = null)

/** Session memory only: no folder addresses, device tokens or headers in saved-state Bundles. */
internal class NetworkMediaBrowserViewModel : ViewModel() {
    private val _state = MutableStateFlow(NetworkBrowserState())
    val state = _state.asStateFlow()
    private var jellyfin: JellyfinConnection? = null
    private var webDav: WebDavConnection? = null
    private var request: Job? = null
    private var generation = 0L
    private val cache = linkedMapOf<String, NetworkMediaPage>()
    private val scroll = mutableMapOf<String, Pair<Int, Int>>()
    fun scrollPosition(key: String) = scroll[key] ?: (0 to 0)
    fun saveScroll(key: String, index: Int, offset: Int) { scroll[key] = index to offset; while (scroll.size > 20) scroll.remove(scroll.keys.first()) }
    fun open(kind: String, jellyfin: JellyfinConnection?, webDav: WebDavConnection?) {
        if (_state.value.kind == kind && this.jellyfin == jellyfin && this.webDav == webDav && _state.value.path.isNotEmpty()) {
            if (!_state.value.busy && _state.value.entries.isEmpty() && cache[key()] == null) load()
            return
        }
        stop(); cache.clear(); scroll.clear()
        this.jellyfin = jellyfin; this.webDav = webDav
        val root = if (kind == "jellyfin") NetworkFolder(null, jellyfin?.serverName ?: "Jellyfin") else NetworkFolder(webDav?.folderUrl, webDav?.displayName ?: "WebDAV")
        _state.value = NetworkBrowserState(kind, listOf(root))
        load()
    }
    fun enter(entry: NetworkMediaEntry) {
        if (_state.value.busy || !entry.folder || entry !in _state.value.entries) return
        _state.value = _state.value.copy(path = _state.value.path + NetworkFolder(entry.reference, entry.title), entries = emptyList())
        showCachedOrLoad()
    }
    fun back() {
        if (_state.value.path.size <= 1) return
        stop()
        _state.value = _state.value.copy(path = _state.value.path.dropLast(1), entries = emptyList())
        showCachedOrLoad()
    }
    private fun key() = _state.value.kind + ":" + _state.value.path.lastOrNull()?.reference.orEmpty()
    private fun showCachedOrLoad() {
        val cached = cache[key()]
        if (cached == null) load() else _state.value = _state.value.copy(entries = cached.entries, nextOffset = cached.nextOffset, note = cached.note, busy = false, message = null)
    }
    fun clear() { stop(); cache.clear(); scroll.clear(); jellyfin = null; webDav = null; _state.value = NetworkBrowserState() }
    fun stop() { request?.cancel(); generation++; _state.value = _state.value.copy(busy = false) }
    fun load(more: Boolean = false) {
        if (_state.value.path.isEmpty() || _state.value.busy || (more && _state.value.nextOffset == null)) return
        request?.cancel()
        val ticket = ++generation
        val before = _state.value
        val folder = before.path.last().reference
        val offset = if (more) before.nextOffset ?: 0 else 0
        val key = key()
        _state.value = before.copy(busy = true, message = null)
        request = viewModelScope.launch {
            try {
                val page = if (before.kind == "jellyfin") JellyfinMediaSourceClient.browse(requireNotNull(jellyfin), folder, offset)
                    else WebDavMediaSourceClient.browse(requireNotNull(webDav), requireNotNull(folder))
                ensureActive()
                if (ticket != generation) return@launch
                val combined = if (more) (before.entries + page.entries).distinctBy { it.id } else page.entries
                val result = page.copy(entries = combined)
                cache[key] = result
                while (cache.size > 20) { val first = cache.keys.first(); cache.remove(first); scroll.remove(first) }
                _state.value = _state.value.copy(entries = combined, nextOffset = page.nextOffset, note = page.note)
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { if (ticket == generation) _state.value = _state.value.copy(message = "Couldn't load this library. Check the server connection or reconnect from Manage, then try again.")
            } finally { if (ticket == generation) _state.value = _state.value.copy(busy = false) }
        }
    }
}
