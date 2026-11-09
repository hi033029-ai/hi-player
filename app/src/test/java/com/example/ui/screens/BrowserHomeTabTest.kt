package com.example.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.material3.Text
import com.example.ui.components.HiBottomNavigationBar
import com.example.ui.components.NavTab
import com.example.ui.theme.HiPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BrowserHomeTabTest {
    @get:Rule val compose = createComposeRule()

    @Test fun homeTabOpensTheExistingHomeDestination() {
        var selected: NavTab? = null
        compose.setContent {
            HiPlayerTheme {
                HiBottomNavigationBar(
                    currentTab = NavTab.BROWSER,
                    onTabSelected = { selected = it },
                    onOpenHubSheet = {},
                )
            }
        }

        compose.onNodeWithText("Home").assertIsDisplayed().performClick()
        compose.onNodeWithText("Videos").assertDoesNotExist()
        compose.runOnIdle { assertEquals(NavTab.VIDEOS, selected) }
    }

    @Test fun hiddenBrowserNavigationHasALabeledRevealControl() {
        compose.setContent {
            HiPlayerTheme {
                BrowserNavigationHost(
                    navigation = { Text("Home tab shown") },
                    content = { Text("Browser page") },
                )
            }
        }

        compose.onNodeWithText("Home / tabs").assertIsDisplayed().performClick()
        compose.onNodeWithText("Home tab shown").assertIsDisplayed()
    }
}
