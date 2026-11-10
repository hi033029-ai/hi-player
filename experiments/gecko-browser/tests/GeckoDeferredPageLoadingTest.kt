package com.example.browser.gecko

import org.junit.Assert.*
import org.junit.Test

/** Source coverage added for the next explicitly approved validation run. */
class GeckoDeferredPageLoadingTest {
    private class Session : GeckoManagedSession<Any> {
        val loads = mutableListOf<String>()
        var closed = false
        override fun attach(view: Any) {}
        override fun detach() {}
        override fun load(url: String) { loads += url }
        override fun reload() {}
        override fun stop() {}
        override fun back() {}
        override fun forward() {}
        override fun exitFullScreen() {}
        override fun purgeHistory() {}
        override fun setDesktop(enabled: Boolean) {}
        override fun close() { closed = true }
    }
    private val tabs = listOf(GeckoTabRegistry.Tab(1, false, null), GeckoTabRegistry.Tab(2, false, null))
    private val states = mapOf(
        1 to GeckoTabPersistence.DisplayState("https://example.com/one", "One", false),
        2 to GeckoTabPersistence.DisplayState("https://example.com/two", "Two", false),
    )
    @Test fun restoredBackgroundPageWaitsForSelectionAndLoadsOnlyOnce() {
        val sessions = mutableMapOf<Int, Session>()
        val controller = GeckoTabController<Any>(tabs, 2, states) { tab -> Session().also { sessions[tab.id] = it } }
        assertTrue(sessions.getValue(1).loads.isEmpty())
        assertEquals(listOf("https://example.com/two"), sessions.getValue(2).loads)
        assertTrue(controller.select(1))
        assertEquals(listOf("https://example.com/one"), sessions.getValue(1).loads)
        controller.select(2); controller.select(1)
        assertEquals(1, sessions.getValue(1).loads.size)
        controller.close()
    }
    @Test fun closedBackgroundPageIsNeverFetched() {
        val sessions = mutableMapOf<Int, Session>()
        val controller = GeckoTabController<Any>(tabs, 2, states) { tab -> Session().also { sessions[tab.id] = it } }
        assertTrue(controller.closeTab(1))
        assertTrue(sessions.getValue(1).closed)
        assertTrue(sessions.getValue(1).loads.isEmpty())
        assertFalse(controller.select(1))
        controller.close()
    }
    @Test fun closingActiveTabLoadsItsDeferredSuccessor() {
        val sessions = mutableMapOf<Int, Session>()
        val controller = GeckoTabController<Any>(tabs, 2, states) { tab -> Session().also { sessions[tab.id] = it } }
        controller.closeTab(2)
        assertEquals(1, controller.activeId)
        assertEquals(listOf("https://example.com/one"), sessions.getValue(1).loads)
        controller.close()
    }
}
