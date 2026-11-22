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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import kotlin.math.abs
import kotlinx.coroutines.flow.first

private val HdrMenuShape = RoundedCornerShape(16.dp)

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
    colorIntensities: Map<HdrColor, Float> = emptyMap(),
    onColorSelected: (HdrColor) -> Unit = {},
    colorSaturations: Map<HdrColor, Float> = emptyMap(),
    onBandChange: (Float, Float) -> Unit = { _, _ -> },
    enhancementActive: Boolean = false,
    onToggleEnhancement: () -> Unit = {},
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier,
    onUserInteraction: () -> Unit = {},
    onAdjustingChange: (Boolean) -> Unit = {},
) {
    if (!visible) return
      DisposableEffect(Unit) { onDispose { onAdjustingChange(false) } }
    var colorIntensityDraft by remember(selectedColor, colorIntensity) {
        mutableFloatStateOf(colorIntensity)
    }
    if (customizing) {
        val saturation = colorSaturations[selectedColor] ?: 1f
        val customizerScrollState = rememberScrollState()
        LaunchedEffect(customizerScrollState.isScrollInProgress) {
            onAdjustingChange(customizerScrollState.isScrollInProgress)
            if (customizerScrollState.isScrollInProgress) onUserInteraction()
        }
        Column(
            modifier = modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .navigationBarsPadding()
                .clip(HdrMenuShape)
                .background(Color(0xF20A0F19), HdrMenuShape)
                .border(1.dp, Color.White.copy(alpha = 0.18f), HdrMenuShape)
                .verticalScroll(customizerScrollState)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("hdr_customizer"),
        ) {
            HdrMenuHeader(
                title = "Customize HDR",
                subtitle = "Live preview",
                secondaryActionLabel = "Reset",
                onSecondaryAction = {
                    onUserInteraction()
                    onCustomPresetChange(ColorPresets.CUSTOM)
                },
                onDone = {
                    onUserInteraction()
                    onApplyCustomPreset(customPreset)
                    onClose()
                },
            )
            com.example.ui.components.FirstUseTip(
                com.example.ui.components.rememberFirstUseTip("hdr_customisation_v1"),
                "Adjust only what you choose",
                "Whole-picture sliders adjust the full frame. Pick any colour below to brighten or dull only matching pixels without tinting the whole screen.",
                overVideo = true,
            )
            Text(
                "Whole picture",
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CustomSlider(Modifier.weight(1f), "Exposure", customPreset.brightness, 0.5f..1.5f, onUserInteraction = onUserInteraction, onAdjustingChange = onAdjustingChange) {
                    onCustomPresetChange(customPreset.copy(brightness = it))
                }
                CustomSlider(Modifier.weight(1f), "Contrast", customPreset.contrast, 0.7f..1.4f, onUserInteraction = onUserInteraction, onAdjustingChange = onAdjustingChange) {
                    onCustomPresetChange(customPreset.copy(contrast = it))
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CustomSlider(Modifier.weight(1f), "Saturation", customPreset.saturation, 0f..2f, onUserInteraction = onUserInteraction, onAdjustingChange = onAdjustingChange) {
                    onCustomPresetChange(customPreset.copy(saturation = it))
                }
                CustomSlider(Modifier.weight(1f), "Vibrance", customPreset.vibrance, 0f..2f, onUserInteraction = onUserInteraction, onAdjustingChange = onAdjustingChange) {
                    onCustomPresetChange(customPreset.copy(vibrance = it))
                }
            }
            CustomSlider(Modifier.fillMaxWidth(), "Sharpness", customPreset.sharpness, 0f..2f, onUserInteraction = onUserInteraction, onAdjustingChange = onAdjustingChange) {
                onCustomPresetChange(customPreset.copy(sharpness = it))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Colour · ${selectedColor.displayName}",
                    color = selectedColor.color,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (abs(colorIntensityDraft - 0.5f) > 0.001f || abs(saturation - 1f) > 0.001f) {
                    Text(
                        text = "Reset colour",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onUserInteraction(); colorIntensityDraft = 0.5f
                                onBandChange(0.5f, 1f)
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                HdrColor.entries.forEach { color ->
                    val isSelected = color == selectedColor
                    val isModified = abs((colorIntensities[color] ?: 0.5f) - 0.5f) > 0.001f || abs((colorSaturations[color] ?: 1f) - 1f) > 0.001f
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.RadioButton) { onUserInteraction(); onColorSelected(color) }
                            .semantics {
                                contentDescription = "${color.displayName} colour adjustment"
                                this.selected = isSelected
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .background(color.color, CircleShape)
                                .then(
                                    if (isSelected) Modifier.border(2.5.dp, Color.White, CircleShape)
                                    else Modifier.border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape)
                                ),
                        )
                        if (isModified) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(6.dp)
                                    .background(Color.White, CircleShape),
                            )
                        }
                    }
                }
            }
            Slider(
                value = colorIntensityDraft,
                onValueChange = {
                    onUserInteraction(); onAdjustingChange(true); colorIntensityDraft = it
                    onBandChange(it, saturation)
                },
                onValueChangeFinished = { onAdjustingChange(false) }, valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth().height(34.dp),
                colors = SliderDefaults.colors(
                    thumbColor = selectedColor.color,
                    activeTrackColor = selectedColor.color,
                    inactiveTrackColor = Color.White.copy(alpha = 0.24f),
                ),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Bright", color = Color.White.copy(alpha = 0.85f), fontSize = 9.sp, fontWeight = FontWeight.Medium)
                Text("Original", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                Text("Dark", color = Color.White.copy(alpha = 0.85f), fontSize = 9.sp, fontWeight = FontWeight.Medium)
            }
            CustomSlider(Modifier.fillMaxWidth(), "${selectedColor.displayName} · Dull to rich", saturation, 0f..2f, onUserInteraction = onUserInteraction, onAdjustingChange = onAdjustingChange) {
                onBandChange(colorIntensityDraft, it)
            }
        }
    } else {
        val listState = rememberLazyListState(); LaunchedEffect(listState.isScrollInProgress) { onAdjustingChange(listState.isScrollInProgress); if (listState.isScrollInProgress) onUserInteraction() }
        val selectedIndex = remember(selected.name) {
            val idx = ColorPresets.ALL.indexOfFirst { it.name == selected.name }
            if (idx >= 0) idx else ColorPresets.ALL.size
        }
        LaunchedEffect(listState, selectedIndex) {
            val layout = snapshotFlow { listState.layoutInfo }.first { it.visibleItemsInfo.isNotEmpty() }
            val selectedItem = layout.visibleItemsInfo.firstOrNull { it.index == selectedIndex }
            if (selectedItem == null || selectedItem.offset < layout.viewportStartOffset ||
                selectedItem.offset + selectedItem.size > layout.viewportEndOffset
            ) {
                listState.animateScrollToItem(selectedIndex)
            }
        }
        Column(
            modifier = modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .navigationBarsPadding()
                .clip(HdrMenuShape)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("hdr_preset_menu"),
        ) {
            HdrMenuHeader(
                title = "Picture mode",
                secondaryActionLabel = if (enhancementActive) "Turn off" else "Turn on",
                onSecondaryAction = { onUserInteraction(); onToggleEnhancement() },
                subtitle = "${hdrPresetDisplayName(selected)} selected · tap another mode to preview",
                onDone = { onUserInteraction(); onClose() },
            )
            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(ColorPresets.ALL, key = { _, preset -> preset.name }) { _, preset ->
                    HdrPresetOption(
                        title = hdrPresetDisplayName(preset),
                        selected = preset.name == selected.name,
                        testTag = "hdr_preset_${preset.name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')}",
                        onClick = { onUserInteraction(); onSelect(preset) },
                    )
                }
                item(key = "customize_hdr") {
                    HdrPresetOption(
                        title = "Customize",
                        selected = selected.name == ColorPresets.CUSTOM.name,
                        testTag = "hdr_preset_customize",
                        onClick = { onUserInteraction(); onCustomize() },
                    )
                }
            }
        }
    }
}

@Composable
private fun HdrMenuHeader(
    title: String,
    subtitle: String,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
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
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                subtitle,
                color = Color.White.copy(alpha = 0.70f),
                fontSize = 9.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (secondaryActionLabel != null && onSecondaryAction != null) {
                TextButton(onClick = onSecondaryAction, modifier = Modifier.heightIn(min = 36.dp)) {
                    Text(secondaryActionLabel, color = Color.White.copy(alpha = 0.78f), fontSize = 10.sp)
                }
            }
            TextButton(onClick = onDone, modifier = Modifier.heightIn(min = 36.dp)) {
                Text("Done", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HdrPresetOption(
    title: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val selectionOutline = if (selected) Modifier.border(2.dp, Color.White, shape) else Modifier
    Column(
        modifier = Modifier
            .widthIn(min = 72.dp, max = 186.dp)
            .heightIn(min = 48.dp)
            .clip(shape)
            .then(selectionOutline)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .testTag(testTag)
            .padding(horizontal = 6.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
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
    onUserInteraction: () -> Unit = {},
    onAdjustingChange: (Boolean) -> Unit = {},
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = modifier) {
        var draft by remember(value) { mutableFloatStateOf(value) }
        Text(
            text = label,
            color = Color.White,
            fontSize = 9.5.sp,
            maxLines = 1,
        )
        Slider(
            value = draft,
            onValueChange = {
                onUserInteraction(); onAdjustingChange(true); draft = it
                onValueChange(it)
            },
            onValueChangeFinished = { onAdjustingChange(false) }, valueRange = range,
            modifier = Modifier.fillMaxWidth().height(32.dp),
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
