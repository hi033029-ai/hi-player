package com.example.browser.gecko

/** A popup must correspond to a recent new-window navigation the host has allowed. */
internal class GeckoPopupGate(private val clock: () -> Long) {
    private data class Ticket(val uri: String, val time: Long)
    private val pending = mutableListOf<Ticket>()

    fun permit(uri: String) {
        val now = clock()
        pending.removeAll { now - it.time !in 0..5_000 }
        if (pending.size >= 8) pending.removeAt(0)
        pending.add(Ticket(uri.ifEmpty { "about:blank" }, now))
    }

    fun clear() { pending.clear() }

    fun consume(uri: String): Boolean {
        val now = clock()
        pending.removeAll { now - it.time !in 0..5_000 }
        val index = pending.indexOfFirst { it.uri == uri.ifEmpty { "about:blank" } }
        if (index == -1) return false
        pending.removeAt(index)
        return true
    }
}
