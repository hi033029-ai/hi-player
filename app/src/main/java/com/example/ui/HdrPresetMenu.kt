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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.player.ColorPreset
import com.example.player.ColorPresets
import com.example.ui.theme.LocalHiPalette

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
    val palette = LocalHiPalette.current

    if (customizing) {
        Column(
            modifier = modifier
                .fillMaxWidth(0.76f)
                .widthIn(max = 320.dp)
                .background(Color.Black.copy(alpha = 0.30f))
                .padding(horizontal = 7.dp, vertical = 5.dp),
        ) {
            Text("Customize HDR", color = palette.textPrimary, fontSize = 10.sp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                CustomSlider(Modifier.weight(1f), "Exposure", customPreset.brightness, 0.5f..1.5f) {
                    onCustomPresetChange(customPreset.copy(brightness = it))
                }
                CustomSlider(Modifier.weight(1f), "Saturation", customPreset.saturation, 0f..2f) {
                    onCustomPresetChange(customPreset.copy(saturation = it))
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                CustomSlider(Modifier.weight(1f), "Vibrance", customPreset.vibrance, 0f..2f) {
                    onCustomPresetChange(customPreset.copy(vibrance = it))
                }
                CustomSlider(Modifier.weight(1f), "Sharpness", customPreset.sharpness, 0f..2f) {
                    onCustomPresetChange(customPreset.copy(sharpness = it))
                }
            }
            Text(
                text = "Color intensity • ${selectedColor.name.lowercase().replaceFirstChar { it.uppercase() }}",
                color = selectedColor.color,
                fontSize = 9.sp,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HdrColor.entries.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(color.color, androidx.compose.foundation.shape.CircleShape)
                            .clickable { onColorSelected(color) }
                            .then(
                                if (color == selectedColor) Modifier.border(2.dp, Color(0xFF111827), androidx.compose.foundation.shape.CircleShape)
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
                colors = SliderDefaults.colors(
                    thumbColor = palette.primary,
                    activeTrackColor = palette.primary,
                    inactiveTrackColor = palette.primary.copy(alpha = 0.28f),
                    activeTickColor = palette.primary,
                    inactiveTickColor = palette.primary.copy(alpha = 0.38f),
                ),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Light", color = palette.textSecondary, fontSize = 8.sp)
                Text("Dark", color = palette.textSecondary, fontSize = 8.sp)
            }
        }
    } else {
        Row(
            modifier = modifier
                .background(Color.Black.copy(alpha = 0.24f))
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
            color = LocalHiPalette.current.textPrimary,
            fontSize = 8.sp,
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp),
            colors = SliderDefaults.colors(
                thumbColor = LocalHiPalette.current.primary,
                activeTrackColor = LocalHiPalette.current.primary,
                inactiveTrackColor = LocalHiPalette.current.primary.copy(alpha = 0.28f),
                activeTickColor = LocalHiPalette.current.primary,
                inactiveTickColor = LocalHiPalette.current.primary.copy(alpha = 0.38f),
            ),
        )
    }
}
