package com.example.browser.gecko

import org.junit.Assert.*
import org.junit.Test

class GeckoTabControllerTest {
    private class FakeSession : GeckoManagedSession<Any> {
        var view: Any? = null
        var closeCount = 0
        val urls = mutableListOf<String>()
        override fun attach(view: Any) { check(this.view == null); this.view = view }
        override fun detach() { view = null }
        override fun load(url: String) { urls += url }
        override fun close() { closeCount++ }
    }

    @Test fun switchingKeepsOneAttachedSessionAndSeparateHistory() {
        val sessions = mutableMapOf<Int, FakeSession>()
        val controller = GeckoTabController<Any> { tab -> FakeSession().also { sessions[tab.id] = it } }
        val view = Any()
        controller.attach(view)
        controller.load("https://one.example")
        val second = controller.open(true, "https://two.example")!!
        assertNull(sessions.getValue(0).view)
        assertSame(view, sessions.getValue(second.id).view)
        controller.select(0)
        assertSame(view, sessions.getValue(0).view)
        assertNull(sessions.getValue(second.id).view)
        assertEquals(listOf("https://one.example"), sessions.getValue(0).urls)
        assertEquals(listOf("https://two.example"), sessions.getValue(second.id).urls)
        controller.close()
        assertEquals(1, sessions.getValue(0).closeCount)
        assertEquals(1, sessions.getValue(second.id).closeCount)
    }

    @Test fun closingActiveTabReturnsToOpenerAndClosesOnlyThatSession() {
        val sessions = mutableMapOf<Int, FakeSession>()
        val controller = GeckoTabController<Any> { tab -> FakeSession().also { sessions[tab.id] = it } }
        val view = Any()
        controller.attach(view)
        val child = controller.open(false)!!
        assertTrue(controller.closeTab(child.id))
        assertEquals(0, controller.activeId)
        assertEquals(1, sessions.getValue(child.id).closeCount)
        assertSame(view, sessions.getValue(0).view)
        assertEquals(0, sessions.getValue(0).closeCount)
    }

    @Test fun closingLastTabCreatesAndAttachesFreshNormalSession() {
        val sessions = mutableMapOf<Int, FakeSession>()
        val controller = GeckoTabController<Any> { tab -> FakeSession().also { sessions[tab.id] = it } }
        val view = Any()
        controller.attach(view)
        assertTrue(controller.closeTab(0))
        assertEquals(1, sessions.getValue(0).closeCount)
        assertEquals(1, controller.tabs.size)
        assertFalse(controller.tabs.single().privateTab)
        assertSame(view, sessions.getValue(controller.activeId).view)
    }

    @Test fun limitAndUnknownTabLeaveCurrentSessionAlone() {
        val controller = GeckoTabController<Any> { FakeSession() }
        repeat(11) { assertNotNull(controller.open(false)) }
        val current = controller.activeId
        assertNull(controller.open(false))
        assertFalse(controller.select(900))
        assertFalse(controller.closeTab(900))
        assertEquals(current, controller.activeId)
    }

    @Test fun detachReattachAndCloseAreSafe() {
        val sessions = mutableMapOf<Int, FakeSession>()
        val controller = GeckoTabController<Any> { tab -> FakeSession().also { sessions[tab.id] = it } }
        val view = Any()
        controller.attach(view)
        controller.attach(view)
        controller.detach()
        assertNull(sessions.getValue(0).view)
        controller.attach(view)
        controller.close()
        controller.close()
        assertNull(sessions.getValue(0).view)
        assertEquals(1, sessions.getValue(0).closeCount)
    }

    @Test fun failedCreationRestoresPreviouslySelectedTab() {
        val controller = GeckoTabController<Any> { tab ->
            if (tab.id != 0) error("creation failed") else FakeSession()
        }
        try { controller.open(false); fail("should throw") } catch (_: IllegalStateException) { }
        assertEquals(0, controller.activeId)
        assertEquals(1, controller.tabs.size)
    }
}
