package com.example.browser.gecko

import org.junit.Assert.*
import org.junit.Test

class GeckoTabRegistryTest {
    @Test fun openAndSwitchKeepTabIdentityAndPrivacy() {
        val registry = GeckoTabRegistry()
        val privateTab = registry.open(true)!!
        assertEquals(privateTab.id, registry.activeId)
        assertTrue(privateTab.privateTab)
        assertTrue(registry.select(0))
        assertFalse(registry.select(999))
        assertEquals(0, registry.activeId)
        assertTrue(registry.tabs.first { it.id == privateTab.id }.privateTab)
    }
    @Test fun closingActiveChildReturnsToOpener() {
        val registry = GeckoTabRegistry()
        val child = registry.open(false)!!
        registry.open(false)
        registry.select(child.id)
        registry.close(child.id)
        assertEquals(0, registry.activeId)
    }
    @Test fun closingInactiveTabDoesNotChangeActiveTab() {
        val registry = GeckoTabRegistry()
        val first = registry.open(false)!!
        val second = registry.open(true)!!
        registry.close(first.id)
        assertEquals(second.id, registry.activeId)
        assertEquals(0, registry.tabs.first { it.id == second.id }.openerId)
    }
    @Test fun lastTabCloseCreatesANewNormalTab() {
        val registry = GeckoTabRegistry()
        registry.close(0)
        assertEquals(1, registry.tabs.size)
        assertEquals(registry.tabs.single().id, registry.activeId)
        assertFalse(registry.tabs.single().privateTab)
        assertNotEquals(0, registry.activeId)
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
        assertEquals(0, registry.activeId)
    }
}
