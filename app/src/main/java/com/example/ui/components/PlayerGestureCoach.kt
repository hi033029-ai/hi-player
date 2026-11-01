package com.example.ui.components

import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Player-only, skippable demonstration. The HDR marker follows the actual measured control. */
@Composable
fun PlayerGestureCoach(state: FirstUseTipState, hdrBounds: Rect?, rootOrigin: Offset, onOpenHdr: () -> Unit) {
    if (!state.visible) return
    var step by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    val animate = remember(context) { runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }.getOrDefault(true) }
    val transition = rememberInfiniteTransition(label = "gesture_demo")
    val motion by transition.animateFloat(0f, 1f,
        infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "hand_motion")
    val phase = if (animate) motion else 0.5f
    val titles = listOf("Swipe between library views", "Double-tap to seek", "Brightness and volume", "Here is HDR")
    val descriptions = listOf(
        "In Videos or Music, swipe left or right across the library to change its view. You can also tap its tabs.",
        "In the video player, double-tap the left or right side to seek 10 seconds. Double-tap the centre to play or pause.",
        "While watching, swipe up or down on the left for brightness, or on the right for volume.",
        "Tap the highlighted HDR button for picture presets. Choose Customize there for whole-picture and individual-colour controls. Device HDR support varies."
    )
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.76f))
        .pointerInput(Unit) { detectTapGestures {} }.testTag("player_gesture_coach")) {
        val viewportHeight = maxHeight
        val target = hdrBounds?.translate(-rootOrigin.x, -rootOrigin.y)
        if (step == 3 && target != null && target.width > 0 && target.height > 0) {
            Canvas(Modifier.fillMaxSize()) {
                val gap = 5.dp.toPx()
                drawRoundRect(Color(0xFF35D4DD), Offset(target.left - gap, target.top - gap),
                    androidx.compose.ui.geometry.Size(target.width + gap * 2, target.height + gap * 2),
                    CornerRadius(12.dp.toPx()), style = Stroke(3.dp.toPx()))
            }
            Icon(Icons.Default.TouchApp, "HDR button is highlighted", tint = Color.White,
                modifier = Modifier.offset {
                    androidx.compose.ui.unit.IntOffset(
                        (target.center.x - with(density) { 22.dp.toPx() }).toInt().coerceAtLeast(0),
                        (target.top - with(density) { 52.dp.toPx() }).toInt().coerceAtLeast(0))
                }.size(44.dp).graphicsLayer { alpha = 0.75f + phase * 0.25f })
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(color = Color(0xF21B252D), shape = RoundedCornerShape(12.dp),
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().weight(1f, fill = false)
                    .heightIn(max = if (step == 3) viewportHeight * 0.5f else viewportHeight * 0.6f)
                    .verticalScroll(rememberScrollState())) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${step + 1} of 4 · Player guide", color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp)
                    Text(titles[step], color = Color.White, fontSize = 20.sp)
                    Text(descriptions[step], color = Color.White, fontSize = 14.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = state::dismiss) { Text("Skip guide", color = Color.White) }
                        Button(onClick = {
                            if (step < 3) step++ else { state.dismiss(); onOpenHdr() }
                        }, enabled = step < 3 || target != null) {
                            Text(if (step == 3) "Open HDR" else "Next")
                        }
                    }
                }
            }
            if (step < 3) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (step == 0) "←     →" else if (step == 2) "↑     ↓" else "Tap · tap",
                            color = Color.White, fontSize = 28.sp)
                        Icon(Icons.Default.TouchApp, null, tint = Color(0xFF35D4DD),
                            modifier = Modifier.size(64.dp).graphicsLayer {
                                translationX = if (step == 0) (phase - 0.5f) * 120.dp.toPx() else 0f
                                translationY = if (step == 2) (phase - 0.5f) * 80.dp.toPx() else 0f
                                alpha = if (step == 1) 0.55f + phase * 0.45f else 1f
                            })
                        Text("Demonstration", color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp,
                            textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}
