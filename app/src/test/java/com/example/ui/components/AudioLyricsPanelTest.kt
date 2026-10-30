package com.example.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.ui.theme.HiPlayerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioLyricsPanelTest {
    @get:Rule val compose = createComposeRule()
    @Test fun plainLyricsAreVisibleInExpandedPlayer() {
        compose.setContent { HiPlayerTheme { Box(Modifier.size(260.dp)) {
            AudioLyricsPanel("First plain line\nSecond plain line", -1, null, false, false, {})
        } } }
        compose.onNodeWithText("First plain line").assertIsDisplayed()
        compose.onNodeWithText("Second plain line").assertIsDisplayed()
    }
    @Test fun notFoundShowsFunctionalRetry() {
        var retried = false
        compose.setContent { HiPlayerTheme { Box(Modifier.size(260.dp)) {
            AudioLyricsPanel(null, -1, "Lyrics not found for this track", false, true, { retried = true })
        } } }
        compose.onNodeWithText("Lyrics not found for this track").assertIsDisplayed()
        compose.onNodeWithContentDescription("Retry lyrics").performClick()
        compose.runOnIdle { assertTrue(retried) }
    }
    @Test fun compactLoadingIsNotAnEmptyArtworkPanel() {
        compose.setContent { HiPlayerTheme { Box(Modifier.size(210.dp, 54.dp)) {
            AudioLyricsPanel(null, -1, "Searching online lyrics…", true, false, {}, compact = true)
        } } }
        compose.onNodeWithTag("audio_lyrics_status").assertExists()
        compose.onNodeWithText("Searching online lyrics…").assertExists()
        compose.onNodeWithContentDescription("Retry lyrics").assertDoesNotExist()
    }
    @Test fun compactTimedLyricsHideTimestampSyntax() {
        compose.setContent { HiPlayerTheme { Box(Modifier.size(210.dp, 54.dp)) {
            AudioLyricsPanel("[00:01]First line\n[00:02.50]Second line", 0, null, false, false, {}, compact = true)
        } } }
        compose.onNodeWithText("First line").assertIsDisplayed()
        compose.onNodeWithText("[00:01]First line").assertDoesNotExist()
    }
}
