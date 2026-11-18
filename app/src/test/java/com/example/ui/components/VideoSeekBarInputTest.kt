package com.example.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoSeekBarInputTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun tapSeeksToTheTappedFraction() {
        var soughtPosition by mutableLongStateOf(-1L)
        compose.setContent {
            Box(Modifier.size(width = 300.dp, height = 48.dp)) {
                HiVideoSeekBar(
                    currentPositionMs = 0L,
                    durationMs = 10_000L,
                    bufferedPositionMs = 0L,
                    onSeekTo = { soughtPosition = it },
                    modifier = Modifier.fillMaxWidth().testTag("seek_bar_input_test"),
                )
            }
        }

        compose.onNodeWithTag("seek_bar_input_test").performTouchInput {
            click(Offset(width * 0.75f, height / 2f))
        }

        compose.runOnIdle { assertEquals(7_500L, soughtPosition) }
    }

    @Test
    fun horizontalSwipeCommitsTheFinalFraction() {
        var soughtPosition by mutableLongStateOf(-1L)
        compose.setContent {
            Box(Modifier.size(width = 300.dp, height = 48.dp)) {
                HiVideoSeekBar(
                    currentPositionMs = 0L,
                    durationMs = 10_000L,
                    bufferedPositionMs = 0L,
                    onSeekTo = { soughtPosition = it },
                    modifier = Modifier.fillMaxWidth().testTag("seek_bar_input_test"),
                )
            }
        }

        compose.onNodeWithTag("seek_bar_input_test").performTouchInput {
            down(Offset(width * 0.2f, height / 2f))
            moveTo(Offset(width * 0.8f, height / 2f), delayMillis = 100L)
            up()
        }

        compose.runOnIdle { assertEquals(8_000L, soughtPosition) }
    }
}
