package com.example.browser.gecko

import com.example.ui.screens.BrowserTabBackPolicy

/** In-memory tab ordering only. Does not persist URLs or private browsing data. */
internal class GeckoTabRegistry(restoredTabs: List<Tab> = emptyList(), restoredActiveId: Int? = null) {
    internal data class Tab(val id: Int, val privateTab: Boolean, val openerId: Int?)

    private val mutableTabs: MutableList<Tab>
    private val recent: MutableList<Int>
    private var nextId: Int
    val tabs: List<Tab> get() = mutableTabs.toList()
    var activeId: Int
        private set

    init {
        val restored = restoredTabs.asSequence().filter { it.id > 0 && !it.privateTab }
            .distinctBy { it.id }.take(MAX_TABS).toList()
        val ids = restored.map { it.id }.toSet()
        mutableTabs = restored.map { tab ->
            tab.copy(openerId = tab.openerId?.takeIf { it in ids && it != tab.id })
        }.toMutableList()
        if (mutableTabs.isEmpty()) mutableTabs += Tab(id = 1, privateTab = false, openerId = null)
        activeId = restoredActiveId?.takeIf { wanted -> mutableTabs.any { it.id == wanted } }
            ?: mutableTabs.last().id
        recent = mutableListOf(activeId)
        nextId = (mutableTabs.maxOf { it.id }.takeIf { it < Int.MAX_VALUE } ?: 0) + 1
    }

    private fun allocateId(): Int {
        while (mutableTabs.any { it.id == nextId }) nextId = if (nextId == Int.MAX_VALUE) 1 else nextId + 1
        val result = nextId
        nextId = if (nextId == Int.MAX_VALUE) 1 else nextId + 1
        return result
    }

    /** A new tab becomes active; null means the existing 12-tab limit was reached. */
    fun open(privateTab: Boolean, openerId: Int? = activeId): Tab? {
        if (mutableTabs.size >= MAX_TABS) return null
        val validOpener = openerId?.takeIf { id -> mutableTabs.any { it.id == id } }
        val tab = Tab(allocateId(), privateTab, validOpener)
        mutableTabs += tab
        select(tab.id)
        return tab
    }

    fun select(id: Int): Boolean {
        if (mutableTabs.none { it.id == id }) return false
        recent.remove(activeId)
        recent.remove(id)
        recent += activeId
        if (activeId != id) recent += id
        activeId = id
        return true
    }

    /** Returns the closed tab, or null if it didn't exist. Last close creates a normal tab. */
    fun close(id: Int): Tab? {
        val closing = mutableTabs.firstOrNull { it.id == id } ?: return null
        val target = BrowserTabBackPolicy.target(id, closing.openerId, mutableTabs.map { it.id }, recent)
        mutableTabs.removeAll { it.id == id }
        for (index in mutableTabs.indices) {
            if (mutableTabs[index].openerId == id) {
                mutableTabs[index] = mutableTabs[index].copy(openerId = closing.openerId)
            }
        }
        recent.remove(id)
        if (mutableTabs.isEmpty()) {
            val tab = Tab(allocateId(), privateTab = false, openerId = null)
            mutableTabs += tab
            activeId = tab.id
            recent.clear()
            recent += tab.id
        } else if (activeId == id) {
            // Keep a valid active id before updating MRU; never resurrect the closed id.
            activeId = target?.takeIf { candidate -> mutableTabs.any { it.id == candidate } }
                ?: mutableTabs.last().id
            recent.remove(activeId)
            recent += activeId
        }
        return closing
    }

    companion object { const val MAX_TABS = 12 }
}
