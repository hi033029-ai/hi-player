package com.example.browser.gecko

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeckoHostActivationTest {
    private class Session : GeckoManagedSession<Any> {
        var active = false
        var closed = false
        override fun attach(view: Any) { active = true }
        override fun detach() { active = false }
        override fun setHostActive(active: Boolean) { this.active = active }
        override fun load(url: String) {}
        override fun reload() {}
        override fun stop() {}
        override fun back() {}
        override fun forward() {}
        override fun exitFullScreen() {}
        override fun purgeHistory() {}
        override fun setDesktop(enabled: Boolean) {}
        override fun close() { active = false; closed = true }
    }

    @Test fun inactiveHostStaysInactiveWhenTabsChange() {
        val sessions = mutableListOf<Session>()
        val controller = GeckoTabController<Any> { Session().also(sessions::add) }
        controller.attach(Any())
        assertTrue(sessions.single().active)
        controller.setHostActive(false)
        controller.open(true)
        assertTrue(sessions.none { it.active })
        controller.setHostActive(true)
        assertTrue(sessions.last().active)
        assertFalse(sessions.first().active)
        controller.close()
    }

    @Test fun reattachmentRespectsSuspendedHost() {
        val session = Session()
        val controller = GeckoTabController<Any> { session }
        controller.attach(Any())
        controller.setHostActive(false)
        controller.detach()
        controller.attach(Any())
        assertFalse(session.active)
        controller.setHostActive(true)
        assertTrue(session.active)
        controller.close()
    }

    @Test fun lateLifecycleCallbackAfterCloseIsHarmless() {
        val session = Session()
        val controller = GeckoTabController<Any> { session }
        controller.attach(Any())
        controller.close()
        controller.setHostActive(false)
        controller.setHostActive(true)
        assertTrue(session.closed)
        assertFalse(session.active)
    }
}
