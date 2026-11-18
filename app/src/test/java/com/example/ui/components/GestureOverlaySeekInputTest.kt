package com.example.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GestureOverlaySeekInputTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun horizontalSwipeReachesScrubCallbacks() {
        var scrubStarted = false
        var scrubMoved = false
        var scrubEnded = false
        compose.setContent {
            GestureOverlay(
                isLocked = false,
                onSingleTap = {},
                onDoubleTapLeft = {},
                onDoubleTapCenter = {},
                onDoubleTapRight = {},
                onBrightnessDelta = {},
                onVolumeDelta = {},
                onScrubStart = { scrubStarted = true },
                onScrubMove = { scrubMoved = true },
                onScrubEnd = { scrubEnded = true },
                brightnessLevel = null,
                volumeLevel = null,
                scrubTimeMs = null,
                scrubDeltaMs = 0L,
                topExclusionDp = 0.dp,
                modifier = Modifier.testTag("gesture_overlay_seek_test"),
            )
        }

        compose.onNodeWithTag("gesture_overlay_seek_test").performTouchInput {
            down(Offset(width * 0.2f, height * 0.5f))
            moveTo(Offset(width * 0.8f, height * 0.5f), delayMillis = 100L)
            up()
        }

        compose.runOnIdle {
            assertTrue("horizontal drag should start a scrub", scrubStarted)
            assertTrue("horizontal drag should update the preview", scrubMoved)
            assertTrue("a normal release should commit the scrub", scrubEnded)
        }
    }
}
