package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
    selectedColor: HdrColor = HdrColor.RED,
    colorIntensity: Float = 1f,
    onColorSelected: (HdrColor) -> Unit = {},
    onColorIntensityChange: (Float) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    if (customizing) {
        Column(
            modifier = modifier
                .background(Color.Black.copy(alpha = 0.52f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text("Customize HDR", color = Color.White, fontSize = 13.sp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CustomSlider(Modifier.weight(1f), "Exposure", customPreset.brightness, 0.5f..1.5f) {
                    onCustomPresetChange(customPreset.copy(brightness = it))
                }
                CustomSlider(Modifier.weight(1f), "Saturation", customPreset.saturation, 0f..2f) {
                    onCustomPresetChange(customPreset.copy(saturation = it))
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CustomSlider(Modifier.weight(1f), "Vibrance", customPreset.vibrance, 0f..2f) {
                    onCustomPresetChange(customPreset.copy(vibrance = it))
                }
                CustomSlider(Modifier.weight(1f), "Sharpness", customPreset.sharpness, 0f..2f) {
                    onCustomPresetChange(customPreset.copy(sharpness = it))
                }
            }
            Text("Color intensity", color = Color.White, fontSize = 12.sp)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HdrColor.entries.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(color.color, androidx.compose.foundation.shape.CircleShape)
                            .clickable { onColorSelected(color) }
                            .then(
                                if (color == selectedColor) Modifier.border(2.dp, Color.White, androidx.compose.foundation.shape.CircleShape)
                                else Modifier
                            ),
                    )
                }
            }
            Slider(
                value = colorIntensity,
                onValueChange = onColorIntensityChange,
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Light", color = Color.LightGray, fontSize = 10.sp)
                Text("Dark", color = Color.LightGray, fontSize = 10.sp)
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

enum class HdrColor(val color: Color) {
    RED(Color(0xFFE53935)), ORANGE(Color(0xFFFB8C00)), YELLOW(Color(0xFFFDD835)),
    GREEN(Color(0xFF43A047)), CYAN(Color(0xFF00ACC1)), BLUE(Color(0xFF1E88E5)),
    PURPLE(Color(0xFF8E24AA)), PINK(Color(0xFFD81B60)),
}

@Composable
private fun CustomSlider(
    modifier: Modifier,
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = modifier) {
        Text(
            text = "$label ${"%.2f".format(value)}",
            color = Color.White,
            fontSize = 11.sp,
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
