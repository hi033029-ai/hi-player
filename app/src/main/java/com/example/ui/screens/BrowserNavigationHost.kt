package com.example.ui.screens

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalHiPalette
import kotlinx.coroutines.delay
import kotlin.math.abs

/** Overlays, never resizes the page; switching app tabs does not close browser tabs. */
@Composable
internal fun BrowserNavigationHost(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    revealButtonBottomInset: androidx.compose.ui.unit.Dp = 12.dp,
    navigation: @Composable () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = LocalHiPalette.current
    var visible by remember { mutableStateOf(false) }
    var lastActivity by remember { mutableLongStateOf(0L) }
    val density = LocalDensity.current
    val inset = WindowInsets.navigationBars.getBottom(density)
    val edge = with(density) { 56.dp.toPx() } + inset
    val threshold = with(density) { 44.dp.toPx() }
    val keyboardVisible = WindowInsets.ime.getBottom(density) > 0
    val available = enabled && !keyboardVisible
    LaunchedEffect(available) { if (!available) visible = false }
    // One clock-driven loop: hover/synthetic page events cannot perpetually
    // restart the timeout through recomposition.
    LaunchedEffect(visible, available) {
        while (visible && available) {
            val remaining = BrowserNavigationPolicy.remainingIdleMillis(SystemClock.elapsedRealtime(), lastActivity)
            if (remaining == 0L) { visible = false; break }
            delay(remaining.coerceAtMost(250L))
        }
    }
    fun reveal() { visible = true; lastActivity = SystemClock.elapsedRealtime() }
    Box(modifier.fillMaxSize().pointerInput(available, edge, threshold) {
        if (!available) return@pointerInput
        awaitEachGesture {
            // Observe rather than consume: website scrolling, links and downloads stay usable.
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val origin = down.position
            val fromEdge = origin.y >= size.height - edge
            if (visible) lastActivity = SystemClock.elapsedRealtime()
            var revealed = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (visible && event.changes.any {
                        BrowserNavigationPolicy.isUserActivity(it.pressed, it.previousPressed, it.position != it.previousPosition)
                    }) lastActivity = SystemClock.elapsedRealtime()
                val change = event.changes.firstOrNull { it.id == down.id }
                if (fromEdge && !revealed && change != null &&
                    BrowserNavigationPolicy.isUpSwipe(origin.y - change.position.y, change.position.x - origin.x, threshold)) {
                    reveal(); revealed = true
                }
            } while (event.changes.any { it.pressed })
        }
    }) {
        content()
        if (available) {
            // Accessible alternative to a gesture, also makes the hidden navigation discoverable.
            if (!visible) IconButton(
                onClick = { reveal() },
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding()
                    .padding(end = 12.dp, bottom = revealButtonBottomInset).size(48.dp)
                    .background(palette.surface.copy(alpha = .92f), RoundedCornerShape(16.dp)),
            ) { Icon(Icons.Default.KeyboardArrowUp, "Show Videos, Music and app navigation", tint = palette.textSecondary) }
            AnimatedVisibility(
                visible = visible,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                Box(Modifier.fillMaxWidth().background(palette.background.copy(alpha = .98f)).navigationBarsPadding()) {
                    navigation()
                }
            }
        }
    }
}

internal object BrowserNavigationPolicy {
    fun remainingIdleMillis(now: Long, lastActivity: Long): Long =
        (5_000L - (now - lastActivity).coerceAtLeast(0L)).coerceIn(0L, 5_000L)
    fun isUserActivity(pressed: Boolean, previousPressed: Boolean, moved: Boolean): Boolean =
        pressed != previousPressed || (pressed && moved)
    fun isUpSwipe(up: Float, horizontal: Float, threshold: Float): Boolean =
        up >= threshold && up > abs(horizontal) * 1.5f
}
