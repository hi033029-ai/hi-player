package com.example.ui.components

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.audio.AudioEqBand
import com.example.audio.AudioEqState
import com.example.ui.theme.HiPlayerTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
class MusicEqualizerDialogTest {
    @get:Rule val compose = createComposeRule()
    @Test fun exposesActualBandControlAndNoFakeCinemaProfile() {
        var band = -1; var gain = 0f
        compose.setContent { HiPlayerTheme {
            MusicEqualizerDialog(AudioEqState(enabled = true, available = true, bands = listOf(AudioEqBand(0, 1000, 0f))),
                {}, {}, { i, value -> band = i; gain = value }, {}, {}, {})
        } }
        compose.onNodeWithText("Cinema 3D").assertDoesNotExist()
        compose.onNodeWithContentDescription("1.0 kHz gain").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(3f) }
        compose.runOnIdle { assertEquals(0, band); assertEquals(3f, gain, 0.01f) }
    }
}
