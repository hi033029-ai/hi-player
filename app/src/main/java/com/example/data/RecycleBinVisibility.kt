package com.example.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** UI visibility only: never represents a completed file operation or grants storage access. */
object RecycleBinVisibility {
    private val pending = mutableSetOf<String>()
    private val committed = mutableSetOf<String>()
    private val _hiddenPaths = MutableStateFlow<Set<String>>(emptySet())
    val hiddenPaths = _hiddenPaths.asStateFlow()

    @Synchronized fun begin(paths: Set<String>) { pending.addAll(paths); publish() }
    @Synchronized fun finish(requested: Set<String>, moved: Set<String>) {
        committed.addAll(moved)
        pending.removeAll(requested)
        publish()
    }
    @Synchronized fun restored(path: String) { committed.remove(path); publish() }
    @Synchronized fun loadCommitted(paths: Set<String>) { committed.addAll(paths); publish() }
    private fun publish() { _hiddenPaths.value = (pending + committed).toSet() }

    fun isHidden(path: String, hidden: Set<String>): Boolean = path.isNotBlank() &&
        hidden.any { it == path || path.startsWith(it.trimEnd('/') + "/") }
}
