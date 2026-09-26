package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalHiPalette

/** Compact in-player equalizer popup, styled like the HDR options overlay. */
@Composable
fun EqualizerBottomSheet(
    selectedPreset: String,
    customBandsDb: List<Float> = List(5) { 0f },
    onSelectPreset: (String) -> Unit,
    onCustomBandChange: (Int, Float) -> Unit = { _, _ -> },
    onDismiss: () -> Unit,
) {
    val presets = listOf("Flat", "Bass Boost", "Jazz", "Rock", "Classical", "Vocal")
    val bands = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")
    val palette = LocalHiPalette.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .background(Color.Black.copy(alpha = 0.88f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            presets.forEach { preset ->
                PresetChip(
                    text = preset,
                    selected = selectedPreset == preset,
                    onClick = { onSelectPreset(preset) },
                )
            }
            PresetChip(
                text = "Custom",
                selected = selectedPreset == "Custom",
                onClick = { onSelectPreset("Custom") },
            )
        }

        if (selectedPreset == "Custom") {
            Text(
                text = "Custom equalizer",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                bands.forEachIndexed { index, label ->
                    val value = customBandsDb.getOrElse(index) { 0f }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = label,
                            color = Color.LightGray,
                            fontSize = 9.sp,
                            maxLines = 1,
                        )
                        Slider(
                            value = value,
                            onValueChange = { onCustomBandChange(index, it) },
                            valueRange = -12f..12f,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = palette.primary,
                                activeTrackColor = palette.primary,
                                inactiveTrackColor = palette.primary.copy(alpha = 0.28f),
                            ),
                        )
                        Text(
                            text = "%+.1f dB".format(value),
                            color = Color.White,
                            fontSize = 9.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalHiPalette.current
    Text(
        text = text,
        color = if (selected) Color.Black else Color.White,
        fontSize = 11.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .background(
                if (selected) palette.primary else Color.Transparent,
                RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 8.dp),
    )
}
