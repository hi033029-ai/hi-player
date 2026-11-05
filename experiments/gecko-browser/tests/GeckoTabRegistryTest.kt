package com.example.browser.gecko

import org.junit.Assert.*
import org.junit.Test

class GeckoTabRegistryTest {
    @Test fun openAndSwitchKeepTabIdentityAndPrivacy() {
        val registry = GeckoTabRegistry()
        val privateTab = registry.open(true)!!
        assertEquals(privateTab.id, registry.activeId)
        assertTrue(privateTab.privateTab)
        assertTrue(registry.select(1))
        assertFalse(registry.select(999))
        assertEquals(1, registry.activeId)
        assertTrue(registry.tabs.first { it.id == privateTab.id }.privateTab)
    }
    @Test fun closingActiveChildReturnsToOpener() {
        val registry = GeckoTabRegistry()
        val child = registry.open(false)!!
        registry.open(false)
        registry.select(child.id)
        registry.close(child.id)
        assertEquals(1, registry.activeId)
    }
    @Test fun closingInactiveTabDoesNotChangeActiveTab() {
        val registry = GeckoTabRegistry()
        val first = registry.open(false)!!
        val second = registry.open(true)!!
        registry.close(first.id)
        assertEquals(second.id, registry.activeId)
        assertEquals(1, registry.tabs.first { it.id == second.id }.openerId)
    }
    @Test fun lastTabCloseCreatesANewNormalTab() {
        val registry = GeckoTabRegistry()
        registry.close(1)
        assertEquals(1, registry.tabs.size)
        assertEquals(registry.tabs.single().id, registry.activeId)
        assertFalse(registry.tabs.single().privateTab)
        assertNotEquals(1, registry.activeId)
    }
    @Test fun tabLimitCannotChangeActiveTab() {
        val registry = GeckoTabRegistry()
        repeat(11) { assertNotNull(registry.open(false)) }
        val before = registry.activeId
        assertNull(registry.open(true))
        assertEquals(before, registry.activeId)
        assertEquals(12, registry.tabs.size)
    }
    @Test fun missingTabCloseDoesNotChangeState() {
        val registry = GeckoTabRegistry()
        val before = registry.tabs
        assertNull(registry.close(100))
        assertEquals(before, registry.tabs)
        assertEquals(1, registry.activeId)
    }
}

class GeckoTabRestoreTest {
    @Test fun restoresOnlyUniquePositiveRegularTabsAndValidOpener() {
        val tabs = listOf(
            GeckoTabRegistry.Tab(4, false, null),
            GeckoTabRegistry.Tab(8, false, 4),
            GeckoTabRegistry.Tab(9, true, 4),
            GeckoTabRegistry.Tab(8, false, null),
            GeckoTabRegistry.Tab(0, false, null),
            GeckoTabRegistry.Tab(10, false, 9),
        )
        val registry = GeckoTabRegistry(tabs, 8)
        assertEquals(listOf(4, 8, 10), registry.tabs.map { it.id })
        assertEquals(8, registry.activeId)
        assertEquals(4, registry.tabs[1].openerId)
        assertNull(registry.tabs[2].openerId)
        assertEquals(11, registry.open(false)!!.id)
    }
    @Test fun privateOnlyRestoreStartsFreshNormalTab() {
        val registry = GeckoTabRegistry(listOf(GeckoTabRegistry.Tab(4, true, null)), 4)
        assertEquals(1, registry.activeId)
        assertFalse(registry.tabs.single().privateTab)
    }
    @Test fun missingActiveSelectsLastAndExtremeIdDoesNotOverflow() {
        val registry = GeckoTabRegistry(listOf(GeckoTabRegistry.Tab(Int.MAX_VALUE, false, null)), 77)
        assertEquals(Int.MAX_VALUE, registry.activeId)
        assertEquals(1, registry.open(false)!!.id)
    }
    @Test fun restoreRespectsTabLimit() {
        val registry = GeckoTabRegistry((1..15).map { GeckoTabRegistry.Tab(it, false, null) }, 15)
        assertEquals(12, registry.tabs.size)
        assertEquals(12, registry.activeId)
        assertNull(registry.open(false))
    }
}
