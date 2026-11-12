package com.example.browser.gecko

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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
class GeckoWebsitePromptContentTest {
    @get:Rule val compose = createComposeRule()

    @Test fun confirmDisplaysSiteAndLetsUserCancel() {
        var answer: Boolean? = null
        compose.setContent { HiPlayerTheme { GeckoWebsitePromptContent("Continue?", true, "example.org") { answer = it } } }
        compose.onNodeWithText("From: example.org").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(false, answer) }
    }
}
