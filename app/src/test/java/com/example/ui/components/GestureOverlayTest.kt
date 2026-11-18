package com.example.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w720dp-h360dp-mdpi")
class GestureOverlayTest {
    @get:Rule val compose = createComposeRule()
    private var seekStarts = 0
    private var seekEnds = 0
    private var cancels = 0
    private var seek = 0L
    private var brightness = 0f
    private var volume = 0f
    private var taps = 0
    private var doubles = 0
    private var zoom = 1f
    private fun show(locked: Boolean = false) {
        compose.setContent { Box(Modifier.size(720.dp, 360.dp)) {
            GestureOverlay(isLocked = locked, onSingleTap = { taps++ },
                onDoubleTapLeft = { doubles-- }, onDoubleTapCenter = { doubles += 10 }, onDoubleTapRight = { doubles++ },
                onBrightnessDelta = { brightness += it }, onVolumeDelta = { volume += it },
                onScrubStart = { seekStarts++ }, onScrubMove = { seek = it }, onScrubEnd = { seekEnds++ },
                onScrubCancel = { cancels++ }, brightnessLevel = null, volumeLevel = null,
                scrubTimeMs = null, scrubDeltaMs = 0, onPinchZoom = { zoom *= it },
                modifier = Modifier.testTag("gestures"))
        } }
    }
    @Test fun ordinaryOneFingerSwipesReachAllThreeControls() {
        show()
        compose.onNodeWithTag("gestures").performTouchInput { swipe(Offset(200f,170f),Offset(500f,174f),400) }
        compose.runOnIdle { assertEquals(1,seekStarts); assertEquals(1,seekEnds); assertTrue(seek > 30_000) }
        compose.onNodeWithTag("gestures").performTouchInput { swipe(Offset(150f,280f),Offset(152f,110f),400) }
        compose.onNodeWithTag("gestures").performTouchInput { swipe(Offset(570f,280f),Offset(568f,110f),400) }
        compose.runOnIdle {
            assertTrue("Left vertical swipe lost to pinch recognizer", brightness > .4f)
            assertTrue("Right vertical swipe lost to pinch recognizer", volume > .4f)
            assertEquals(1,seekEnds); assertEquals(0,cancels); assertEquals(1f,zoom,0f)
        }
    }
    @Test fun cancelledSeekDoesNotCommitAndNextSwipeStillWorks() {
        show()
        compose.onNodeWithTag("gestures").performTouchInput {
            down(Offset(250f,180f)); moveTo(Offset(330f,180f)); moveTo(Offset(400f,180f))
        }
        compose.runOnIdle { assertEquals("A real seek must be active before cancellation",1,seekStarts) }
        compose.onNodeWithTag("gestures").performTouchInput { cancel() }
        compose.runOnIdle { assertEquals("Cancelled preview must be discarded",1,cancels); assertEquals(0,seekEnds) }
        compose.onNodeWithTag("gestures").performTouchInput { swipe(Offset(400f,180f),Offset(200f,180f),300) }
        compose.runOnIdle { assertEquals(1,seekEnds); assertTrue(seek < 0) }
    }
    @Test fun pinchDoesNotCommitASeekAndLockedSwipesDoNothing() {
        show()
        compose.onNodeWithTag("gestures").performTouchInput {
            down(0,Offset(250f,180f)); down(1,Offset(450f,180f))
            updatePointerTo(0,Offset(180f,180f)); updatePointerTo(1,Offset(520f,180f)); move()
            up(0); up(1)
        }
        compose.runOnIdle { assertTrue(zoom > 1.2f); assertEquals(0,seekEnds); assertEquals(0f,volume,0f) }
    }
    @Test fun lockedSwipesCannotChangePlaybackButTapCanUnlock() {
        show(locked = true)
        compose.onNodeWithTag("gestures").performTouchInput { swipe(Offset(200f,200f),Offset(500f,200f),300) }
        compose.onNodeWithTag("gestures").performTouchInput { click(Offset(200f,200f)) }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertEquals(0,seekStarts); assertEquals(0f,brightness,0f); assertEquals(1,taps) }
    }
    @Test fun doubleTapSeeksWithoutAlsoTogglingControls() {
        show()
        compose.onNodeWithTag("gestures").performTouchInput { doubleClick(Offset(590f,180f)) }
        compose.mainClock.advanceTimeBy(600)
        compose.runOnIdle { assertEquals(1,doubles); assertEquals(0,taps); assertEquals(0,seekStarts) }
    }
}
