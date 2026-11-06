package com.example.browser.gecko

import android.content.Context
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import com.example.ui.theme.HiPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoSession
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
class GeckoPermissionDialogTest {
    @get:Rule val compose = createComposeRule()
    private class Registry : ActivityResultRegistry() {
        override fun <I : Any?, O : Any?> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) = Unit
    }
    @Test fun consentShowsOriginAndDenialCompletesRequest() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val ui = GeckoPermissionUi(GeckoRuntimePermissionLauncher(context, Registry()))
        var result: Boolean? = null
        compose.setContent { HiPlayerTheme { GeckoPermissionDialog(ui) } }
        compose.runOnIdle {
            ui.requestSite("https://example.org", GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE, true) { result = it }
        }
        compose.onNodeWithText("https://example.org").assertIsDisplayed()
        compose.onNodeWithText("Private tab").assertIsDisplayed()
        compose.onNodeWithText("Not now").performClick()
        compose.runOnIdle { assertEquals(false, result); ui.close() }
    }
}
