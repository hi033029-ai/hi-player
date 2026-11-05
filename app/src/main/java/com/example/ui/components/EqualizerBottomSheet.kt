package com.example.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** In-player controls for the existing video equalizer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerBottomSheet(
    selectedPreset: String,
    customBandsDb: List<Float> = List(5) { 0f },
    onSelectPreset: (String) -> Unit,
    onCustomBandChange: (Int, Float) -> Unit = { _, _ -> },
    onDismiss: () -> Unit,
) {
    val presets = listOf("Custom", "Flat", "Bass Boost", "Jazz", "Rock", "Classical", "Vocal",
        "Dance", "Electronic", "Acoustic", "Podcast", "Movie")
    // The engine maps these five logical bands onto the device's actual bands.
    val bands = listOf("Low", "Low-mid", "Mid", "High-mid", "High")
    val accent = Color(0xFF66B6FF)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetMaxWidth = 560.dp,
        containerColor = Color(0xFF202020),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.LightGray) },
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Equalizer", color = Color.White, fontSize = 18.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Done", color = accent) }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                presets.forEach { preset ->
                    FilterChip(selected = selectedPreset == preset, onClick = { onSelectPreset(preset) },
                        label = { Text(preset) }, colors = FilterChipDefaults.filterChipColors(
                            labelColor = Color.White, selectedLabelColor = Color.Black,
                            selectedContainerColor = accent))
                }
            }
            if (selectedPreset == "Custom") {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    bands.forEachIndexed { index, label ->
                        val gain = customBandsDb.getOrElse(index) { 0f }
                        Column(Modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${if (gain > 0) "+" else ""}${gain.roundToInt()} dB", color = Color.White, fontSize = 11.sp)
                            VerticalGainSlider(value = gain, min = -12f, max = 12f,
                                onValueChange = { onCustomBandChange(index, it) }, label = "$label band gain",
                                activeTop = accent, activeBottom = accent, track = Color.Gray,
                                modifier = Modifier.width(48.dp).height(120.dp).padding(vertical = 4.dp))
                            Text(label, color = Color.LightGray, fontSize = 11.sp)
                        }
                    }
                }
            } else {
                Text("Select Custom to adjust the bands.", color = Color.LightGray, fontSize = 12.sp)
            }
        }
    }
}
