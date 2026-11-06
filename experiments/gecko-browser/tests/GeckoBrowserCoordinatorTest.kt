package com.example.browser.gecko

import com.example.ui.screens.OrbitSavedTab
import org.junit.Assert.*
import org.junit.Test

class GeckoBrowserCoordinatorTest {
    private class Store(var tabs: List<OrbitSavedTab> = emptyList(), var active: Int = -1) : GeckoTabStore {
        var writes = 0
        override fun readTabs() = tabs
        override fun readActiveId() = active
        override fun writeTabs(tabs: List<OrbitSavedTab>, activeId: Int) { this.tabs = tabs; active = activeId; writes++ }
    }
    private class Fake(private val tab: GeckoTabRegistry.Tab, val callback: (GeckoTabSnapshot) -> Unit) : GeckoManagedSession<Any> {
        val urls = mutableListOf<String>()
        var desktopEnabled = false
        var closed = false
        override fun attach(view: Any) = Unit
        override fun detach() = Unit
        override fun load(url: String) { urls += url; callback(GeckoTabSnapshot(tab.id, tab.privateTab, url)) }
        override fun reload() = Unit
        override fun stop() = Unit
        override fun back() = Unit
        override fun forward() = Unit
        override fun setDesktop(enabled: Boolean) { desktopEnabled = enabled }
        override fun close() { closed = true }
    }

    @Test fun restoresTabsAndPersistsRegularUpdatesWithoutPrivateUrls() {
        val store = Store(listOf(OrbitSavedTab(4, "https://example.org", "Example", true)), 4)
        val sessions = mutableMapOf<Int, Fake>()
        val coordinator = GeckoBrowserCoordinator<Any>(store) { tab, callback -> Fake(tab, callback).also { sessions[tab.id] = it } }
        assertEquals(4, coordinator.activeId)
        assertTrue(sessions.getValue(4).desktopEnabled)
        assertEquals(listOf("https://example.org"), sessions.getValue(4).urls)
        assertTrue(coordinator.open(true))
        val privateId = coordinator.activeId
        coordinator.load("https://private.example/secret")
        assertEquals(listOf(4), store.tabs.map { it.id })
        assertEquals(-1, store.active)
        assertFalse(store.tabs.toString().contains("private.example"))
        assertTrue(coordinator.select(4))
        coordinator.setDesktop(false)
        coordinator.load("https://example.org/other")
        assertEquals(4, store.active)
        assertEquals("https://example.org/other", store.tabs.single().url)
        assertFalse(store.tabs.single().desktop)
        assertTrue(coordinator.closeTab(privateId))
        coordinator.close()
        assertTrue(sessions.values.all { it.closed })
    }

    @Test fun ignoresLateCallbacksAndDoesNotPersistProgressOnlyChanges() {
        val store = Store()
        val sessions = mutableMapOf<Int, Fake>()
        val coordinator = GeckoBrowserCoordinator<Any>(store) { tab, callback -> Fake(tab, callback).also { sessions[tab.id] = it } }
        val initialWrites = store.writes
        sessions.getValue(1).callback(GeckoTabSnapshot(1, false, loading = true, progress = 30))
        assertEquals(initialWrites, store.writes)
        assertTrue(coordinator.open(true))
        val id = coordinator.activeId
        val old = sessions.getValue(id)
        assertTrue(coordinator.closeTab(id))
        old.callback(GeckoTabSnapshot(id, true, "https://private.example"))
        assertFalse(coordinator.snapshots.containsKey(id))
        assertFalse(store.tabs.toString().contains("private.example"))
        coordinator.close()
    }

    @Test fun homeReplacesSessionAndIgnoresStaleCallbacks() {
        val store = Store()
        val sessions = mutableListOf<Fake>()
        val coordinator = GeckoBrowserCoordinator<Any>(store) { tab, callback -> Fake(tab, callback).also(sessions::add) }
        coordinator.setDesktop(true)
        coordinator.load("https://example.org/page")
        val old = sessions.single()
        coordinator.home()
        assertTrue(old.closed)
        assertEquals(2, sessions.size)
        assertTrue(sessions.last().desktopEnabled)
        assertEquals("", coordinator.activeSnapshot.url)
        old.callback(GeckoTabSnapshot(1, false, "https://stale.example"))
        assertEquals("", coordinator.activeSnapshot.url)
        assertEquals("", store.tabs.single().url)
        coordinator.close()
    }

    @Test fun failedHomeKeepsOldSessionAndCallbacks() {
        val store = Store()
        var calls = 0
        lateinit var first: Fake
        val coordinator = GeckoBrowserCoordinator<Any>(store) { tab, callback ->
            if (++calls > 1) error("creation failed")
            Fake(tab, callback).also { first = it }
        }
        coordinator.load("https://example.org")
        try { coordinator.home(); fail("expected failure") } catch (_: IllegalStateException) { }
        assertFalse(first.closed)
        first.callback(GeckoTabSnapshot(1, false, "https://example.org/next"))
        assertEquals("https://example.org/next", coordinator.activeSnapshot.url)
        assertEquals("https://example.org/next", store.tabs.single().url)
        coordinator.close()
    }

    @Test fun sensitiveUrlIsNotWrittenToStore() {
        val store = Store()
        val coordinator = GeckoBrowserCoordinator<Any>(store) { tab, callback -> Fake(tab, callback) }
        coordinator.load("https://example.org/?access_token=secret")
        assertEquals("", store.tabs.single().url)
        coordinator.close()
    }
}
