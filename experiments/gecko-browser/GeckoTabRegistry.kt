package com.example.browser.gecko

import com.example.ui.screens.BrowserTabBackPolicy

/** In-memory tab ordering only. Does not persist URLs or private browsing data. */
internal class GeckoTabRegistry {
    internal data class Tab(val id: Int, val privateTab: Boolean, val openerId: Int?)

    private val mutableTabs = mutableListOf(Tab(id = 0, privateTab = false, openerId = null))
    private val recent = mutableListOf(0)
    private var nextId = 1
    val tabs: List<Tab> get() = mutableTabs.toList()
    var activeId: Int = 0
        private set

    /** A new tab becomes active; null means the existing 12-tab limit was reached. */
    fun open(privateTab: Boolean, openerId: Int? = activeId): Tab? {
        if (mutableTabs.size >= MAX_TABS) return null
        val validOpener = openerId?.takeIf { id -> mutableTabs.any { it.id == id } }
        val tab = Tab(nextId++, privateTab, validOpener)
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
            val tab = Tab(nextId++, privateTab = false, openerId = null)
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
