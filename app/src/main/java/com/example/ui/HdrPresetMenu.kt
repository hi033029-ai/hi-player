package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.player.ColorBandAdjustment
import com.example.player.ColorPreset
import com.example.player.ColorPresets
import com.example.player.HdrColor
import com.example.player.withBandAdjustment
import kotlin.math.roundToInt

/**
 * Compact, uncluttered HDR preset bar & customizer designed for watching video:
 *  • Horizontal carousel of presets with live preview.
 *  • Single "Customize" action to fine-tune the whole picture or individual colours.
 */
@Composable
fun HdrPresetMenu(
    selected: ColorPreset,
    isCustomizing: Boolean,
    onSelect: (ColorPreset) -> Unit,
    onOpenCustomizer: () -> Unit,
    onUpdatePreset: (ColorPreset) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onUserInteraction: () -> Unit = {},
    onAdjustingChange: (Boolean) -> Unit = {},
) {
    DisposableEffect(Unit) {
        onDispose { onAdjustingChange(false) }
    }

    var selectedColor by remember { mutableStateOf<HdrColor?>(null) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        if (isCustomizing) {
            val panelShape = RoundedCornerShape(16.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(panelShape)
                    .background(Color(0xE00A0F1A))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), panelShape)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HdrMenuHeader(
                    title = "Customize ${hdrPresetDisplayName(selected)}",
                    subtitle = "Adjust only what you choose",
                    onDone = onClose,
                    onReset = {
                        onUserInteraction()
                        onReset()
                    },
                )

                // Scope toggle: Whole picture vs individual colours
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val wholeSelected = selectedColor == null
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                if (wholeSelected) Color.White.copy(alpha = 0.18f)
                                else Color.White.copy(alpha = 0.06f),
                            )
                            .border(
                                1.dp,
                                if (wholeSelected) Color.White else Color.White.copy(alpha = 0.16f),
                                RoundedCornerShape(18.dp),
                            )
                            .clickable {
                                onUserInteraction()
                                selectedColor = null
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = "Whole picture",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    Text(
                        text = "By colour:",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 10.sp,
                        modifier = Modifier.padding(start = 4.dp),
                    )

                    HdrColor.entries.forEach { hdrColor ->
                        val isSelected = selectedColor == hdrColor
                        val hasAdjustment = !selected.bandFor(hdrColor).isNeutral
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color(hdrColor.swatchArgb))
                                .border(
                                    width = if (isSelected) 2.5.dp else if (hasAdjustment) 1.5.dp else 1.dp,
                                    color = when {
                                        isSelected -> Color.White
                                        hasAdjustment -> Color.White.copy(alpha = 0.85f)
                                        else -> Color.White.copy(alpha = 0.25f)
                                    },
                                    shape = CircleShape,
                                )
                                .clickable {
                                    onUserInteraction()
                                    selectedColor = hdrColor
                                },
                        )
                    }
                }

                val activeColor = selectedColor
                if (activeColor == null) {
                    CustomSlider(
                        label = "Brightness",
                        value = selected.brightness,
                        range = -0.35f..0.45f,
                        valueText = "%+.2f".format(selected.brightness),
                        onValueChange = {
                            onUserInteraction()
                            onAdjustingChange(true)
                            onUpdatePreset(selected.copy(brightness = it))
                        },
                        onValueChangeFinished = { onAdjustingChange(false) },
                    )
                    CustomSlider(
                        label = "Contrast",
                        value = selected.contrast,
                        range = 0.6f..1.8f,
                        valueText = "%.2fx".format(selected.contrast),
                        onValueChange = {
                            onUserInteraction()
                            onAdjustingChange(true)
                            onUpdatePreset(selected.copy(contrast = it))
                        },
                        onValueChangeFinished = { onAdjustingChange(false) },
                    )
                    CustomSlider(
                        label = "Saturation",
                        value = selected.saturation,
                        range = 0.5f..2.0f,
                        valueText = "%.2fx".format(selected.saturation),
                        onValueChange = {
                            onUserInteraction()
                            onAdjustingChange(true)
                            onUpdatePreset(selected.copy(saturation = it))
                        },
                        onValueChangeFinished = { onAdjustingChange(false) },
                    )
                    CustomSlider(
                        label = "Warmth",
                        value = selected.temperature,
                        range = -0.5f..0.5f,
                        valueText = "%+.2f".format(selected.temperature),
                        onValueChange = {
                            onUserInteraction()
                            onAdjustingChange(true)
                            onUpdatePreset(selected.copy(temperature = it))
                        },
                        onValueChangeFinished = { onAdjustingChange(false) },
                    )
                    CustomSlider(
                        label = "Sharpness",
                        value = selected.sharpness,
                        range = 0f..1f,
                        valueText = "${(selected.sharpness * 100).roundToInt()}%",
                        onValueChange = {
                            onUserInteraction()
                            onAdjustingChange(true)
                            onUpdatePreset(selected.copy(sharpness = it))
                        },
                        onValueChangeFinished = { onAdjustingChange(false) },
                    )
                } else {
                    val band = selected.bandFor(activeColor)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(activeColor.swatchArgb)),
                            )
                            Text(
                                text = "${activeColor.displayName} channel",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        if (!band.isNeutral) {
                            Text(
                                text = "Reset colour",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        onUserInteraction()
                                        onUpdatePreset(
                                            selected.withBandAdjustment(activeColor, ColorBandAdjustment.NEUTRAL),
                                        )
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    CustomSlider(
                        label = "Hue",
                        value = band.hueDegrees,
                        range = -45f..45f,
                        valueText = "%+.0f°".format(band.hueDegrees),
                        onValueChange = {
                            onUserInteraction()
                            onAdjustingChange(true)
                            onUpdatePreset(
                                selected.withBandAdjustment(activeColor, band.copy(hueDegrees = it)),
                            )
                        },
                        onValueChangeFinished = { onAdjustingChange(false) },
                    )
                    CustomSlider(
                        label = "Saturation",
                        value = band.saturation,
                        range = 0f..2f,
                        valueText = "%.2fx".format(band.saturation),
                        onValueChange = {
                            onUserInteraction()
                            onAdjustingChange(true)
                            onUpdatePreset(
                                selected.withBandAdjustment(activeColor, band.copy(saturation = it)),
                            )
                        },
                        onValueChangeFinished = { onAdjustingChange(false) },
                    )
                    CustomSlider(
                        label = "Luminance",
                        value = band.luminance,
                        range = -0.5f..0.5f,
                        valueText = "%+.2f".format(band.luminance),
                        onValueChange = {
                            onUserInteraction()
                            onAdjustingChange(true)
                            onUpdatePreset(
                                selected.withBandAdjustment(activeColor, band.copy(luminance = it)),
                            )
                        },
                        onValueChangeFinished = { onAdjustingChange(false) },
                    )
                }
            }
        } else {
            val listState = rememberLazyListState()
            LaunchedEffect(listState.isScrollInProgress) {
                if (listState.isScrollInProgress) {
                    onUserInteraction()
                    onAdjustingChange(true)
                } else {
                    onAdjustingChange(false)
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HdrMenuHeader(
                    title = "Picture mode",
                    subtitle = "${hdrPresetDisplayName(selected)} selected · tap another mode to preview",
                    onDone = onClose,
                    onReset = null,
                )

                LazyRow(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    itemsIndexed(ColorPresets.ALL) { _, preset ->
                        HdrPresetOption(
                            title = hdrPresetDisplayName(preset),
                            selected = preset.id == selected.id,
                            testTag = hdrPresetTestTag(preset),
                            onClick = {
                                onUserInteraction()
                                onSelect(preset)
                            },
                        )
                    }
                    item {
                        Box(
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                                .clickable {
                                    onUserInteraction()
                                    onOpenCustomizer()
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Customize",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun hdrPresetDisplayName(preset: ColorPreset): String =
    when (preset.id) {
        ColorPreset.ORIGINAL.id -> "Original"
        ColorPreset.HDR_VIVID.id -> "Vivid"
        else -> preset.name
    }

private fun hdrPresetTestTag(preset: ColorPreset): String =
    when (preset.id) {
        ColorPreset.ORIGINAL.id -> "hdr_preset_original"
        ColorPreset.HDR_VIVID.id -> "hdr_preset_hdr_vivid"
        else -> "hdr_preset_${preset.id}"
    }

@Composable
private fun HdrMenuHeader(
    title: String,
    subtitle: String,
    onDone: () -> Unit,
    onReset: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 10.sp,
                maxLines = 1,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onReset != null) {
                TextButton(
                    onClick = onReset,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "Reset",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            TextButton(
                onClick = onDone,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "Done",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
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
    Column(
        modifier = Modifier
            .widthIn(min = 84.dp, max = 186.dp)
            .heightIn(min = 48.dp)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .testTag(testTag)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .padding(top = 3.dp)
                    .width(28.dp)
                    .height(2.dp)
                    .background(Color.White),
            )
        }
    }
}

@Composable
private fun CustomSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit = {},
) {
    var draft by remember(value) { mutableFloatStateOf(value) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 11.sp,
            )
            Text(
                text = valueText,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Slider(
            value = draft.coerceIn(range.start, range.endInclusive),
            onValueChange = {
                draft = it
                onValueChange(it)
            },
            onValueChangeFinished = onValueChangeFinished,
            valueRange = range,
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                activeTickColor = Color.White,
                inactiveTickColor = Color.White.copy(alpha = 0.50f),
            ),
        )
    }
}
