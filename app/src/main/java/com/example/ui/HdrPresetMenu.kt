package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.player.ColorPreset
import com.example.player.ColorPresets

@Composable
fun HdrPresetMenu(
    visible: Boolean,
    selected: ColorPreset,
    onSelect: (ColorPreset) -> Unit,
    customPreset: ColorPreset = ColorPresets.CUSTOM,
    customizing: Boolean = false,
    onCustomize: () -> Unit = {},
    onCustomPresetChange: (ColorPreset) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    if (customizing) {
        Column(
            modifier = modifier
                .background(Color.Black.copy(alpha = 0.94f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text("Customize HDR", color = Color.White, fontSize = 13.sp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CustomSlider("Exposure", customPreset.brightness, 0.5f..1.5f) {
                    onCustomPresetChange(customPreset.copy(brightness = it))
                }
                CustomSlider("Saturation", customPreset.saturation, 0f..2f) {
                    onCustomPresetChange(customPreset.copy(saturation = it))
                }
                CustomSlider("Vibrance", customPreset.vibrance, 0f..2f) {
                    onCustomPresetChange(customPreset.copy(vibrance = it))
                }
                CustomSlider("Sharpness", customPreset.sharpness, 0f..2f) {
                    onCustomPresetChange(customPreset.copy(sharpness = it))
                }
            }
        }
    } else {
        Row(
            modifier = modifier
                .background(Color.Black.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 6.dp),
        ) {
            ColorPresets.ALL.forEach { preset ->
                Text(
                    text = preset.name,
                    color = if (preset.name == selected.name) Color.Cyan else Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clickable { onSelect(preset) }
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                )
            }
            Text(
                text = "Customize",
                color = if (selected.name == ColorPresets.CUSTOM.name) Color.Cyan else Color.White,
                fontSize = 14.sp,
                modifier = Modifier
                    .clickable(onClick = onCustomize)
                    .padding(horizontal = 16.dp, vertical = 11.dp),
            )
        }
    }
}

@Composable
private fun CustomSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.width(150.dp)) {
        Text(
            text = "$label ${"%.2f".format(value)}",
            color = Color.White,
            fontSize = 11.sp,
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.width(150.dp),
        )
    }
}
