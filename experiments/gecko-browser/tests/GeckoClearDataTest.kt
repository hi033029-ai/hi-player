package com.example.browser.gecko

import org.junit.Assert.*
import org.junit.Test
import org.mozilla.geckoview.StorageController

class GeckoClearDataTest {
    @Test fun selectedCategoriesWaitForAllOperationsAndUseScopedFlags() {
        val calls = mutableListOf<String>()
        val callbacks = mutableMapOf<String, (Boolean) -> Unit>()
        var flags = 0L
        val clear = GeckoClearDataController(
            purgeTabs = { calls += "tabs"; true },
            clearHistory = { calls += "history"; callbacks["history"] = it },
            clearIcons = { calls += "icons"; callbacks["icons"] = it },
            clearRouting = { calls += "routing"; callbacks["routing"] = it },
            clearStorage = { bits, done -> calls += "storage"; flags = bits; callbacks["storage"] = done },
        )
        assertTrue(clear.clear(GeckoClearDataSelection(true, true, true)))
        assertEquals(listOf("tabs", "history", "icons", "routing", "storage"), calls)
        assertEquals(StorageController.ClearFlags.COOKIES or StorageController.ClearFlags.DOM_STORAGES or
            StorageController.ClearFlags.AUTH_SESSIONS or StorageController.ClearFlags.ALL_CACHES, flags)
        assertFalse(clear.clear(GeckoClearDataSelection(true, false, false)))
        callbacks.getValue("history")(true); callbacks.getValue("icons")(true)
        callbacks.getValue("routing")(true); callbacks.getValue("storage")(true)
        assertEquals(GeckoClearDataState.COMPLETE, clear.state)
        clear.dismissResult(); assertEquals(GeckoClearDataState.IDLE, clear.state)
    }
    @Test fun cacheOnlyDoesNotEraseCookiesOrHistory() {
        var actual = 0L
        val clear = GeckoClearDataController({ error("history") }, { error("history") }, { error("icons") }, { error("routing") }, { flags, done -> actual = flags; done(true) })
        assertTrue(clear.clear(GeckoClearDataSelection(false, false, true)))
        assertEquals(StorageController.ClearFlags.ALL_CACHES, actual)
        assertEquals(GeckoClearDataState.COMPLETE, clear.state)
    }
    @Test fun failuresAndDuplicatesDoNotHideIncompleteOrFailedOperations() {
        var history: ((Boolean) -> Unit)? = null
        var icons: ((Boolean) -> Unit)? = null
        val clear = GeckoClearDataController({ false }, { history = it }, { icons = it }, { error("routing") }, { _, _ -> error("storage") })
        assertTrue(clear.clear(GeckoClearDataSelection(true, false, false)))
        history!!(true); history!!(true)
        assertEquals(GeckoClearDataState.CLEARING, clear.state)
        icons!!(true)
        assertEquals(GeckoClearDataState.FAILED, clear.state)
    }
    @Test fun exceptionsFailButOtherOperationsStillRunAndClosedOwnerIgnoresCallbacks() {
        var otherRan = false
        var delayed: ((Boolean) -> Unit)? = null
        val clear = GeckoClearDataController({ throw IllegalStateException() }, { delayed = it }, { otherRan = true; it(true) }, { it(true) }, { _, done -> done(true) })
        assertTrue(clear.clear(GeckoClearDataSelection(true, false, false)))
        assertTrue(otherRan); assertEquals(GeckoClearDataState.CLEARING, clear.state)
        clear.close(); delayed!!(true)
        assertEquals(GeckoClearDataState.CLEARING, clear.state)
        assertFalse(clear.clear(GeckoClearDataSelection(false, false, true)))
    }
    @Test fun emptySelectionDoesNotRunAnyOperation() {
        val clear = GeckoClearDataController({ false }, {}, {}, {}, { _, _ -> })
        assertFalse(clear.clear(GeckoClearDataSelection(false, false, false)))
        assertEquals(GeckoClearDataState.IDLE, clear.state)
    }
}
