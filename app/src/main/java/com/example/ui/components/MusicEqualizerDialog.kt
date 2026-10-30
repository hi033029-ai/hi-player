package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.audio.AudioEqState
import com.example.audio.EqualizerCurves
import com.example.ui.theme.LocalHiPalette
import com.example.ui.theme.contrastContentColor
import kotlin.math.roundToInt

@Composable
fun MusicEqualizerDialog(
    state: AudioEqState, onEnabled: (Boolean) -> Unit, onPreset: (String) -> Unit,
    onBand: (Int, Float) -> Unit, onBass: (Int) -> Unit, onReset: () -> Unit, onDismiss: () -> Unit,
) {
    val palette = LocalHiPalette.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = palette.surfaceElevated) {
            Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).verticalScroll(rememberScrollState()).padding(20.dp)
                .testTag("music_equalizer_dialog"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Equalizer", color = palette.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("${state.bands.size} device bands · ${state.preset}", color = palette.textSecondary, fontSize = 12.sp)
                    }
                    Switch(checked = state.enabled, onCheckedChange = onEnabled,
                        modifier = Modifier.semantics { contentDescription = "Enable music equalizer" })
                }
                Text(state.status, color = palette.textSecondary, fontSize = 12.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(EqualizerCurves.presets.keys.toList()) { name ->
                        FilterChip(selected = state.preset == name, onClick = { onPreset(name) }, label = { Text(name) },
                            colors = FilterChipDefaults.filterChipColors(
                                labelColor = palette.textPrimary, selectedContainerColor = palette.primary,
                                selectedLabelColor = contrastContentColor(palette.primary, palette.surfaceElevated)))
                    }
                }
                if (state.available) {
                    Text("Frequency bands", color = palette.textPrimary, fontWeight = FontWeight.SemiBold)
                    state.bands.forEach { band ->
                        val label = if (band.frequencyHz >= 1000) "${band.frequencyHz / 1000f} kHz" else "${band.frequencyHz} Hz"
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(label, Modifier.width(58.dp), color = palette.textSecondary, fontSize = 11.sp)
                            Slider(value = band.gainDb, onValueChange = { onBand(band.index, it) },
                                enabled = state.enabled, valueRange = state.minDb..state.maxDb,
                                modifier = Modifier.weight(1f).semantics { contentDescription = "$label gain" },
                                colors = SliderDefaults.colors(thumbColor = palette.primary, activeTrackColor = palette.primary))
                            Text("${if (band.gainDb > 0) "+" else ""}${band.gainDb.roundToInt()} dB",
                                Modifier.width(44.dp), color = palette.textPrimary, fontSize = 11.sp)
                        }
                    }
                }
                if (state.bassSupported) {
                    Text("Bass strength · ${state.bassStrength / 10}%", color = palette.textPrimary)
                    Slider(value = state.bassStrength.toFloat(), onValueChange = { onBass(it.roundToInt()) },
                        valueRange = 0f..1000f, enabled = state.enabled,
                        modifier = Modifier.semantics { contentDescription = "Bass strength" })
                }
                Text("Band count and gain range depend on your device and audio output. High boosts can distort; start low.",
                    color = palette.textSecondary, fontSize = 11.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onReset) { Text("Reset to flat", color = palette.primary) }
                    TextButton(onClick = onDismiss) { Text("Done", color = palette.primary) }
                }
            }
        }
    }
}
