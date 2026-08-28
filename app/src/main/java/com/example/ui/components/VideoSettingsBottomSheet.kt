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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppThemeMode
import com.example.model.AspectRatioMode
import com.example.ui.theme.HiAccentAmber
import com.example.ui.theme.HiPrimaryCyan
import com.example.ui.theme.HiSurfaceDark
import com.example.ui.theme.HiSurfaceElevated
import com.example.ui.theme.HiTextPrimary
import com.example.ui.theme.HiTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoSettingsBottomSheet(
    currentAspectRatio: AspectRatioMode,
    onAspectRatioSelected: (AspectRatioMode) -> Unit,
    sleepTimerMinutes: Int?,
    onSetSleepTimer: (Int?) -> Unit,
    currentThemeMode: AppThemeMode = AppThemeMode.CYAN_NEON_DARK,
    onThemeSelected: (AppThemeMode) -> Unit = {},
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var hwDecodingEnabled by remember { mutableStateOf(true) }
    var remuxUltraBufferEnabled by remember { mutableStateOf(true) }
    var tunnelingEnabled by remember { mutableStateOf(true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HiSurfaceDark,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Video & Decoder Settings",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = HiTextPrimary
            )
            Text(
                text = "4K UHD Blu-ray Remux engine & playback tuning",
                fontSize = 12.sp,
                color = HiTextSecondary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 0. THEME SELECTION
            Text(
                text = "App & Player Theme",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = HiTextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalThemeSelector(
                selectedTheme = currentThemeMode,
                onThemeSelected = onThemeSelected,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 1. ASPECT RATIO MODES
            Text(
                text = "Aspect Ratio Mode",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = HiTextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(AspectRatioMode.values()) { mode ->
                    val isSelected = mode == currentAspectRatio
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) HiPrimaryCyan else HiSurfaceElevated)
                            .clickable { onAspectRatioSelected(mode) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = mode.displayName,
                            color = if (isSelected) Color.Black else HiTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. HARDWARE DECODING ACCELERATION
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(HiSurfaceElevated)
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            tint = HiPrimaryCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Hardware Acceleration (MediaCodec)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = HiTextPrimary
                            )
                            Text(
                                text = "Direct GPU decoding for HEVC, AV1 & VP9 4K",
                                fontSize = 11.sp,
                                color = HiTextSecondary
                            )
                        }
                    }
                    Switch(
                        checked = hwDecodingEnabled,
                        onCheckedChange = { hwDecodingEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = HiPrimaryCyan
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. 4K BLU-RAY REMUX ULTRA BUFFER (128MB Cache)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(HiSurfaceElevated)
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = HiAccentAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "4K Remux Ultra Buffer (128MB)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = HiTextPrimary
                            )
                            Text(
                                text = "Smooth stutter-free playback for 80+ Mbps files",
                                fontSize = 11.sp,
                                color = HiTextSecondary
                            )
                        }
                    }
                    Switch(
                        checked = remuxUltraBufferEnabled,
                        onCheckedChange = { remuxUltraBufferEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = HiAccentAmber
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 4. TUNNELING DIRECT DISPLAY SYNC
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(HiSurfaceElevated)
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = HiPrimaryCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Tunneling Decoder Sync",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = HiTextPrimary
                            )
                            Text(
                                text = "Eliminates dropped frames on HDR displays",
                                fontSize = 11.sp,
                                color = HiTextSecondary
                            )
                        }
                    }
                    Switch(
                        checked = tunnelingEnabled,
                        onCheckedChange = { tunnelingEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = HiPrimaryCyan
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 5. SLEEP TIMER
            Text(
                text = "Sleep Timer",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = HiTextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val timerOptions = listOf(null, 15, 30, 45, 60, 90)
                items(timerOptions) { min ->
                    val isSelected = sleepTimerMinutes == min
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) HiAccentAmber else HiSurfaceElevated)
                            .clickable { onSetSleepTimer(min) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = if (min == null) "Off" else "${min}m",
                            color = if (isSelected) Color.Black else HiTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
