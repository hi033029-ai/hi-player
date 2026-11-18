package com.example.ui.components

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.model.AspectRatioMode
import com.example.ui.theme.HiPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerTransportActionsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun fileNavigationAndTenSecondSeekingHaveSeparateCallbacks() {
        val actions = mutableListOf<String>()
        compose.setContent {
            HiPlayerTheme {
                ControlsOverlay(
                    isVisible = true,
                    isLocked = false,
                    showLockedUnlockControl = false,
                    isPlaying = false,
                    isBuffering = false,
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
                )
            }
        }

        compose.onNodeWithTag("previous_file_button").assertHasClickAction().performClick()
        compose.onNodeWithContentDescription("Rewind 10 seconds").assertHasClickAction().performClick()
        compose.onNodeWithContentDescription("Play").assertHasClickAction().performClick()
        compose.onNodeWithContentDescription("Forward 10 seconds").assertHasClickAction().performClick()
        compose.onNodeWithTag("next_file_button").assertHasClickAction().performClick()

        compose.runOnIdle {
            assertEquals(
                listOf("previousVideo", "rewind10", "play", "forward10", "nextVideo"),
                actions,
            )
        }
    }
}
