package com.example.ui.components

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w800dp-h360dp")
class VideoEqualizerSheetTest {
    @get:Rule val compose = createComposeRule()

    @Test fun customBandAndDismissCallbacksRemainAvailable() {
        var band = -1
        var gain = 0f
        var dismissed = false
        compose.setContent {
            EqualizerBottomSheet("Custom", onSelectPreset = {},
                onCustomBandChange = { i, v -> band = i; gain = v }, onDismiss = { dismissed = true })
        }
        compose.onNodeWithContentDescription("Low band gain")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(3f) }
        compose.onNodeWithText("Done").performClick()
        compose.runOnIdle { assertEquals(0, band); assertEquals(3f, gain, 0.01f); assertEquals(true, dismissed) }
    }
}
