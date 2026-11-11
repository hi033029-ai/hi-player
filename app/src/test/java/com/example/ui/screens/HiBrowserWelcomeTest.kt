package com.example.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ui.theme.HiPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
class HiBrowserWelcomeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun startPageShowsBrandShortcutsAndSavedBookmarksImmediately() {
        compose.mainClock.autoAdvance = false
        var visited = ""
        compose.setContent { HiPlayerTheme {
            HiBrowserHome(isPrivate = false,
                shortcuts = listOf("YouTube" to "https://m.youtube.com", "Facebook" to "https://www.facebook.com/", "TikTok" to "https://www.tiktok.com/"),
                bookmarks = listOf("Saved example" to "https://example.com"),
                adBlockEnabled = true, onVisit = { visited = it }, onAddShortcut = { _, _ -> },
                onRemoveShortcut = {})
        } }
        compose.onNodeWithText("Hi Browser").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("YouTube").fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodesWithText("Facebook").fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodesWithText("TikTok").fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithText("Saved bookmarks").assertIsDisplayed()
        compose.onNodeWithText("Saved example").assertIsDisplayed()
        assertEquals("The start page must not be covered by a welcome screen", 0,
            compose.onAllNodesWithText("Welcome to Hi Browser").fetchSemanticsNodes().size)
        compose.onNodeWithTag("browser_bookmark_0").performClick()
        compose.runOnIdle { assertEquals("https://example.com", visited) }
        compose.mainClock.autoAdvance = true
    }

    @Test fun freshInstallQuickLinksIncludeVideoAndSocialSites() {
        val quickLinks = ORBIT_DEFAULT_SHORTCUTS.associate { it.title to it.url }
        assertEquals("https://m.youtube.com", quickLinks["YouTube"])
        assertEquals("https://www.facebook.com/", quickLinks["Facebook"])
        assertEquals("https://www.tiktok.com/", quickLinks["TikTok"])
    }

    @Test fun privateHomeKeepsItsPrivateLabel() {
        compose.setContent { HiPlayerTheme {
            HiBrowserHome(isPrivate = true, shortcuts = emptyList(), adBlockEnabled = false,
                onVisit = {}, onAddShortcut = { _, _ -> }, onRemoveShortcut = {})
        } }
        compose.onNodeWithText("Private browsing").assertIsDisplayed()
    }
}
