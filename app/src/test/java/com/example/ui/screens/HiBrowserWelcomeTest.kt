package com.example.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ui.theme.HiPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
class HiBrowserWelcomeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun homeRemainsUsableDuringWelcome() {
        compose.mainClock.autoAdvance = false
        var visited = ""
        compose.setContent { HiPlayerTheme {
            HiBrowserHome(isPrivate = false, shortcuts = listOf("Example" to "https://example.com"),
                adBlockEnabled = true, onVisit = { visited = it }, onAddShortcut = { _, _ -> },
                onRemoveShortcut = {})
        } }
        compose.onNodeWithTag("browser_welcome_animation", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Welcome to Hi Browser").assertIsDisplayed()
        compose.onNodeWithText("Example").performClick()
        compose.runOnIdle { assertEquals("https://example.com", visited) }
        compose.mainClock.advanceTimeBy(1800)
        compose.onNodeWithText("Hi Browser").assertIsDisplayed()
        compose.mainClock.autoAdvance = true
    }

    @Test fun privateHomeKeepsItsPrivateLabel() {
        compose.setContent { HiPlayerTheme {
            HiBrowserHome(isPrivate = true, shortcuts = emptyList(), adBlockEnabled = false,
                onVisit = {}, onAddShortcut = { _, _ -> }, onRemoveShortcut = {})
        } }
        compose.onNodeWithText("Private browsing").assertIsDisplayed()
    }
}
