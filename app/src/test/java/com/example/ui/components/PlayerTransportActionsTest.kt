package com.example.ui.components

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.model.AspectRatioMode
import com.example.ui.theme.HiPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
class PlayerTransportActionsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun portraitKeepsCoreTransportClearOfGestureLanes() = exerciseTransport(expectWideTransport = false)

    @Test @Config(qualifiers = "w800dp-h360dp")
    fun landscapeKeepsTransportAndUtilitiesSeparate() = exerciseTransport(expectWideTransport = true)

    @Test
    fun enlargedTextRetainsEveryTransportAction() = exerciseTransport(1.5f, expectWideTransport = false)

    private fun exerciseTransport(textScale: Float = 1f, expectWideTransport: Boolean) {
        val actions = mutableListOf<String>()
        compose.setContent {
            HiPlayerTheme(uiTextScale = textScale) {
                ControlsOverlay(
                    isVisible = true,
                    isLocked = false,
                    showLockedUnlockControl = false,
                    isPlaying = false,
                    currentPositionMs = 20_000L,
                    durationMs = 120_000L,
                    bufferedPositionMs = 30_000L,
                    video = null,
                    aspectRatioMode = AspectRatioMode.FIT,
                    playbackSpeed = 1f,
                    isBgPlayActive = false,
                    onTogglePlayPause = { actions += "play" },
                    onSeekTo = {},
                    onRewind10 = { actions += "rewind10" },
                    onFastForward10 = { actions += "forward10" },
                    onPrevious = { actions += "previousVideo" },
                    onNext = { actions += "nextVideo" },
                    onBack = {},
                    onToggleLock = {},
                    onRotateScreen = {},
                    onZoomIn = {},
                    onZoomOut = {},
                    onEnterPip = {},
                    onToggleBgPlay = {},
                    onOpenFile = {},
                    onToggleSubtitles = {},
                    onOpenAudioSettings = {},
                    onOpenEqualizer = {},
                    onOpenVideoSettings = {},
                    onOpenTelemetry = {},
                    onCycleAspectRatio = {},
                    onCycleSpeed = {},
                    modifier = Modifier.testTag("controls_root"),
                )
            }
        }

        val coreTags = listOf("rewind_10_button", "bottom_play_pause_button", "forward_10_button")
        val adjacentTags = listOf("previous_file_button", "next_file_button")
        val tags = if (expectWideTransport) {
            listOf("previous_file_button", "rewind_10_button", "bottom_play_pause_button",
                "forward_10_button", "next_file_button")
        } else coreTags
        val root = compose.onNodeWithTag("controls_root").fetchSemanticsNode().boundsInRoot
        val allTargets = tags + listOf("bottom_lock_button", "bottom_aspect_button",
            "bottom_subtitles_cc_button", "bottom_audio_tracks_button")
        val bounds = allTargets.map { compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }
        bounds.forEachIndexed { i, rect ->
            assertTrue("${allTargets[i]} was clipped", rect.left >= root.left && rect.right <= root.right &&
                rect.top >= root.top && rect.bottom <= root.bottom)
            bounds.drop(i + 1).forEach { other ->
                assertTrue("Control touch targets overlap", !rect.overlaps(other))
            }
        }
        tags.forEach {
            compose.onNodeWithTag(it).assertHasClickAction().performTouchInput { click() }
        }

        if (expectWideTransport) {
            compose.onNodeWithTag("bottom_previous_file_button").assertDoesNotExist()
            compose.onNodeWithTag("bottom_next_file_button").assertDoesNotExist()
        } else {
            adjacentTags.forEach { compose.onNodeWithTag(it).assertDoesNotExist() }
            compose.onNodeWithTag("bottom_previous_file_button")
                .performScrollTo().assertHasClickAction().performClick()
            compose.onNodeWithTag("bottom_next_file_button")
                .performScrollTo().assertHasClickAction().performClick()
        }

        compose.runOnIdle {
            assertEquals(
                if (expectWideTransport) {
                    listOf("previousVideo", "rewind10", "play", "forward10", "nextVideo")
                } else {
                    listOf("rewind10", "play", "forward10", "previousVideo", "nextVideo")
                },
                actions,
            )
        }
    }
}
