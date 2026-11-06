package com.example.browser.gecko

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.screens.OrbitBrowserStore
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
}
