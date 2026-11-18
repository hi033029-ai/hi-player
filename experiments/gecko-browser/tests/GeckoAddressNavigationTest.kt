package com.example.browser.gecko

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.ui.screens.OrbitBrowserStore
import com.example.ui.theme.HiPlayerTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoView
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Input/focus and handoff wiring. Real Gecko is exercised in the device suite. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
class GeckoAddressNavigationTest {
    @get:Rule val compose = createComposeRule()
    @Test fun addressSubmissionUsesHostMediaRoutingAndClearsEditingFocus() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("orbit_browser",Context.MODE_PRIVATE).edit().clear().commit()
        val store = OrbitBrowserStore(context)
        var hostUrl = ""
        var engineLoads = 0
        var backs = 0
        var exits = 0
        lateinit var activity: ComponentActivity
        val coordinator = GeckoBrowserCoordinator<GeckoView>(GeckoOrbitTabStore(store)) { tab,changed ->
            object : GeckoManagedSession<GeckoView> {
                override fun attach(view: GeckoView) = Unit
                override fun detach() = Unit
                override fun load(url: String) { engineLoads++; changed(GeckoTabSnapshot(tab.id,tab.privateTab,url,canGoBack = true)) }
                override fun reload() = Unit
                override fun stop() = Unit
                override fun back() { backs++ }
                override fun forward() = Unit
                override fun exitFullScreen() = Unit
                override fun purgeHistory() = Unit
                override fun setDesktop(enabled: Boolean) = Unit
                override fun close() = Unit
            }
        }
        compose.setContent { HiPlayerTheme {
            activity = LocalContext.current as ComponentActivity
            BackHandler { exits++ }
            GeckoBrowserShell(coordinator,store,listOf("Google"),"Google",{},{it},{},{},
                onNavigate = { hostUrl = it },pageContent = { Text("Page") })
        } }
        compose.onNodeWithTag("browser_address_input").performClick().performTextReplacement("https://example.org/movie.mkv")
        compose.onNodeWithTag("browser_address_input").performImeAction()
        compose.onNodeWithTag("browser_address_input").assertIsNotFocused()
        compose.runOnIdle { assertEquals("https://example.org/movie.mkv",hostUrl); assertEquals(0,engineLoads) }
        compose.runOnIdle { coordinator.load("https://example.org/page") }
        compose.onNodeWithTag("browser_address_input").performClick().performTextReplacement("unfinished search")
        compose.runOnIdle { activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("browser_address_input").assertIsNotFocused()
        compose.runOnIdle { assertEquals("Back while editing must not leave page",0,backs); assertEquals(0,exits) }
        compose.onNodeWithTag("browser_address_input").performClick()
        compose.onNodeWithContentDescription("Open tabs, 1 tab").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithContentDescription("Browser home").assertIsDisplayed()
        compose.onNodeWithTag("browser_address_input").assertIsNotFocused()
        compose.runOnIdle { coordinator.close() }
    }
}
