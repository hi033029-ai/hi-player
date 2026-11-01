package com.example.ui.screens
import org.junit.Assert.*
import org.junit.Test
class BrowserTabBackPolicyTest {
    @Test fun rootBackReturnsHomeInsteadOfOpeningAnOlderTab() {
        assertEquals(BrowserTabBackPolicy.BackAction.HOME, BrowserTabBackPolicy.backAction(false, false))
    }
    @Test fun homeBackExitsEvenIfOldWebHistoryExisted() {
        assertEquals(BrowserTabBackPolicy.BackAction.EXIT_BROWSER, BrowserTabBackPolicy.backAction(true, true))
    }
    @Test fun inPageBackStillUnwindsActualPageHistory() {
        assertEquals(BrowserTabBackPolicy.BackAction.PAGE_HISTORY, BrowserTabBackPolicy.backAction(false, true))
    }
    @Test fun childReturnsToOpenerBeforeOtherTabs() { assertEquals(1, BrowserTabBackPolicy.target(3, 1, listOf(1, 2, 3), listOf(1, 2, 3))) }
    @Test fun closedOpenerUsesMostRecentSurvivingTab() { assertEquals(2, BrowserTabBackPolicy.target(3, 1, listOf(2, 3, 4), listOf(4, 1, 2, 3))) }
    @Test fun closingInactiveTabNeverTargetsItself() { assertEquals(2, BrowserTabBackPolicy.target(1, null, listOf(1, 2), listOf(2, 1))) }
    @Test fun lastTabHasNoPreviousTab() { assertNull(BrowserTabBackPolicy.target(1, null, listOf(1), listOf(1))) }
    @Test fun missingHistoryUsesAnotherOpenTabNotHome() { assertEquals(5, BrowserTabBackPolicy.target(3, null, listOf(3, 5), emptyList())) }
}
