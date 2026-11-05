package com.example.ui.screens

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.auth.SecureWebSessions
import com.example.ui.theme.HiPlayerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
class PopularWebsiteShortcutsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun websiteLinksUseHttpsAndIncludeRequestedSites() {
        assertTrue(POPULAR_WEBSITES.all { SecureWebSessions.validPage(it.second) })
        assertTrue(setOf("Facebook", "TikTok", "Yahoo").all { name -> POPULAR_WEBSITES.any { it.first == name } })
    }

    @Test fun privateTabExplainsBrowserHandoffBeforeOpening() {
        compose.setContent { HiPlayerTheme { PopularWebsiteShortcuts(isPrivate = true) } }
        compose.onNodeWithTag("popular_site_facebook").performClick()
        compose.onNodeWithText("Open Facebook").assertExists()
        compose.onNodeWithText("Continue in browser").assertExists()
        compose.onNodeWithText("This leaves Hi Browser's private tab. Your device browser may use saved accounts and history.").assertExists()
        compose.onNodeWithText("Cancel").performClick()
    }
}
