package com.example.viewmodel

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoalescingRefreshTest {
    @Test fun continuousEventsDoNotStarveAnInFlightScan() = runTest {
        var scans = 0
        val refresh = CoalescingRefresh(backgroundScope) { delay(300); scans++ }
        val producer = launch { repeat(30) { refresh.request(); delay(40) } }
        advanceTimeBy(1000); runCurrent()
        assertTrue("At least one scan must finish while events are still arriving", scans > 0)
        producer.join(); refresh.close()
    }
    @Test fun burstIsCoalescedWithoutConcurrentScans() = runTest {
        var inFlight = 0; var maximum = 0; var scans = 0
        val refresh = CoalescingRefresh(backgroundScope) {
            inFlight++; maximum = maxOf(maximum, inFlight); delay(300); scans++; inFlight--
        }
        repeat(100) { refresh.request() }
        advanceTimeBy(1000); runCurrent()
        assertEquals(1, maximum); assertEquals(1, scans)
        refresh.close()
    }
    @Test fun requestDuringScanRunsOneFollowup() = runTest {
        var scans = 0
        val refresh = CoalescingRefresh(backgroundScope) { delay(300); scans++ }
        refresh.request(); advanceTimeBy(200); runCurrent()
        repeat(10) { refresh.request() }
        advanceTimeBy(1000); runCurrent()
        assertEquals(2, scans)
        refresh.close()
    }
}
