package com.example.browser.gecko

import com.example.ui.screens.OrbitBrowserEntry
import com.example.ui.screens.OrbitSavedTab
import org.junit.Assert.*
import org.junit.Test

class GeckoCoordinatorPopupTest {
    private class Store : GeckoTabStore {
        var saved = emptyList<OrbitSavedTab>()
        override fun readTabs() = emptyList<OrbitSavedTab>()
        override fun readActiveId() = -1
        override fun writeTabs(tabs: List<OrbitSavedTab>, activeId: Int) { saved = tabs }
        override fun recordVisit(entry: OrbitBrowserEntry) = Unit
        override fun clearHistory(completion: (Boolean) -> Unit) { completion(true) }
        override fun removeHistory(url: String, completion: (Boolean) -> Unit) { completion(true) }
    }
    private class Session(val callback: (GeckoTabSnapshot) -> Unit) : GeckoManagedSession<Any> {
        override fun attach(view: Any) = Unit
        override fun detach() = Unit
        override fun load(url: String) = Unit
        override fun reload() = Unit
        override fun stop() = Unit
        override fun back() = Unit
        override fun forward() = Unit
        override fun exitFullScreen() = Unit
        override fun setDesktop(enabled: Boolean) = Unit
        override fun close() = Unit
    }
    @Test fun privateChildIsReservedInBackgroundAndSelectedOnlyWhenOpened() {
        val store = Store()
        val owner = GeckoBrowserCoordinator<Any>(store) { _, changed -> Session(changed) }
        assertTrue(owner.open(true))
        val opener = owner.activeId
        lateinit var child: GeckoTabRegistry.Tab
        val session = owner.reservePopup(opener) { tab, changed -> child = tab; Session(changed) } as Session
        assertTrue(child.privateTab)
        assertTrue(owner.isPopup(child.id))
        assertTrue(owner.isPopupSession(child.id, session))
        assertEquals(opener, owner.activeId)
        assertFalse(store.saved.any { it.id == child.id })
        session.callback(GeckoTabSnapshot(child.id, true, "https://example.org", loading = true))
        assertEquals(child.id, owner.activeId)
        assertTrue(owner.isPopup(child.id))
        assertTrue(owner.closeTab(child.id))
        assertFalse(owner.isPopup(child.id))
        assertFalse(owner.isPopupSession(child.id, session))
        owner.close()
    }
    @Test fun popupDoesNotStealFocusAfterUserChangesTabs() {
        val owner = GeckoBrowserCoordinator<Any>(Store()) { _, changed -> Session(changed) }
        val opener = owner.activeId
        lateinit var child: GeckoTabRegistry.Tab
        val session = owner.reservePopup(opener) { tab, changed -> child = tab; Session(changed) } as Session
        assertTrue(owner.open(false))
        val selected = owner.activeId
        session.callback(GeckoTabSnapshot(child.id, false, "https://example.org", loading = true))
        assertEquals(selected, owner.activeId)
        owner.close()
    }
    @Test fun factoryFailureDoesNotLeaveAGhostTab() {
        val owner = GeckoBrowserCoordinator<Any>(Store()) { _, changed -> Session(changed) }
        try { owner.reservePopup(owner.activeId) { _, _ -> error("creation failed") }; fail() } catch (_: IllegalStateException) { }
        assertEquals(1, owner.tabs.size)
        assertEquals(setOf(owner.activeId), owner.snapshots.keys)
        owner.close()
    }
}
