package com.example.browser.gecko

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.example.BuildConfig
import com.example.ui.theme.HiPlayerTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
class GeckoProtectionControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun switchesSaveAutomaticProtectionPreferences() {
        var saved: Triple<Boolean, Boolean, Boolean>? = null
        val settings = GeckoBrowserSettings(false, 100, true, { _, _, _ -> }, { _, _ -> }, { },
            initialAdBlock = true, initialPageSkipper = true, initialYoutubeAdSkip = true,
            saveContentPreferences = { block, wait, youtube, _ -> saved = Triple(block, wait, youtube) })
        compose.setContent { HiPlayerTheme { Column { GeckoProtectionControls(settings) } } }
        compose.onNodeWithContentDescription("Ad and tracker blocking").performClick()
        compose.runOnIdle { assertEquals(Triple(false, true, true), saved); assertTrue(settings.contentNeedsReload) }
        if (BuildConfig.PLAY_STORE_BUILD) {
            compose.onNodeWithContentDescription("Skip supported wait pages").assertIsNotEnabled()
            compose.onNodeWithContentDescription("Skip supported YouTube ads").assertIsNotEnabled()
        } else {
            compose.onNodeWithContentDescription("Skip supported wait pages").performClick()
            compose.onNodeWithContentDescription("Skip supported YouTube ads").performClick()
            compose.runOnIdle { assertEquals(Triple(false, false, false), saved) }
        }
    }
}
