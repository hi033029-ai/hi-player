package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.VideoTrackInfo
import com.example.ui.theme.HiAccentAmber
import com.example.ui.theme.HiPrimaryCyan
import com.example.ui.theme.HiSurfaceDark
import com.example.ui.theme.HiSurfaceElevated
import com.example.ui.theme.HiTextPrimary
import com.example.ui.theme.HiTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioSettingsBottomSheet(
    audioTracks: List<VideoTrackInfo>,
    volumeBoostPercent: Int,
    audioDelayMs: Long,
    onSelectTrack: (VideoTrackInfo) -> Unit,
    onVolumeBoostChange: (Int) -> Unit,
    onAudioDelayChange: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var boostSlider by remember { mutableFloatStateOf(volumeBoostPercent.toFloat()) }
    var showAdvanced by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HiSurfaceDark,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Audio Language",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = HiTextPrimary
            )
            Text(
                text = "Switch between the audio tracks available in this file",
                fontSize = 12.sp,
                color = HiTextSecondary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // AUDIO LANGUAGE LIST - this is the primary purpose of the sheet,
            // so it leads and is no longer buried below decorative controls.
            if (audioTracks.isEmpty()) {
                Text(
                    text = "Default Stereo Stream (Internal)",
                    fontSize = 13.sp,
                    color = HiTextSecondary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    audioTracks.forEach { track ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (track.isSelected) Color(0x3300E5FF) else HiSurfaceElevated)
                                .clickable { onSelectTrack(track) }
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = track.label,
                                fontSize = 14.sp,
                                fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (track.isSelected) HiPrimaryCyan else HiTextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            if (track.isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = HiPrimaryCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Loudness booster and lip-sync delay are real, working controls
            // (unlike the old "Night Mode" toggle, which was decorative local
            // state that did nothing - it's been removed rather than kept as
            // clutter). They're tucked behind a disclosure since the person
            // asking for this sheet mainly wants the language list above.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { showAdvanced = !showAdvanced }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Volume Boost & Sync Delay",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HiTextSecondary
                )
                Icon(
                    imageVector = if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = HiTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (showAdvanced) {
                Spacer(modifier = Modifier.height(8.dp))

                // VOLUME BOOSTER
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(HiSurfaceElevated)
                        .padding(14.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = null,
                                    tint = if (boostSlider > 0) HiAccentAmber else HiPrimaryCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Loudness Booster",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = HiTextPrimary
                                )
                            }
                            Text(
                                text = "+${boostSlider.toInt()}% (${(boostSlider * 0.2f).toInt()} dB)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (boostSlider > 0) HiAccentAmber else HiPrimaryCyan
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Slider(
                            value = boostSlider,
                            onValueChange = {
                                boostSlider = it
                                onVolumeBoostChange(it.toInt())
                            },
                            valueRange = 0f..100f,
                            steps = 9,
                            colors = SliderDefaults.colors(
                                thumbColor = if (boostSlider > 0) HiAccentAmber else HiPrimaryCyan,
                                activeTrackColor = if (boostSlider > 0) HiAccentAmber else HiPrimaryCyan,
                                inactiveTrackColor = Color(0x33FFFFFF)
                            ),
                            modifier = Modifier.testTag("volume_boost_slider")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // AUDIO DELAY / LIP SYNC (-2s to +2s)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(HiSurfaceElevated)
                        .padding(14.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = HiPrimaryCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Audio Sync / Delay",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = HiTextPrimary
                                )
                            }
                            Text(
                                text = if (audioDelayMs >= 0) "+${audioDelayMs}ms" else "${audioDelayMs}ms",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = HiPrimaryCyan
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { onAudioDelayChange(audioDelayMs - 100) }) {
                                Text("-100ms", color = HiPrimaryCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            IconButton(onClick = { onAudioDelayChange(0) }) {
                                Text("Reset", color = HiTextSecondary, fontSize = 11.sp)
                            }
                            IconButton(onClick = { onAudioDelayChange(audioDelayMs + 100) }) {
                                Text("+100ms", color = HiPrimaryCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
