package com.example.connections

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class StorageFolder(val id: String, val name: String)
internal data class StorageBrowserState(val kind: String = "", val session: String? = null, val path: List<StorageFolder> = emptyList(),
    val files: List<StorageFile> = emptyList(), val next: String? = null, val note: String? = null, val busy: Boolean = false, val message: String? = null)
internal class StorageBrowserViewModel : ViewModel() {
    private val _state = MutableStateFlow(StorageBrowserState())
    val state = _state.asStateFlow()
    private var request: Job? = null; private var generation = 0L
    private val cache = linkedMapOf<String, StoragePage>()
    fun open(kind: String, session: String?, label: String) {
        if (_state.value.kind == kind && _state.value.session == session && _state.value.path.isNotEmpty()) {
            if (session != null && !_state.value.busy && cache[key()] == null) load()
            return
        }
        stop(); cache.clear(); _state.value = StorageBrowserState(kind, session, listOf(StorageFolder(if (kind == "smb") "" else "root", label)))
        if (session != null) load()
    }
    fun enter(file: StorageFile) {
        if (!file.folder || _state.value.busy || file !in _state.value.files) return
        _state.value = _state.value.copy(path = _state.value.path + StorageFolder(file.id, file.name), files = emptyList(), next = null, message = null, note = null); cached()
    }
    fun back() { if (_state.value.path.size <= 1) return; stop(); _state.value = _state.value.copy(path = _state.value.path.dropLast(1), files = emptyList(), next = null, message = null, note = null); cached() }
    private fun key() = _state.value.path.lastOrNull()?.id.orEmpty()
    private fun cached() { val page = cache[key()]; if (page == null) load() else _state.value = _state.value.copy(files = page.files, next = page.next, note = page.note, busy = false, message = null) }
    fun stop() { request?.cancel(); generation++; _state.value = _state.value.copy(busy = false) }
    fun clear() { stop(); cache.clear(); _state.value = StorageBrowserState() }
    fun load(more: Boolean = false) {
        val old = _state.value; val session = old.session ?: return
        if (old.busy || (more && old.next == null)) return
        val ticket = ++generation; _state.value = old.copy(busy = true, message = null)
        request = viewModelScope.launch {
            try {
                val page = withContext(Dispatchers.IO) {
                    if (old.kind == "smb") SmbStorage.list(session, old.path.last().id)
                    else CloudFiles.list(CloudProvider.entries.first { it.key == old.kind }, session, old.path.last().id, if (more) old.next else null)
                }
                ensureActive(); if (ticket != generation) return@launch
                val files = (if (more) old.files + page.files else page.files).distinctBy { it.id }
                val result = page.copy(files = files.take(2000), next = if (files.size >= 2000) null else page.next,
                    note = if (files.size >= 2000) "Showing up to 2,000 entries. Use smaller folders to see more." else page.note)
                cache[key()] = result; while (cache.size > 20) cache.remove(cache.keys.first())
                _state.value = _state.value.copy(files = result.files, next = result.next, note = result.note)
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { if (ticket == generation) _state.value = _state.value.copy(message = "Couldn't load files. Check your network and access, or reconnect this source.")
            } finally { if (ticket == generation) _state.value = _state.value.copy(busy = false) }
        }
    }
    fun media(file: StorageFile): android.net.Uri {
        val state = _state.value; check(!state.busy && file in state.files && file.playable)
        val session = checkNotNull(state.session)
        return if (state.kind == "smb") { SmbStorage.requireActive(session); SmbStorage.mediaUri(session, file.id) }
        else { val p = CloudProvider.entries.first { it.key == state.kind }; PersonalCloud.requireSession(p, session); CloudFiles.mediaUri(p, session, file.id) }
    }
}

/** Setup drafts are VM memory, never rememberSaveable or a SavedStateHandle. */
internal class SmbSetupViewModel : ViewModel() {
    val host = mutableStateOf(""); val share = mutableStateOf(""); val username = mutableStateOf("")
    val password = mutableStateOf(""); val domain = mutableStateOf(""); val encrypted = mutableStateOf(true)
    val busy = mutableStateOf(false); val message = mutableStateOf<String?>(null)
    private var job: Job? = null; private var generation = 0L
    fun connect(complete: () -> Unit) {
        if (busy.value) return
        val config = SmbCredentials(host.value.trim(), share.value.trim(), username.value.trim(), password.value.toCharArray(), domain.value.trim(), encrypted.value)
        val ticket = ++generation; busy.value = true; message.value = "Checking the shared folder…"
        job = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { SmbStorage.connect(config).use { check(it.share.folderExists("")) } }
                ensureActive(); if (ticket != generation) return@launch
                SmbStorage.activate(config); password.value = ""; message.value = null; complete()
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { if (ticket == generation) message.value = "Couldn't connect. Check the server, share name and credentials. Keep encryption on unless this is a trusted network with an older server."
            } finally { config.password.fill('\u0000'); if (ticket == generation) busy.value = false }
        }
    }
    fun stop() { generation++; job?.cancel(); busy.value = false }
    fun discard() { stop(); host.value = ""; share.value = ""; username.value = ""; password.value = ""; domain.value = ""; message.value = null; encrypted.value = true }
    override fun onCleared() { discard(); super.onCleared() }
}
