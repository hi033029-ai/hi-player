package com.example.browser.gecko

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.ui.screens.OrbitBrowserStore
import com.example.ui.theme.HiPlayerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoView
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Compose and fake-session wiring only; this does not exercise GeckoView. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
class GeckoBrowserChromeTest {
    @get:Rule val compose = createComposeRule()
    @Test fun menuOpensPrivateTabWithoutPersistingIt() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("orbit_browser", Context.MODE_PRIVATE).edit().clear().commit()
        val store = OrbitBrowserStore(context)
        val coordinator = GeckoBrowserCoordinator<GeckoView>(GeckoOrbitTabStore(store)) { _, _ ->
            object : GeckoManagedSession<GeckoView> {
                override fun attach(view: GeckoView) = Unit
                override fun detach() = Unit
                override fun load(url: String) = Unit
                override fun reload() = Unit
                override fun stop() = Unit
                override fun back() = Unit
                override fun forward() = Unit
                override fun exitFullScreen() = Unit
                override fun purgeHistory() = Unit
                override fun setDesktop(enabled: Boolean) = Unit
                override fun close() = Unit
            }
        }
        compose.setContent { HiPlayerTheme {
            GeckoBrowserChrome(coordinator, store, listOf("Google"), "Google", {}, { it }, {}, {}, {},
                downloadsContent = { Text("Downloads") }, pageContent = { Text("Gecko page") })
        } }
        compose.onNodeWithContentDescription("Browser menu").performClick()
        compose.onNodeWithText("Sign in to Google for websites").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Scan QR code").performScrollTo()
        compose.onNodeWithText("Scan QR code").assertIsDisplayed()
        compose.onNodeWithText("New private tab").performScrollTo().performClick()
        compose.runOnIdle {
            assertTrue(coordinator.tabs.single { it.id == coordinator.activeId }.privateTab)
            assertTrue(store.savedTabs().all { it.id != coordinator.activeId })
            coordinator.close()
        }
    }

    @Test fun pageToolsLiveInTheBrowserMenuInsteadOfFloatingOverThePage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("orbit_browser", Context.MODE_PRIVATE).edit().clear().commit()
        val store = OrbitBrowserStore(context)
        var openedPageTools = false
        val coordinator = GeckoBrowserCoordinator<GeckoView>(GeckoOrbitTabStore(store)) { tab, changed ->
            object : GeckoManagedSession<GeckoView> {
                override fun attach(view: GeckoView) = Unit
                override fun detach() = Unit
                override fun load(url: String) { changed(GeckoTabSnapshot(tab.id, tab.privateTab, url)) }
                override fun reload() = Unit
                override fun stop() = Unit
                override fun back() = Unit
                override fun forward() = Unit
                override fun exitFullScreen() = Unit
                override fun purgeHistory() = Unit
                override fun setDesktop(enabled: Boolean) = Unit
                override fun close() = Unit
            }
        }
        compose.runOnIdle { coordinator.load("https://example.org") }
        compose.setContent { HiPlayerTheme {
            GeckoBrowserChrome(coordinator, store, listOf("Google"), "Google", {}, { it }, {}, {}, {},
                downloadsContent = { Text("Downloads") },
                pageContent = { Text("Gecko page") },
                onPageTools = { openedPageTools = true })
        } }

        compose.runOnIdle { assertTrue(compose.onAllNodesWithText("Page actions").fetchSemanticsNodes().isEmpty()) }
        compose.onNodeWithContentDescription("Browser menu").performClick()
        compose.onNodeWithText("Page tools").performScrollTo().performClick()
        compose.runOnIdle {
            assertTrue(openedPageTools)
            coordinator.close()
        }
    }
}
