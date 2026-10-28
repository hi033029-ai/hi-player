package com.example.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Finish the current scan; coalesce bursts into one follow-up, never cancel useful work. */
internal class CoalescingRefresh(scope: CoroutineScope, private val scan: suspend () -> Unit) {
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private val worker = scope.launch {
        for (request in requests) {
            delay(150)
            scan()
        }
    }
    fun request() { requests.trySend(Unit) }
    fun close() { requests.close(); worker.cancel() }
}
