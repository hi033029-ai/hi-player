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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.player.ColorPreset
import com.example.player.ColorPresets
import com.example.ui.theme.LocalHiPalette

private val HdrMenuShape = RoundedCornerShape(18.dp)

@Composable
fun HdrPresetMenu(
    visible: Boolean,
    selected: ColorPreset,
    onSelect: (ColorPreset) -> Unit,
    customPreset: ColorPreset = ColorPresets.CUSTOM,
    customizing: Boolean = false,
    onCustomize: () -> Unit = {},
    onApplyCustomPreset: (ColorPreset) -> Unit = {},
    onCustomPresetChange: (ColorPreset) -> Unit = {},
    selectedColor: HdrColor = HdrColor.RED,
    colorIntensity: Float = 0.5f,
    onColorSelected: (HdrColor) -> Unit = {},
    onColorIntensityChange: (Float) -> Unit = {},
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    val palette = LocalHiPalette.current
    var colorIntensityDraft by remember(selectedColor, colorIntensity) {
        mutableFloatStateOf(colorIntensity)
    }

    if (customizing) {
        Column(
            modifier = modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .navigationBarsPadding()
                .clip(HdrMenuShape)
                .background(Color(0xF20A0F19), HdrMenuShape)
                .border(1.dp, Color.White.copy(alpha = 0.18f), HdrMenuShape)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .testTag("hdr_customizer"),
        ) {
            HdrMenuHeader(
                title = "Customize HDR",
                subtitle = "Adjust your settings, then tap Done to apply",
                onDone = {
                    onApplyCustomPreset(customPreset)
                    onClose()
                },
            )
            com.example.ui.components.FirstUseTip(
                com.example.ui.components.rememberFirstUseTip("hdr_customisation_v1"),
                "Adjust only what you choose",
                "Whole-picture sliders affect the frame. Choose a colour below, then move its Light–Dark slider to adjust matching pixels only. The middle keeps the original colour.",
                overVideo = true,
            )
            Text(
                "Whole picture",
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
            )
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
            Text(
                text = "Selected colour · ${selectedColor.displayName}",
                color = selectedColor.color,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 2.dp),
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                HdrColor.entries.forEach { color ->
                    val isSelected = color == selectedColor
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.RadioButton) { onColorSelected(color) }
                            .semantics {
                                contentDescription = "${color.displayName} colour adjustment"
                                selected = isSelected
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(color.color, CircleShape)
                                .then(
                                    if (isSelected) Modifier.border(3.dp, Color.White, CircleShape)
                                    else Modifier.border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape)
                                ),
                        )
                    }
                }
            }
            Slider(
                value = colorIntensityDraft,
                onValueChange = { colorIntensityDraft = it },
                onValueChangeFinished = { onColorIntensityChange(colorIntensityDraft) },
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth().height(40.dp),
                colors = SliderDefaults.colors(
                    thumbColor = palette.primary,
                    activeTrackColor = palette.primary,
                    inactiveTrackColor = palette.primary.copy(alpha = 0.28f),
                    activeTickColor = palette.primary,
                    inactiveTickColor = palette.primary.copy(alpha = 0.38f),
                ),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Light", color = Color.White.copy(alpha = 0.78f), fontSize = 11.sp)
                Text("Original", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text("Dark", color = Color.White.copy(alpha = 0.78f), fontSize = 11.sp)
            }
        }
    } else {
        Column(
            modifier = modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .navigationBarsPadding()
                .clip(HdrMenuShape)
                .background(Color(0xF20A0F19), HdrMenuShape)
                .border(1.dp, Color.White.copy(alpha = 0.18f), HdrMenuShape)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .testTag("hdr_preset_menu"),
        ) {
            HdrMenuHeader(
                title = "Picture mode",
                subtitle = "${hdrPresetDisplayName(selected)} selected · tap another mode to preview",
                onDone = onClose,
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ColorPresets.ALL.forEach { preset ->
                    HdrPresetOption(
                        title = hdrPresetDisplayName(preset),
                        subtitle = hdrPresetSubtitle(preset),
                        selected = preset.name == selected.name,
                        testTag = "hdr_preset_${preset.name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')}",
                        onClick = { onSelect(preset) },
                    )
                }
                HdrPresetOption(
                    title = "Customize",
                    subtitle = "Fine controls",
                    selected = selected.name == ColorPresets.CUSTOM.name,
                    testTag = "hdr_preset_customize",
                    onClick = onCustomize,
                )
            }
        }
    }
}

@Composable
private fun HdrMenuHeader(
    title: String,
    subtitle: String,
    onDone: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                subtitle,
                color = Color.White.copy(alpha = 0.70f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onDone, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Done", color = LocalHiPalette.current.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HdrPresetOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    val palette = LocalHiPalette.current
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .widthIn(min = 112.dp)
            .heightIn(min = 60.dp)
            .clip(shape)
            .background(
                if (selected) palette.primary.copy(alpha = 0.18f)
                else Color.White.copy(alpha = 0.07f),
            )
            .border(
                1.dp,
                if (selected) palette.primary.copy(alpha = 0.90f) else Color.White.copy(alpha = 0.12f),
                shape,
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .testTag(testTag)
            .padding(horizontal = 13.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            color = if (selected) palette.primary else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Text(
            text = if (selected) "Selected" else subtitle,
            color = Color.White.copy(alpha = 0.66f),
            fontSize = 10.sp,
            maxLines = 1,
        )
    }
}

private fun hdrPresetDisplayName(preset: ColorPreset): String = when (preset.name) {
    ColorPresets.HDR_DISABLED.name -> "Original"
    ColorPresets.VIVID_HDR.name -> "Vivid"
    ColorPresets.CINEMATIC.name -> "Cinema"
    ColorPresets.HDR_BRIGHT.name -> "Bright"
    ColorPresets.NATURAL_HDR.name -> "Natural"
    else -> preset.name
}

private fun hdrPresetSubtitle(preset: ColorPreset): String = when (preset.name) {
    ColorPresets.HDR_DISABLED.name -> "Native output"
    ColorPresets.VIVID_HDR.name -> "More colour"
    ColorPresets.CINEMATIC.name -> "Balanced tone"
    ColorPresets.HDR_BRIGHT.name -> "Brighter image"
    ColorPresets.NATURAL_HDR.name -> "Gentle boost"
    ColorPresets.WARM.name -> "Warmer tone"
    ColorPresets.COOL.name -> "Cooler tone"
    ColorPresets.EYE_COMFORT.name -> "Reduced blue"
    else -> "Picture preset"
}

enum class HdrColor(val color: Color, val displayName: String) {
    RED(Color(0xFFE53935), "Red"),
    ORANGE(Color(0xFFFB8C00), "Orange"),
    YELLOW(Color(0xFFFDD835), "Yellow"),
    GREEN(Color(0xFF43A047), "Green"),
    CYAN(Color(0xFF00ACC1), "Cyan"),
    BLUE(Color(0xFF1E88E5), "Blue"),
    PURPLE(Color(0xFF8E24AA), "Purple"),
    PINK(Color(0xFFD81B60), "Pink"),
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
        var draft by remember(value) { mutableFloatStateOf(value) }
        Text(
            text = "$label ${"%.2f".format(draft)}",
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 1,
        )
        Slider(
            value = draft,
            onValueChange = { draft = it },
            onValueChangeFinished = { onValueChange(draft) },
            valueRange = range,
            modifier = Modifier.fillMaxWidth().height(36.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.30f),
                activeTickColor = Color.White,
                inactiveTickColor = Color.White.copy(alpha = 0.50f),
            ),
        )
    }
}
