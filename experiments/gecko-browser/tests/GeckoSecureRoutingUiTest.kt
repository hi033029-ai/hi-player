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
class GeckoSecureRoutingUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun explicitConfirmationAndReturnPage() {
        val opened = mutableListOf<String>()
        val routing = GeckoSecureRouting({ false }, { false }, {}, {}, { opened.add(it); true }, { false })
        compose.setContent { HiPlayerTheme {
            GeckoSecureRoutingDialogs(routing, 1)
            GeckoSecureSitePage(routing, 1) { }
        } }
        compose.runOnIdle { routing.decision(1, GeckoNavigationDecision.SecureSite("https://example.org/", false)) }
        compose.onNodeWithText("Website sign-in").assertIsDisplayed()
        compose.runOnIdle { assertTrue(opened.isEmpty()) }
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("This website is open in your device browser.").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf("https://example.org/"), opened) }
        compose.onNodeWithText("Use Hi Browser instead").performClick()
        compose.runOnIdle { assertNull(routing.route(1)) }
    }
}
