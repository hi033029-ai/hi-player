package com.example.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.example.ui.theme.HiPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerDeviceStatusTest {
    @get:Rule val compose = createComposeRule()

    @Test fun computesBatteryPercentageWithoutGuessingMissingReadings() {
        assertEquals(46, batteryPercentage(46, 100))
        assertEquals(50, batteryPercentage(1, 2))
        assertEquals(100, batteryPercentage(110, 100))
        assertNull(batteryPercentage(-1, 100))
        assertNull(batteryPercentage(10, 0))
    }

    @Test fun showsTimeBatteryAndWifiState() {
        compose.setContent { HiPlayerTheme { PlayerDeviceStatusContent("2:24", 46, true) } }
        compose.onNodeWithText("2:24").assertIsDisplayed()
        compose.onNodeWithContentDescription("Battery 46 percent").assertIsDisplayed()
        compose.onNodeWithContentDescription("Wi-Fi in use").assertIsDisplayed()
    }
}
