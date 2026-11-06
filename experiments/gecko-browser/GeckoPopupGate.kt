package com.example.browser.gecko

/** A popup must correspond to a recent new-window navigation the host has allowed. */
internal class GeckoPopupGate(private val clock: () -> Long) {
    private data class Ticket(val uri: String, val time: Long)
    private var pending: Ticket? = null

    fun permit(uri: String) { pending = Ticket(uri, clock()) }
    fun clear() { pending = null }
    fun consume(uri: String): Boolean {
        val ticket = pending ?: return false
        pending = null
        val age = clock() - ticket.time
        return ticket.uri == uri && age in 0..5_000
    }
}
