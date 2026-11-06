package com.example.browser.gecko

import com.example.ui.screens.OrbitSavedTab
import org.junit.Assert.*
import org.junit.Test

class GeckoTabPersistenceTest {
    @Test fun savesOnlyRegularTabsAndClearsPrivateOpenerAndSelection() {
        val tabs = listOf(GeckoTabRegistry.Tab(1, false, null), GeckoTabRegistry.Tab(2, true, 1), GeckoTabRegistry.Tab(3, false, 2))
        val states = mapOf(1 to GeckoTabPersistence.DisplayState("https://example.org/a?q=test", "One", false), 2 to GeckoTabPersistence.DisplayState("https://private.example", "Secret", false), 3 to GeckoTabPersistence.DisplayState("https://example.org", "Three", true))
        val result = GeckoTabPersistence.save(tabs, 2, states)
        assertEquals(listOf(1, 3), result.tabs.map { it.id })
        assertNull(result.tabs.last().openerTabId)
        assertEquals(-1, result.activeId)
        assertTrue(result.tabs.last().desktop)
        assertEquals("https://example.org/a?q=test", result.tabs.first().url)
    }

    @Test fun restoresExistingRecordsAndHandlesDuplicatesInvalidOpenerAndSelection() {
        val saved = listOf(OrbitSavedTab(4, "https://example.org", "Page", true, 99), OrbitSavedTab(4, "https://different.example", "Duplicate"), OrbitSavedTab(5, "", "", false, 4), OrbitSavedTab(-1, "", "Bad"))
        val restored = GeckoTabPersistence.restore(saved, 90)
        assertEquals(listOf(4, 5), restored.tabs.map { it.id })
        assertNull(restored.activeId)
        assertNull(restored.tabs.first().openerId)
        assertEquals(4, restored.tabs.last().openerId)
        assertTrue(restored.states.getValue(4).desktop)
        assertEquals("New tab", restored.states.getValue(5).title)
    }

    @Test fun doesNotPersistCredentialBearingOrNonWebUrls() {
        val unsafe = listOf("https://user:password@example.org/", "https://example.org/?access_token=secret", "https://example.org/#code=secret", "https://example.org/?%74oken=secret", "file:///data/private", "javascript:alert(1)", "https://example.org/?X-Amz-Signature=secret")
        for (url in unsafe) {
            val saved = GeckoTabPersistence.save(listOf(GeckoTabRegistry.Tab(1, false, null)), 1, mapOf(1 to GeckoTabPersistence.DisplayState(url, "Page", false)))
            assertEquals("must not persist $url", "", saved.tabs.single().url)
        }
    }

    @Test fun limitsNumberOfSavedTabsAndRejectsOversizedUrl() {
        val tabs = (1..20).map { GeckoTabRegistry.Tab(it, false, null) }
        val states = mapOf(1 to GeckoTabPersistence.DisplayState("https://example.org/" + "a".repeat(4100), "A".repeat(600), false))
        val saved = GeckoTabPersistence.save(tabs, 20, states)
        assertEquals(12, saved.tabs.size)
        assertEquals(-1, saved.activeId)
        assertEquals("", saved.tabs.first().url)
        assertEquals(500, saved.tabs.first().title.length)
    }
}
