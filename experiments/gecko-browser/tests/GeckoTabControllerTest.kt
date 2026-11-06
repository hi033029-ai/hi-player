package com.example.browser.gecko

import org.junit.Assert.*
import org.junit.Test

class GeckoTabControllerTest {
    private class FakeSession : GeckoManagedSession<Any> {
        var view: Any? = null
        var closeCount = 0
        val urls = mutableListOf<String>()
        val commands = mutableListOf<String>()
        override fun attach(view: Any) { check(this.view == null); this.view = view }
        override fun detach() { view = null }
        override fun load(url: String) { urls += url }
        override fun reload() { commands += "reload" }
        override fun stop() { commands += "stop" }
        override fun back() { commands += "back" }
        override fun forward() { commands += "forward" }
        override fun exitFullScreen() = Unit
        override fun setDesktop(enabled: Boolean) { commands += "desktop:$enabled" }
        override fun close() { closeCount++ }
    }

    @Test fun switchingKeepsOneAttachedSessionAndSeparateHistory() {
        val sessions = mutableMapOf<Int, FakeSession>()
        val controller = GeckoTabController<Any> { tab -> FakeSession().also { sessions[tab.id] = it } }
        val view = Any()
        controller.attach(view)
        controller.load("https://one.example")
        val second = controller.open(true, "https://two.example")!!
        assertNull(sessions.getValue(1).view)
        assertSame(view, sessions.getValue(second.id).view)
        controller.select(1)
        assertSame(view, sessions.getValue(1).view)
        assertNull(sessions.getValue(second.id).view)
        assertEquals(listOf("https://one.example"), sessions.getValue(1).urls)
        assertEquals(listOf("https://two.example"), sessions.getValue(second.id).urls)
        controller.close()
        assertEquals(1, sessions.getValue(1).closeCount)
        assertEquals(1, sessions.getValue(second.id).closeCount)
    }

    @Test fun homeResetReplacesSessionAndReattachesView() {
        val sessions = mutableListOf<FakeSession>()
        val controller = GeckoTabController<Any> { FakeSession().also(sessions::add) }
        val view = Any()
        controller.attach(view)
        val first = sessions.single()
        controller.resetActive()
        assertEquals(1, first.closeCount)
        assertNull(first.view)
        assertSame(view, sessions.last().view)
        controller.close()
    }

    @Test fun failedReplacementAttachRestoresOldSession() {
        var calls = 0
        val first = FakeSession()
        var replacementClosed = false
        val controller = GeckoTabController<Any> {
            if (++calls == 1) first else object : GeckoManagedSession<Any> by FakeSession() {
                override fun attach(view: Any) { error("attach failed") }
                override fun close() { replacementClosed = true }
            }
        }
        val view = Any()
        controller.attach(view)
        try { controller.resetActive(); fail("expected failure") } catch (_: IllegalStateException) { }
        assertSame(view, first.view)
        assertEquals(0, first.closeCount)
        assertTrue(replacementClosed)
        controller.close()
    }

    @Test fun navigationCommandsTargetOnlyTheSelectedTab() {
        val sessions = mutableMapOf<Int, FakeSession>()
        val controller = GeckoTabController<Any> { tab -> FakeSession().also { sessions[tab.id] = it } }
        val second = controller.open(true)!!
        controller.reload(); controller.stop(); controller.back(); controller.forward(); controller.setDesktop(true)
        assertEquals(listOf("reload", "stop", "back", "forward", "desktop:true"), sessions.getValue(second.id).commands)
        assertTrue(sessions.getValue(1).commands.isEmpty())
        controller.select(1)
        controller.back(); controller.setDesktop(false)
        assertEquals(listOf("back", "desktop:false"), sessions.getValue(1).commands)
        controller.close()
    }

    @Test fun closingActiveTabReturnsToOpenerAndClosesOnlyThatSession() {
        val sessions = mutableMapOf<Int, FakeSession>()
        val controller = GeckoTabController<Any> { tab -> FakeSession().also { sessions[tab.id] = it } }
        val view = Any()
        controller.attach(view)
        val child = controller.open(false)!!
        assertTrue(controller.closeTab(child.id))
        assertEquals(1, controller.activeId)
        assertEquals(1, sessions.getValue(child.id).closeCount)
        assertSame(view, sessions.getValue(1).view)
        assertEquals(0, sessions.getValue(1).closeCount)
    }

    @Test fun closingLastTabCreatesAndAttachesFreshNormalSession() {
        val sessions = mutableMapOf<Int, FakeSession>()
        val controller = GeckoTabController<Any> { tab -> FakeSession().also { sessions[tab.id] = it } }
        val view = Any()
        controller.attach(view)
        assertTrue(controller.closeTab(1))
        assertEquals(1, sessions.getValue(1).closeCount)
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
        assertNull(sessions.getValue(1).view)
        controller.attach(view)
        controller.close()
        controller.close()
        assertNull(sessions.getValue(1).view)
        assertEquals(1, sessions.getValue(1).closeCount)
    }

    @Test fun lateReleaseDoesNotDetachAReplacementView() {
        val sessions = mutableMapOf<Int, FakeSession>()
        val controller = GeckoTabController<Any> { tab -> FakeSession().also { sessions[tab.id] = it } }
        val old = Any()
        val replacement = Any()
        controller.attach(old)
        controller.attach(replacement)
        controller.detach(old)
        assertSame(replacement, sessions.getValue(1).view)
        controller.close()
        controller.detach(replacement)
    }

    @Test fun failedCreationRestoresPreviouslySelectedTab() {
        val controller = GeckoTabController<Any> { tab ->
            if (tab.id != 1) error("creation failed") else FakeSession()
        }
        try { controller.open(false); fail("should throw") } catch (_: IllegalStateException) { }
        assertEquals(1, controller.activeId)
        assertEquals(1, controller.tabs.size)
    }
}

class GeckoControllerRestoreTest {
    private class Fake : GeckoManagedSession<Any> {
        var attached = false
        var closes = 0
        override fun attach(view: Any) { attached = true }
        override fun detach() { attached = false }
        override fun load(url: String) = Unit
        override fun reload() = Unit
        override fun stop() = Unit
        override fun back() = Unit
        override fun forward() = Unit
        override fun exitFullScreen() = Unit
        override fun setDesktop(enabled: Boolean) = Unit
        override fun close() { closes++ }
    }
    @Test fun createsSessionsForRestoredRegularTabsAndAttachesSelectedOne() {
        val sessions = mutableMapOf<Int, Fake>()
        val tabs = listOf(GeckoTabRegistry.Tab(4, false, null), GeckoTabRegistry.Tab(5, false, 4))
        val controller = GeckoTabController<Any>(tabs, 4) { tab -> Fake().also { sessions[tab.id] = it } }
        controller.attach(Any())
        assertTrue(sessions.getValue(4).attached)
        assertFalse(sessions.getValue(5).attached)
        controller.select(5)
        assertTrue(sessions.getValue(5).attached)
        assertFalse(sessions.getValue(4).attached)
    }
    @Test fun restoresUrlAndDesktopOnlyForRestoredRegularSessions() {
        val sessions = mutableMapOf<Int, MutableList<String>>()
        val tabs = listOf(GeckoTabRegistry.Tab(4, false, null), GeckoTabRegistry.Tab(5, true, null))
        val states = mapOf(4 to GeckoTabPersistence.DisplayState("https://example.org", "Example", true),
            5 to GeckoTabPersistence.DisplayState("https://secret.example", "Secret", false))
        val controller = GeckoTabController<Any>(tabs, 4, states) { tab ->
            object : GeckoManagedSession<Any> {
                val events = mutableListOf<String>().also { sessions[tab.id] = it }
                override fun attach(view: Any) = Unit
                override fun detach() = Unit
                override fun load(url: String) { events += "load:$url" }
                override fun reload() = Unit
                override fun stop() = Unit
                override fun back() = Unit
                override fun forward() = Unit
        override fun exitFullScreen() = Unit
                override fun setDesktop(enabled: Boolean) { events += "desktop:$enabled" }
                override fun close() = Unit
            }
        }
        assertEquals(listOf("desktop:true", "load:https://example.org"), sessions.getValue(4))
        assertFalse(sessions.containsKey(5))
        controller.close()
    }

    @Test fun failedRestoreLoadClosesAllCreatedSessions() {
        val created = Fake()
        val tabs = listOf(GeckoTabRegistry.Tab(4, false, null))
        try {
            GeckoTabController<Any>(tabs, 4, mapOf(4 to GeckoTabPersistence.DisplayState("https://example.org", "Page", false))) {
                object : GeckoManagedSession<Any> by created {
                    override fun load(url: String) { error("load failed") }
                }
            }
            fail("should throw")
        } catch (_: IllegalStateException) { }
        assertEquals(1, created.closes)
    }
    @Test fun failureCreatingRestoredSessionClosesPreviouslyCreatedSessions() {
        val created = Fake()
        val tabs = listOf(GeckoTabRegistry.Tab(4, false, null), GeckoTabRegistry.Tab(5, false, 4))
        try {
            GeckoTabController<Any>(tabs, 4) { tab -> if (tab.id == 5) error("failure") else created }
            fail("should throw")
        } catch (_: IllegalStateException) { }
        assertEquals(1, created.closes)
    }
}

class GeckoPopupReservationTest {
    private class Fake : GeckoManagedSession<Any> {
        var attached = false
        var closed = false
        override fun attach(view: Any) { attached = true }
        override fun detach() { attached = false }
        override fun load(url: String) = Unit
        override fun reload() = Unit
        override fun stop() = Unit
        override fun back() = Unit
        override fun forward() = Unit
        override fun exitFullScreen() = Unit
        override fun setDesktop(enabled: Boolean) = Unit
        override fun close() { closed = true }
    }

    @Test fun popupInheritsPrivateModeAndStaysBackgroundUntilSelected() {
        val sessions = mutableMapOf<Int, Fake>()
        val controller = GeckoTabController<Any> { tab -> Fake().also { sessions[tab.id] = it } }
        val privateTab = controller.open(true)!!
        controller.attach(Any())
        val (popup, child) = controller.reservePopup(privateTab.id) { tab -> Fake().also { sessions[tab.id] = it } }!!
        assertTrue(popup.privateTab)
        assertEquals(privateTab.id, popup.openerId)
        assertEquals(privateTab.id, controller.activeId)
        assertFalse((child as Fake).attached)
        controller.select(popup.id)
        assertTrue(child.attached)
        controller.closeTab(popup.id)
        assertTrue(child.closed)
        assertEquals(privateTab.id, controller.activeId)
    }

    @Test fun failureOrUnknownOpenerDoesNotDisruptCurrentTab() {
        val controller = GeckoTabController<Any> { Fake() }
        assertNull(controller.reservePopup(999) { Fake() })
        try { controller.reservePopup(1) { error("failure") }; fail() } catch (_: IllegalStateException) {}
        assertEquals(1, controller.activeId)
        assertEquals(1, controller.tabs.size)
    }

    @Test fun tabLimitBlocksPopupWithoutCreatingSession() {
        val controller = GeckoTabController<Any> { Fake() }
        repeat(11) { controller.open(false) }
        var created = false
        assertNull(controller.reservePopup(1) { created = true; Fake() })
        assertFalse(created)
    }
}
