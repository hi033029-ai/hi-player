package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.audio.AudioEqState
import com.example.audio.EqualizerCurves
import com.example.ui.theme.LocalHiPalette
import com.example.ui.theme.contrastContentColor
import kotlin.math.roundToInt

/**
 * Compact equalizer: presets in rows of three, device bands as vertical
 * sliders side by side, and a single-line bass control. Dragging a band
 * switches the preset to Custom in the view model.
 */
@Composable
fun MusicEqualizerDialog(
    state: AudioEqState, onEnabled: (Boolean) -> Unit, onPreset: (String) -> Unit,
    onBand: (Int, Float) -> Unit, onBass: (Int) -> Unit, onReset: () -> Unit, onDismiss: () -> Unit,
) {
    val palette = LocalHiPalette.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(Modifier.widthIn(max = 340.dp), shape = RoundedCornerShape(22.dp), color = palette.surfaceElevated) {
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp).testTag("music_equalizer_dialog"),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Equalizer", color = palette.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(if (state.enabled) state.preset else "Off", color = palette.textSecondary, fontSize = 11.sp)
                    }
                    Switch(checked = state.enabled, onCheckedChange = onEnabled,
                        modifier = Modifier.semantics { contentDescription = "Enable music equalizer" })
                }

                // Presets: three per row.
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    EqualizerCurves.presets.keys.toList().chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { name ->
                                PresetPill(name, selected = state.enabled && state.preset == name,
                                    onClick = { onPreset(name) }, modifier = Modifier.weight(1f))
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }

                if (state.available && state.bands.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(palette.surface)
                        .padding(horizontal = 6.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        state.bands.forEach { band ->
                            val label = if (band.frequencyHz >= 1000) {
                                val k = band.frequencyHz / 1000f
                                if (k >= 10f) "${k.roundToInt()}k" else "${(k * 10).roundToInt() / 10f}k"
                            } else "${band.frequencyHz}"
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${if (band.gainDb > 0) "+" else ""}${band.gainDb.roundToInt()}",
                                    color = palette.textPrimary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                VerticalGainSlider(value = band.gainDb, min = state.minDb, max = state.maxDb,
                                    onValueChange = { onBand(band.index, it) }, label = if (band.frequencyHz >= 1000) "${band.frequencyHz / 1000f} kHz gain" else "${band.frequencyHz} Hz gain",
                                    activeTop = palette.secondary, activeBottom = palette.primary,
                                    track = palette.surfaceBorder,
                                    modifier = Modifier.padding(vertical = 4.dp).width(28.dp).height(120.dp))
                                Text(label, color = palette.textSecondary, fontSize = 10.sp, maxLines = 1)
                            }
                        }
                    }
                } else {
                    Text(state.status, color = palette.textSecondary, fontSize = 11.sp)
                }

                if (state.bassSupported) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Bass", color = palette.textPrimary, fontSize = 12.sp, modifier = Modifier.width(40.dp))
                        Slider(value = state.bassStrength.toFloat(), onValueChange = { onBass(it.roundToInt()) },
                            valueRange = 0f..1000f,
                            modifier = Modifier.weight(1f).height(28.dp).semantics { contentDescription = "Bass strength" },
                            colors = SliderDefaults.colors(thumbColor = palette.primary, activeTrackColor = palette.primary))
                        Text("${state.bassStrength / 10}%", color = palette.textSecondary, fontSize = 11.sp,
                            modifier = Modifier.width(38.dp).padding(start = 6.dp))
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onReset) { Text("Reset", color = palette.primary) }
                    TextButton(onClick = onDismiss) { Text("Done", color = palette.primary) }
                }
            }
        }
    }
}

@Composable
private fun PresetPill(name: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalHiPalette.current
    val shape = RoundedCornerShape(12.dp)
    Box(modifier.height(34.dp).clip(shape)
        .then(if (selected) Modifier.background(palette.primary) else Modifier.border(1.dp, palette.surfaceBorder, shape))
        .clickable(onClick = onClick)
        .semantics { role = Role.Button; this.selected = selected; contentDescription = "$name preset" },
        contentAlignment = Alignment.Center) {
        Text(name, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) contrastContentColor(palette.primary, palette.surfaceElevated) else palette.textPrimary,
            modifier = Modifier.padding(horizontal = 4.dp))
    }
}

/** Vertical slider centred on 0 dB; fill grows up for boosts and down for cuts. */
@Composable
private fun VerticalGainSlider(
    value: Float, min: Float, max: Float, onValueChange: (Float) -> Unit, label: String,
    activeTop: Color, activeBottom: Color, track: Color, modifier: Modifier = Modifier,
) {
    val range = (max - min).takeIf { it > 0f } ?: 1f
    fun valueAt(y: Float, height: Float): Float =
        (max - (y / height).coerceIn(0f, 1f) * range).coerceIn(min, max)
    Canvas(modifier
        .semantics {
            contentDescription = label
            progressBarRangeInfo = ProgressBarRangeInfo(value, min..max)
            setProgress { target -> onValueChange(target.coerceIn(min, max)); true }
        }
        .pointerInput(min, max) { detectTapGestures { onValueChange(valueAt(it.y, size.height.toFloat())) } }
        .pointerInput(min, max) {
            detectVerticalDragGestures { change, _ ->
                change.consume(); onValueChange(valueAt(change.position.y, size.height.toFloat()))
            }
        }) {
        val thumbR = 7.dp.toPx()
        val w = 4.dp.toPx()
        val x = (size.width - w) / 2f
        val top = thumbR
        val usable = (size.height - thumbR * 2f).coerceAtLeast(1f)
        fun yOf(v: Float) = top + (max - v.coerceIn(min, max)) / range * usable
        val zeroY = yOf(0f.coerceIn(min, max))
        val y = yOf(value)
        drawRoundRect(track, Offset(x, top), Size(w, usable), CornerRadius(w / 2f))
        drawLine(track, Offset(x - 4.dp.toPx(), zeroY), Offset(x + w + 4.dp.toPx(), zeroY), strokeWidth = 1.dp.toPx())
        val fillTop = minOf(y, zeroY)
        val fillH = kotlin.math.abs(zeroY - y)
        if (fillH > 0.5f) {
            drawRoundRect(Brush.verticalGradient(listOf(activeTop, activeBottom), startY = top, endY = top + usable),
                Offset(x, fillTop), Size(w, fillH), CornerRadius(w / 2f))
        }
        val c = Offset(size.width / 2f, y)
        drawCircle(activeBottom, thumbR * 1.7f, c, alpha = 0.22f)
        drawCircle(androidx.compose.ui.graphics.lerp(activeBottom, activeTop, (value - min) / range), thumbR, c)
        drawCircle(Color.White, thumbR * 0.4f, c, alpha = 0.9f)
    }
}
