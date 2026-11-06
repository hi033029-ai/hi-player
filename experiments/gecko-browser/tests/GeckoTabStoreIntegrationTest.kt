package com.example.browser.gecko

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.screens.OrbitBrowserStore
import com.example.ui.screens.OrbitBrowserEntry
import java.util.concurrent.Executor
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GeckoTabStoreIntegrationTest {
    @Test fun existingStoreRoundTripsRegularTabsWithoutPrivateTabData() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("orbit_browser", Context.MODE_PRIVATE).edit().clear().commit()
        val store = OrbitBrowserStore(context)
        val tabs = listOf(GeckoTabRegistry.Tab(8, false, null), GeckoTabRegistry.Tab(9, true, 8))
        val saved = GeckoTabPersistence.save(tabs, 9, mapOf(
            8 to GeckoTabPersistence.DisplayState("https://example.org/path", "Page", true),
            9 to GeckoTabPersistence.DisplayState("https://private.example/", "Private", false),
        ))
        store.saveTabs(saved.tabs, saved.activeId)
        val restored = GeckoTabPersistence.restore(store.savedTabs(), store.savedActiveTabId)
        assertEquals(listOf(8), restored.tabs.map { it.id })
        assertNull(restored.activeId)
        assertEquals("https://example.org/path", restored.states.getValue(8).url)
        assertTrue(restored.states.getValue(8).desktop)
        assertFalse(context.getSharedPreferences("orbit_browser", Context.MODE_PRIVATE).getString("open_tabs", "")!!.contains("private.example"))
    }
    @Test fun completedVisitUsesExistingHistoryStore() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = OrbitBrowserStore(context)
        store.clearHistory()
        val adapter = GeckoOrbitTabStore(store, Executor { it.run() })
        adapter.recordVisit(OrbitBrowserEntry("Example", "https://example.org/page"))
        assertEquals(OrbitBrowserEntry("Example", "https://example.org/page"), store.history().first())
        store.clearHistory()
    }
    @Test fun clearingRunsAfterPreviouslyQueuedVisits() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = OrbitBrowserStore(context)
        store.clearHistory()
        val work = mutableListOf<Runnable>()
        val adapter = GeckoOrbitTabStore(store, Executor { work.add(it) })
        adapter.recordVisit(OrbitBrowserEntry("old", "https://example.org/old"))
        adapter.clearHistory { }
        adapter.recordVisit(OrbitBrowserEntry("new", "https://example.org/new"))
        assertEquals(3, work.size)
        work.forEach { it.run() }
        assertEquals(listOf(OrbitBrowserEntry("new", "https://example.org/new")), store.history())
        store.clearHistory()
    }

    @Test fun durableHistoryClearPreservesBookmarks() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = OrbitBrowserStore(context)
        store.clearHistory()
        val history = OrbitBrowserEntry("History", "https://example.org/history")
        val bookmark = OrbitBrowserEntry("Saved", "https://example.org/bookmark")
        store.addHistory(history); store.addBookmark(bookmark)
        assertTrue(store.clearHistoryAndWait())
        assertTrue(store.history().isEmpty())
        assertTrue(store.isBookmarked(bookmark.url))
        assertEquals("[]", context.getSharedPreferences("orbit_browser", Context.MODE_PRIVATE).getString("history", null))
        store.removeBookmark(bookmark.url)
    }

}
