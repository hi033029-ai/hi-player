package com.example.browser.gecko

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.example.ui.theme.HiPlayerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
class GeckoAppearanceControlsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun appearanceIsAvailableInlineAndPersists() {
        var savedDark = false
        val settings = GeckoBrowserSettings(false, 100, true, { _, _, _ -> },
            { dark, _ -> savedDark = dark }, { })
        compose.setContent { HiPlayerTheme { Column { GeckoAppearanceControls(settings) } } }
        compose.onNodeWithContentDescription("Webpage text size").assertIsDisplayed()
        compose.onNodeWithContentDescription("Dark web pages").performClick()
        compose.runOnIdle { assertTrue(savedDark); assertTrue(settings.dark) }
    }
}
