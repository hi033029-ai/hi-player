package com.example.browser.gecko

/** The session wrapper makes tab lifecycle independent of a particular Android view. */
internal interface GeckoManagedSession<View : Any> : AutoCloseable {
    fun attach(view: View)
    fun detach()
    fun load(url: String)
}

/** Experimental: a single displayed view and one independent session per open tab. */
internal class GeckoTabController<View : Any>(
    private val newSession: (GeckoTabRegistry.Tab) -> GeckoManagedSession<View>,
) : AutoCloseable {
    private val registry = GeckoTabRegistry()
    private val sessions = mutableMapOf<Int, GeckoManagedSession<View>>()
    private var view: View? = null
    private var closed = false
    val tabs: List<GeckoTabRegistry.Tab> get() = registry.tabs
    val activeId: Int get() = registry.activeId

    init { sessions[activeId] = newSession(tabs.single()) }

    /** A newly attached view displays only the active session. */
    fun attach(newView: View) {
        check(!closed)
        if (view === newView) return
        sessions.getValue(activeId).detach()
        view = newView
        sessions.getValue(activeId).attach(newView)
    }

    fun detach() {
        check(!closed)
        if (view != null) sessions.getValue(activeId).detach()
        view = null
    }

    /** Null means the tab limit has been reached. */
    fun open(privateTab: Boolean, url: String = ""): GeckoTabRegistry.Tab? {
        check(!closed)
        val previousId = activeId
        val tab = registry.open(privateTab) ?: return null
        val session = try { newSession(tab) } catch (error: Throwable) {
            registry.close(tab.id)
            throw error
        }
        sessions[tab.id] = session
        if (view != null) {
            sessions.getValue(previousId).detach()
            session.attach(view!!)
        }
        if (url.isNotBlank()) session.load(url)
        return tab
    }

    fun select(id: Int): Boolean {
        check(!closed)
        if (id == activeId) return true
        if (tabs.none { it.id == id }) return false
        val previousId = activeId
        registry.select(id)
        if (view != null) {
            sessions.getValue(previousId).detach()
            sessions.getValue(id).attach(view!!)
        }
        return true
    }

    fun closeTab(id: Int): Boolean {
        check(!closed)
        val session = sessions[id] ?: return false
        val wasActive = id == activeId
        if (wasActive && view != null) session.detach()
        registry.close(id)
        sessions.remove(id)
        session.close()
        if (activeId !in sessions) sessions[activeId] = newSession(tabs.single { it.id == activeId })
        if (wasActive && view != null) sessions.getValue(activeId).attach(view!!)
        return true
    }

    fun load(url: String) { check(!closed); sessions.getValue(activeId).load(url) }

    override fun close() {
        if (closed) return
        if (view != null) sessions.getValue(activeId).detach()
        view = null
        sessions.values.forEach { it.close() }
        sessions.clear()
        closed = true
    }
}
