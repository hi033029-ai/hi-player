package com.example.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.auth.SecureWebSessions
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
class PopularWebsiteShortcutsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun websiteLinksUseHttpsAndIncludeRequestedSites() {
        assertTrue(POPULAR_WEBSITES.all { SecureWebSessions.validPage(it.second) })
        assertTrue(setOf("Facebook", "TikTok", "Yahoo").all { name -> POPULAR_WEBSITES.any { it.first == name } })
    }

    @Test fun shortcutUsesTheExistingBrowserNavigationCallback() {
        var visited = ""
        compose.setContent { HiPlayerTheme { PopularWebsiteShortcuts(onVisit = { visited = it }) } }
        compose.onNodeWithTag("popular_site_facebook").performClick()
        compose.runOnIdle { assertEquals("https://www.facebook.com/", visited) }
    }
}
