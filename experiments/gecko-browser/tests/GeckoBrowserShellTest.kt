package com.example.browser.gecko

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.ui.screens.OrbitBrowserEntry
import com.example.ui.screens.OrbitBrowserStore
import com.example.ui.theme.HiPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoView
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Fake session and fake surface: UI wiring only, not a Gecko device test. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
class GeckoBrowserShellTest {
    @get:Rule val compose = createComposeRule()

    @Test fun shortcutLoadsPageAndReloadUsesActiveSession() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("orbit_browser", Context.MODE_PRIVATE).edit().clear().commit()
        val store = OrbitBrowserStore(context)
        store.saveShortcuts(listOf(OrbitBrowserEntry("Example", "https://example.org")))
        var loaded = ""
        var reloads = 0
        val coordinator = GeckoBrowserCoordinator<GeckoView>(GeckoOrbitTabStore(store)) { tab, changed ->
            object : GeckoManagedSession<GeckoView> {
                override fun attach(view: GeckoView) = Unit
                override fun detach() = Unit
                override fun load(url: String) { loaded = url; changed(GeckoTabSnapshot(tab.id, tab.privateTab, url, "Example")) }
                override fun reload() { reloads++ }
                override fun stop() = Unit
                override fun back() = Unit
                override fun forward() = Unit
                override fun setDesktop(enabled: Boolean) = Unit
                override fun close() = Unit
            }
        }
        compose.setContent { HiPlayerTheme {
            GeckoBrowserShell(coordinator, store, listOf("Google"), "Google", {}, { it }, {}, {},
                pageContent = { Text("Gecko page") })
        } }
        compose.onNodeWithText("Example").performClick()
        compose.onNodeWithText("Gecko page").assertIsDisplayed()
        compose.onNodeWithContentDescription("Reload").performClick()
        compose.runOnIdle {
            assertEquals("https://example.org", loaded)
            assertEquals(1, reloads)
            coordinator.close()
        }
    }
}
