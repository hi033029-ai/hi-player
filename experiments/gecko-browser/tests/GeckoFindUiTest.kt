package com.example.browser.gecko

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
class GeckoFindUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun menuSearchesCurrentTabAndClosesFindBar() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("orbit_browser", Context.MODE_PRIVATE).edit().clear().commit()
        val store = OrbitBrowserStore(context)
        var clears = 0
        var lastQuery: String? = null
        lateinit var update: (GeckoTabSnapshot) -> Unit
        val coordinator = GeckoBrowserCoordinator<GeckoView>(GeckoOrbitTabStore(store)) { _, callback ->
            update = callback
            object : GeckoManagedSession<GeckoView>, GeckoFindable {
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
                override fun find(query: String?, backwards: Boolean, completion: (GeckoFindResult?) -> Unit) { lastQuery = query; completion(GeckoFindResult(true, 1, 3)) }
                override fun clearFind() { clears++ }
                override fun close() = Unit
            }
        }
        compose.setContent { HiPlayerTheme {
            GeckoBrowserChrome(coordinator, store, listOf("Google"), "Google", {}, { it }, {}, {}, {},
                downloadsContent = { Text("Downloads") }, pageContent = { Text("Gecko page") })
        } }
        compose.runOnIdle { update(GeckoTabSnapshot(1, false, "https://example.org")) }
        compose.onNodeWithContentDescription("Browser menu").performClick()
        compose.onNodeWithText("Find in page").performClick()
        // The find input and address input both accept text; use the placeholder to identify the find field.
        compose.onNode(hasSetTextAction() and androidx.compose.ui.test.hasAnyDescendant(androidx.compose.ui.test.hasText("Find in page")), useUnmergedTree = true).performTextInput("hello")
        compose.onNodeWithText("1/3").assertExists()
        compose.runOnIdle { assertEquals("hello", lastQuery) }
        compose.onNodeWithContentDescription("Close find in page").performClick()
        compose.runOnIdle { assertEquals(1, clears); coordinator.close() }
    }
}
