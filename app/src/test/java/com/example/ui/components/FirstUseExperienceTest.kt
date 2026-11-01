package com.example.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.HiPlayerTheme
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FirstUseExperienceTest {
    @get:Rule val compose = createComposeRule()
    @Before fun clearTips() {
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("first_use_tips", Context.MODE_PRIVATE).edit().clear().commit()
    }
    private fun screenshot(name: String) {
        val directory = System.getProperty("hi.player.qa.dir") ?: return
        val file = java.io.File(directory, "$name.png").apply { parentFile?.mkdirs() }
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        onView(isRoot()).captureRoboImage(file.absolutePath)
    }
    @Test fun systemThemeTracksConfigurationChangesWithoutReselecting() {
        val dark = mutableStateOf(true)
        compose.setContent {
            val configuration = android.content.res.Configuration(androidx.compose.ui.platform.LocalConfiguration.current).apply {
                uiMode = (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark.value) android.content.res.Configuration.UI_MODE_NIGHT_YES else android.content.res.Configuration.UI_MODE_NIGHT_NO
            }
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalConfiguration provides configuration) {
                HiPlayerTheme(themeMode = com.example.data.AppThemeMode.SYSTEM) {
                    androidx.compose.material3.Text(if (com.example.ui.theme.LocalHiPalette.current.isDark) "Dark appearance" else "Light appearance")
                }
            }
        }
        compose.onNodeWithText("Dark appearance").assertIsDisplayed()
        compose.runOnIdle { dark.value = false }
        compose.onNodeWithText("Light appearance").assertIsDisplayed()
    }
    @Test fun themePickerContainsNoDuplicateMonochromeChoices() {
        compose.setContent { HiPlayerTheme {
            HorizontalThemeSelector(com.example.data.AppThemeMode.SYSTEM, {})
        } }
        compose.onNodeWithTag("theme_card_SYSTEM").assertExists()
        compose.onNodeWithTag("theme_card_CYAN_NEON_DARK").assertDoesNotExist()
        compose.onNodeWithTag("theme_card_WARM_SUNSET_LIGHT").assertDoesNotExist()
    }
    @Test fun permissionRecoveryOnlyOpensSettingsWhenTapped() {
        var calls = 0
        compose.setContent { HiPlayerTheme { Box(Modifier.size(360.dp, 640.dp)) {
            LibraryPermissionPanel(audio = false, onOpenSettings = { calls++ })
        } } }
        assertEquals(0, calls)
        compose.onNodeWithTag("library_allow_access").assertIsDisplayed()
        screenshot("permission-recovery")
        compose.onNodeWithTag("library_allow_access").performClick()
        assertEquals(1, calls)
    }
    @Test fun scanFeedbackDisappearsImmediatelyAfterCompletion() {
        val status = mutableStateOf(LibraryScanStatus.SCANNING)
        compose.setContent { HiPlayerTheme { Box(Modifier.size(360.dp, 640.dp)) {
            LibraryScanFeedback(status.value, 12, audio = true, centered = true)
        } } }
        compose.onNodeWithTag("library_scan_circle").assertExists()
        compose.onNodeWithText("Scan complete · 12 audio tracks available").assertDoesNotExist()
        screenshot("scanning")
        compose.runOnIdle { status.value = LibraryScanStatus.COMPLETE }
        compose.onNodeWithTag("library_scan_circle").assertDoesNotExist()
        compose.onNodeWithText("Scan complete · 12 audio tracks available").assertDoesNotExist()
    }
    @Test fun visualPlayerGuidePointsToHdrAndOpensRealControls() {
        val state = FirstUseTipState(ApplicationProvider.getApplicationContext(), "visual_test")
        var opened = 0
        compose.setContent { HiPlayerTheme { Box(Modifier.size(360.dp, 640.dp)) {
            PlayerGestureCoach(state, androidx.compose.ui.geometry.Rect(100f, 400f, 148f, 448f),
                androidx.compose.ui.geometry.Offset.Zero) { opened++ }
        } } }
        repeat(3) { compose.onNodeWithText("Next").performClick() }
        compose.onNodeWithText("Here is HDR").assertIsDisplayed()
        compose.onNodeWithContentDescription("HDR button is highlighted").assertExists()
        compose.onNodeWithText("Open HDR").performClick()
        assertEquals(1, opened)
        assertFalse(state.visible)
    }
    @Test fun visualPlayerGuideCanBeSkippedWithoutOpeningHdr() {
        val state = FirstUseTipState(ApplicationProvider.getApplicationContext(), "visual_skip_test")
        var opened = false
        compose.setContent { HiPlayerTheme {
            PlayerGestureCoach(state, null, androidx.compose.ui.geometry.Offset.Zero) { opened = true }
        } }
        compose.onNodeWithText("Skip guide").performClick()
        compose.onNodeWithTag("player_gesture_coach").assertDoesNotExist()
        assertFalse(opened)
    }
    @Test fun contextualTipIsDismissibleAndRemembered() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val state = FirstUseTipState(context, "test_player")
        compose.setContent { HiPlayerTheme { FirstUseTip(state, "Video controls", "Double-tap left or right to seek.") } }
        compose.onNodeWithText("Got it").performClick()
        compose.onNodeWithTag("first_use_tip").assertDoesNotExist()
        assertFalse(FirstUseTipState(context, "test_player").visible)
    }
    @Test fun hdrHelpOnlyAppearsWhenCustomisationIsOpened() {
        val opened = mutableStateOf(false)
        compose.setContent { HiPlayerTheme { Box(Modifier.size(360.dp, 640.dp)) {
            com.example.ui.HdrPresetMenu(visible = opened.value, selected = com.example.player.ColorPresets.CUSTOM,
                onSelect = {}, customizing = true)
        } } }
        compose.onNodeWithText("Adjust only what you choose").assertDoesNotExist()
        compose.runOnIdle { opened.value = true }
        compose.onNodeWithText("Adjust only what you choose").assertIsDisplayed()
        screenshot("hdr-customisation-help")
        compose.onNodeWithText("Got it").performClick()
        compose.onNodeWithText("Whole picture").assertIsDisplayed()
    }
    @Test fun skippingPermissionsFinishesSetupWithoutAnotherSetupPage() {
        var finished = 0
        compose.setContent { HiPlayerTheme { SplashScreen(true, { _, _ -> finished++ }) } }
        compose.onNodeWithTag("welcome_continue_button").performClick()
        compose.onNodeWithTag("theme_proceed_button").performClick()
        compose.onNodeWithText("Not now").performClick()
        assertEquals(1, finished)
        compose.onNodeWithTag("onboarding_text_size_slider").assertDoesNotExist()
    }
    @Test fun welcomeSkipEntersAppWithoutRequestingPermissions() {
        var skipped = 0
        compose.setContent { HiPlayerTheme { SplashScreen(true, { _, _ -> }, onSkip = { skipped++ }) } }
        compose.onNodeWithTag("welcome_skip_button").assertIsDisplayed()
        screenshot("welcome")
        compose.onNodeWithText("Make it yours").performScrollTo().assertIsDisplayed()
        screenshot("welcome-features")
        compose.onNodeWithTag("welcome_skip_button").performClick()
        assertEquals(1, skipped)
    }
}
