package com.example.ui.components
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SubtitleSheetLayoutTest {
    @get:Rule val compose = createComposeRule()
    @Test @Config(qualifiers = "w360dp-h640dp") fun portraitHasReadableActions() {
        compose.setContent { SubtitleSettingsBottomSheet(emptyList(), 0, {}, {}, {}, {}, {}) }
        compose.onNodeWithText("Subtitles").assertExists()
        compose.onNodeWithText("Load subtitle file").assertExists()
        compose.onNodeWithText("Subtitle appearance").assertExists()
        compose.onNodeWithTag("load_external_subtitle_button").assertWidthIsAtLeast(280.dp)
    }
    @Test @Config(qualifiers = "w800dp-h360dp") fun landscapeRetainsTrackControls() {
        compose.setContent { SubtitleSettingsBottomSheet(emptyList(), 0, {}, {}, {}, {}, {}) }
        compose.onNodeWithText("Load subtitle file").assertExists()
        compose.onNodeWithText("Subtitle appearance").assertExists()
        compose.onNodeWithText("Subtitles Off").assertExists()
    }
}
