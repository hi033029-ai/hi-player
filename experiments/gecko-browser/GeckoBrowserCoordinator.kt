package com.example.browser.gecko

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import com.example.ui.screens.OrbitBrowserEntry
import com.example.ui.screens.OrbitBrowserStore
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import com.example.ui.screens.OrbitSavedTab

/** Small boundary for the existing tab store; never stores a private Gecko session. */
internal interface GeckoTabStore {
    fun readTabs(): List<OrbitSavedTab>
    fun readActiveId(): Int
    fun writeTabs(tabs: List<OrbitSavedTab>, activeId: Int)
    fun recordVisit(entry: OrbitBrowserEntry)
}

internal class GeckoOrbitTabStore(private val store: OrbitBrowserStore, private val executor: Executor = historyExecutor) : GeckoTabStore {
    override fun readTabs() = store.savedTabs()
    override fun readActiveId() = store.savedActiveTabId
    override fun writeTabs(tabs: List<OrbitSavedTab>, activeId: Int) = store.saveTabs(tabs, activeId)
    override fun recordVisit(entry: OrbitBrowserEntry) { executor.execute { runCatching { store.addHistory(entry) } } }
    private companion object {
        val historyExecutor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "gecko-history").apply { isDaemon = true } }
    }
}

/**
 * Screen-independent tab owner. The caller owns and closes it on the main thread, not on each
 * Compose view detach. Pages and private state live in memory until their tabs are closed.
 * Snapshot callbacks must be delivered on the main thread.
 */
internal class GeckoBrowserCoordinator<View : Any>(
    private val store: GeckoTabStore,
    private val createSession: (GeckoTabRegistry.Tab, (GeckoTabSnapshot) -> Unit) -> GeckoManagedSession<View>,
) : AutoCloseable {
    val snapshots = mutableStateMapOf<Int, GeckoTabSnapshot>()
    private val desktop = mutableMapOf<Int, Boolean>()
    private val lastRecordedUrl = mutableMapOf<Int, String>()
    private val generations = mutableMapOf<Int, Long>()
    private var nextGeneration = 0L
    private var ready = false
    private var closed = false
    private var lastSaved: GeckoTabPersistence.Saved? = null
    var revision by mutableIntStateOf(0)
        private set
    val controller: GeckoTabController<View>
    val tabs get() = controller.tabs
    val activeId get() = controller.activeId
    val activeSnapshot get() = snapshots[activeId] ?: GeckoTabSnapshot(activeId, tabs.single { it.id == activeId }.privateTab)

    init {
        val restored = GeckoTabPersistence.restore(store.readTabs(), store.readActiveId())
        restored.tabs.forEach { tab ->
            val state = restored.states.getValue(tab.id)
            snapshots[tab.id] = GeckoTabSnapshot(tab.id, false, state.url, state.title)
            desktop[tab.id] = state.desktop
        }
        controller = GeckoTabController(restored.tabs, restored.activeId, restored.states) { tab ->
            if (!snapshots.containsKey(tab.id)) snapshots[tab.id] = GeckoTabSnapshot(tab.id, tab.privateTab)
            val previous = generations[tab.id]
            val generation = ++nextGeneration
            generations[tab.id] = generation
            try {
                createSession(tab) { snapshot ->
                    if (snapshot.id == tab.id && generations[tab.id] == generation) onSnapshot(snapshot)
                }
            } catch (error: Exception) {
                if (previous == null) generations.remove(tab.id) else generations[tab.id] = previous
                throw error
            }
        }
        ready = true
        persist()
    }

    private fun onSnapshot(snapshot: GeckoTabSnapshot) {
        if (closed) return
        val previous = snapshots[snapshot.id]
        // Ignore late callbacks from closed sessions. During initialization all restored IDs
        // are already known; a new tab is registered before its session factory is invoked.
        if (previous == null || previous.privateTab != snapshot.privateTab) return
        snapshots[snapshot.id] = snapshot
        if (snapshot.loading || snapshot.error != null) lastRecordedUrl.remove(snapshot.id)
        if (ready && !snapshot.privateTab && !snapshot.loading && snapshot.error == null) {
            val completed = previous.loading && snapshot.progress == 100
            val updatedTitle = lastRecordedUrl[snapshot.id] == snapshot.url && previous.title != snapshot.title
            val safeUrl = GeckoTabPersistence.safeUrl(snapshot.url)
            if ((completed || updatedTitle) && safeUrl.isNotEmpty()) {
                val title = snapshot.title.take(500).takeUnless { it.isBlank() || it == "New tab" } ?: safeUrl
                store.recordVisit(OrbitBrowserEntry(title, safeUrl))
                lastRecordedUrl[snapshot.id] = safeUrl
            }
        }
        if (ready && (previous.url != snapshot.url || previous.title != snapshot.title)) persist()
    }

    fun open(privateTab: Boolean): Boolean {
        check(!closed)
        val before = controller.tabs.map { it.id }.toSet()
        try { controller.open(privateTab) } catch (error: Exception) {
            snapshots.keys.filter { it !in before }.toList().forEach { snapshots.remove(it); generations.remove(it) }
            throw error
        } ?: return false
        revision++
        persist()
        return true
    }
    fun select(id: Int): Boolean {
        check(!closed)
        if (!controller.select(id)) return false
        revision++
        persist()
        return true
    }
    fun closeTab(id: Int): Boolean {
        check(!closed)
        if (!controller.closeTab(id)) return false
        snapshots.remove(id)
        desktop.remove(id)
        lastRecordedUrl.remove(id)
        generations.remove(id)
        revision++
        persist()
        return true
    }
    /** Home starts a fresh session so the previous page is not retained as hidden history. */
    fun home() {
        check(!closed)
        val tab = tabs.single { it.id == activeId }
        val previousGeneration = generations[tab.id]
        val previousSnapshot = snapshots[tab.id]
        try { controller.resetActive() } catch (error: Exception) {
            if (previousGeneration == null) generations.remove(tab.id) else generations[tab.id] = previousGeneration
            if (previousSnapshot == null) snapshots.remove(tab.id) else snapshots[tab.id] = previousSnapshot
            throw error
        }
        controller.setDesktop(desktop[tab.id] ?: false)
        lastRecordedUrl.remove(tab.id)
        snapshots[tab.id] = GeckoTabSnapshot(tab.id, tab.privateTab)
        revision++
        persist()
    }
    fun load(url: String) { check(!closed); controller.load(url) }
    fun reload() { check(!closed); controller.reload() }
    fun stop() { check(!closed); controller.stop() }
    fun back() { check(!closed); controller.back() }
    fun forward() { check(!closed); controller.forward() }
    fun exitFullScreen() { check(!closed); controller.exitFullScreen() }
    fun desktopMode(): Boolean = desktop[activeId] ?: false
    fun setDesktop(enabled: Boolean) {
        check(!closed)
        controller.setDesktop(enabled)
        desktop[activeId] = enabled
        revision++
        persist()
    }

    private fun persist() {
        if (!ready || closed) return
        val states = controller.tabs.associate { tab ->
            val snapshot = snapshots[tab.id] ?: GeckoTabSnapshot(tab.id, tab.privateTab)
            tab.id to GeckoTabPersistence.DisplayState(snapshot.url, snapshot.title, desktop[tab.id] ?: false)
        }
        val saved = GeckoTabPersistence.save(controller.tabs, controller.activeId, states)
        if (saved != lastSaved) {
            store.writeTabs(saved.tabs, saved.activeId)
            lastSaved = saved
        }
    }

    override fun close() {
        if (closed) return
        persist()
        closed = true
        controller.close()
        snapshots.clear()
        desktop.clear()
        lastRecordedUrl.clear()
        generations.clear()
    }
}
