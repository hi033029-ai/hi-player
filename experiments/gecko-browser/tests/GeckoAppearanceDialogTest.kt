package com.example.browser.gecko

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ui.theme.HiPlayerTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
class GeckoAppearanceDialogTest {
    @get:Rule val compose = createComposeRule()
    @Test fun togglesApplyWithoutReloadingPage() {
        var appearanceChanges = 0
        var popup = true
        val settings = GeckoBrowserSettings(false, 100, true, { _, _, _ -> appearanceChanges++ }, { _, _ -> }, { popup = it })
        var dismissed = false
        compose.setContent { HiPlayerTheme { GeckoAppearanceDialog(settings) { dismissed = true } } }
        compose.onNodeWithContentDescription("Dark web pages").performClick()
        compose.onNodeWithContentDescription("Block pop-ups").performClick()
        compose.runOnIdle { assertTrue(settings.dark); assertFalse(popup); assertEquals(1, appearanceChanges) }
        compose.onNodeWithText("Done").performClick()
        compose.runOnIdle { assertTrue(dismissed) }
    }
}
