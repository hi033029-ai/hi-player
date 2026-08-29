package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HdrOn
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.AppThemeMode
import com.example.data.HwAccelerationMode
import com.example.ui.theme.LocalHiPalette
import com.example.viewmodel.LibraryViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    libraryViewModel: LibraryViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by libraryViewModel.playerSettings.collectAsState()
    val palette = LocalHiPalette.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = palette.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                modifier = Modifier
                    .statusBarsPadding()
                    .height(104.dp),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, palette.primary.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .background(Color(0xFF0F172A)),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(R.drawable.hi_player_logo)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Hi Player Logo",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Hi Player",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = palette.textPrimary
                            )
                            Text(
                                text = "Settings • Themes, Decoder & Playback Engine",
                                fontSize = 11.sp,
                                color = palette.primary
                            )
                        }
                    }
                },
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.surface)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Theme & Appearance (Horizontal Form)
            SettingsSectionHeader(title = "App Theme & Visual Style (Horizontal)", accentColor = palette.primary)

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surfaceElevated),
                border = null,
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    Text(
                        text = "CHOOSE APP THEME",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.primary,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
                    )
                    Text(
                        text = "Swipe horizontally to preview & pick your visual experience",
                        fontSize = 11.5.sp,
                        color = palette.textSecondary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Horizontal Theme Selector
                    com.example.ui.components.HorizontalThemeSelector(
                        selectedTheme = settings.themeMode,
                        onThemeSelected = { mode ->
                            libraryViewModel.setThemeMode(mode)
                        },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Active theme badge footer
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.surface)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Current Theme: ${settings.themeMode.displayName}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = palette.textPrimary
                            )
                            Text(
                                text = settings.themeMode.description,
                                fontSize = 10.sp,
                                color = palette.textSecondary
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(palette.primary)
                        )
                    }
                }
            }

            // 2. Hardware Decoder Mode
            SettingsSectionHeader(title = "Decoder & Hardware Acceleration", accentColor = palette.primary)

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surfaceElevated)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HwAccelerationMode.entries.forEach { hwMode ->
                        val isSelected = settings.hwAccelerationMode == hwMode
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { libraryViewModel.setHwAccelerationMode(hwMode) }
                                .testTag("settings_hw_${hwMode.id}"),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) palette.primary.copy(alpha = 0.15f) else Color.Transparent
                            ),
                            border = null,
                            elevation = CardDefaults.cardElevation(0.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = hwMode.displayName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = palette.textPrimary
                                    )
                                    Text(
                                        text = hwMode.description,
                                        fontSize = 11.sp,
                                        color = palette.textSecondary,
                                        lineHeight = 15.sp
                                    )
                                }

                                if (isSelected) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = palette.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. 4K UHD Blu-ray Remux Engine & HDR Enhancement
            SettingsSectionHeader(title = "4K UHD Blu-ray & HDR Graphics", accentColor = palette.primary)

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surfaceElevated)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SettingsSwitchItem(
                        icon = Icons.Default.HdrOn,
                        title = "Wide Color Gamut (BT.2020 / P3)",
                        subtitle = "Prevents dull or washed out colors on HDR 4K videos",
                        checked = settings.enableWideColorGamut,
                        onCheckedChange = { libraryViewModel.setWideColorGamut(it) }
                    )
                    HorizontalDivider(color = palette.surfaceBorder)
                    SettingsSwitchItem(
                        icon = Icons.Default.ColorLens,
                        title = "HDR Color Enhancement",
                        subtitle = "Hardware tonemapping for vivid, accurate dynamic range",
                        checked = settings.enableHdrEnhance,
                        onCheckedChange = { libraryViewModel.setHdrEnhance(it) }
                    )
                    HorizontalDivider(color = palette.surfaceBorder)
                    SettingsSwitchItem(
                        icon = Icons.Default.Tv,
                        title = "4K Remux Ultra Buffer (128MB)",
                        subtitle = "High throughput memory cache for 80+ Mbps Blu-ray remux",
                        checked = settings.remuxUltraBufferMode,
                        onCheckedChange = { libraryViewModel.setRemuxUltraBufferMode(it) }
                    )
                }
            }

            // 4. Playback & Multitasking
            SettingsSectionHeader(title = "Playback & Multitasking", accentColor = palette.primary)

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surfaceElevated)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SettingsSwitchItem(
                        icon = Icons.Default.Headphones,
                        title = "Background Audio Playback",
                        subtitle = "Keep listening when app is minimized or screen locked",
                        checked = settings.backgroundPlayEnabled,
                        onCheckedChange = { libraryViewModel.setBackgroundPlay(it) }
                    )
                    HorizontalDivider(color = palette.surfaceBorder)
                    SettingsSwitchItem(
                        icon = Icons.Default.PictureInPictureAlt,
                        title = "Auto Picture-in-Picture",
                        subtitle = "Enter PiP automatically when returning to Home screen",
                        checked = settings.autoPipEnabled,
                        onCheckedChange = { libraryViewModel.setAutoPip(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String, accentColor: Color) {
    Text(
        text = title,
        color = accentColor,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}

@Composable
private fun SettingsSwitchItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val palette = LocalHiPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = palette.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    color = palette.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = palette.textSecondary,
                    fontSize = 11.sp
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = palette.primary,
                checkedBorderColor = palette.primary,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFF94A3B8),
                uncheckedBorderColor = Color(0xFF94A3B8)
            )
        )
    }
}
