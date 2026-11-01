package com.example.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class TabHistoryTest {
    @Test fun settingsReturnsToFilesNotDefaultVideos() {
        val history = TabHistory.open(listOf("VIDEOS", "FILE_MANAGER"), "SETTINGS")
        assertEquals(listOf("VIDEOS", "FILE_MANAGER"), TabHistory.back(history))
    }
    @Test fun browserReturnsToTheActualVisitedOrigin() {
        val history = TabHistory.open(listOf("VIDEOS", "MUSIC"), "BROWSER")
        assertEquals("MUSIC", TabHistory.back(history).last())
    }
    @Test fun selectingCurrentTabDoesNotCreateDuplicateBackEntries() {
        val history = listOf("VIDEOS", "SETTINGS")
        assertEquals(history, TabHistory.open(history, "SETTINGS"))
    }
    @Test fun returningHomeDropsOldTabBranch() {
        var history = TabHistory.open(listOf("VIDEOS"), "MUSIC")
        history = TabHistory.open(history, "VIDEOS")
        history = TabHistory.open(history, "SETTINGS")
        assertEquals(listOf("VIDEOS"), TabHistory.back(history))
    }
    @Test fun selectingVisitedOriginUnwindsItsChildren() {
        assertEquals(listOf("VIDEOS", "FILE_MANAGER"),
            TabHistory.open(listOf("VIDEOS", "FILE_MANAGER", "BROWSER"), "FILE_MANAGER"))
    }
    @Test fun rootBackDoesNotInventAnUnvisitedDestination() {
        assertEquals(listOf("VIDEOS"), TabHistory.back(listOf("VIDEOS")))
    }
    @Test fun severalVisitsPopInReverseOrder() {
        var history = listOf("VIDEOS")
        for (tab in listOf("FILE_MANAGER", "SETTINGS", "BROWSER")) history = TabHistory.open(history, tab)
        history = TabHistory.back(history)
        assertEquals("SETTINGS", history.last())
        history = TabHistory.back(history)
        assertEquals("FILE_MANAGER", history.last())
    }
}
