package com.example.browser.gecko

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ui.theme.HiPlayerTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
class GeckoContextActionsUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun privateLinkRequiresTapAndWarnsBeforeSharing() {
        val shared = mutableListOf<String>()
        val actions = GeckoContextActions({ 1 to true }, { _, _ -> true }, { true }, { shared.add(it); true })
        compose.setContent { HiPlayerTheme { GeckoContextActionsDialog(actions, 1) } }
        compose.runOnIdle { actions.offer(1, "https://example.org", null) }
        compose.onNodeWithText("Copying or sharing leaves private browsing.").assertIsDisplayed()
        compose.runOnIdle { assertTrue(shared.isEmpty()) }
        compose.onNodeWithText("Share link").performClick()
        compose.runOnIdle { assertEquals(listOf("https://example.org"), shared) }
    }
}
