package com.example.browser.gecko

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.screens.OrbitBrowserFilterEngine
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeckoChromeDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test(timeout = 150_000) fun addressTabsBookmarksAndPlayerHandoffThroughRealAppBrowser() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("orbit_browser",Context.MODE_PRIVATE).edit().clear().commit()
        context.filesDir.resolve("orbit-filters.txt").writeText("/adserver/\n")
        OrbitBrowserFilterEngine.refreshPlayFilters(context)
        FixtureServer(context).use { server ->
            ActivityScenario.launch(GeckoChromeTestActivity::class.java).use { scenario ->
                lateinit var activity: GeckoChromeTestActivity
                scenario.onActivity { activity = it }
                fun pageIs(test: (GeckoTabSnapshot) -> Boolean): Boolean {
                    var matches = false
                    scenario.onActivity { current -> current.owner.acquire()?.coordinator?.activeSnapshot?.let { matches = test(it) } }
                    return matches
                }
                compose.onNodeWithTag("browser_address_input").performClick().performTextReplacement(server.root+"/page")
                compose.onNodeWithTag("browser_address_input").performImeAction()
                compose.waitUntil(90_000) { pageIs { it.title == "Integrated Gecko ready" && !it.loading } }
                compose.onNodeWithTag("browser_address_input").assertIsNotFocused()
                compose.onNodeWithContentDescription("Browser menu").performClick()
                compose.onNodeWithContentDescription("Add bookmark").performClick()
                compose.onNodeWithContentDescription("Open tabs, 1 tab").performClick()
                compose.onNodeWithContentDescription("New tab").performClick()
                compose.onNodeWithText("Hi Browser").assertIsDisplayed()
                compose.onNodeWithContentDescription("Open tabs, 2 tabs").performClick()
                compose.onNodeWithTag("browser_tab_1").performClick()
                compose.waitUntil(20_000) { pageIs { it.url == server.root+"/page" } }
                compose.onNodeWithContentDescription("Browser home").performClick()
                // The home surface and its bookmark row are composed after the
                // Gecko tab snapshot switches to the blank start page. Waiting
                // for that observable UI state avoids racing the transition on
                // slower emulators and mirrors what a user can actually tap.
                compose.waitUntil(20_000) {
                    compose.onAllNodesWithTag("browser_bookmark_0")
                        .fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithTag("browser_bookmark_0").performClick()
                compose.waitUntil(20_000) { pageIs { it.url == server.root+"/page" } }
                compose.onNodeWithTag("browser_address_input").performClick().performTextReplacement(server.root+"/video.mp4")
                compose.onNodeWithTag("browser_address_input").performImeAction()
                compose.waitUntil(5_000) { activity.playedUrl == server.root+"/video.mp4" }
                assertNotNull("Direct media must reach Hi Player from the address bar",activity.playedUrl)
            }
        }
    }
}
