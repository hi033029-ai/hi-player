package com.example.ui.screens

import com.example.data.AppTextSize

import com.example.ui.theme.hiContainerBorder

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import com.example.ui.theme.AppAlertDialog as AlertDialog
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.AppThemeMode
import com.example.data.HwAccelerationMode
import com.example.ui.theme.LocalHiPalette
import com.example.ui.theme.HiCardShape
import com.example.ui.theme.HiThumbnailShape
import com.example.ui.theme.contrastContentColor
import com.example.ui.theme.LocalHiUiMetrics
import com.example.ui.theme.getCustomPalette
import com.example.ui.components.DiscoverSourceSettings
import com.example.ui.components.HiPlayerHeader
import com.example.ui.components.ThemeCustomizationPanel
import com.example.viewmodel.DiscoverViewModel
import com.example.viewmodel.LibraryViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    libraryViewModel: LibraryViewModel,
    discoverViewModel: DiscoverViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    includeHeader: Boolean = true,
    onPlayDriveMedia: (com.example.model.VideoItem) -> Unit = {}
) {
    val settings by libraryViewModel.playerSettings.collectAsState()
    val palette = LocalHiPalette.current
    val uiMetrics = LocalHiUiMetrics.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCustomTheme by rememberSaveable { mutableStateOf(settings.themeMode == AppThemeMode.CUSTOM) }
    var pendingPlaybackPermission by remember { mutableStateOf<String?>(null) }
    var showAccountSources by rememberSaveable { mutableStateOf(true) }
    var showConnections by rememberSaveable { mutableStateOf(false) }
    var showThemes by rememberSaveable { mutableStateOf(false) }
    var showTextSize by rememberSaveable { mutableStateOf(false) }
    var showDecoder by rememberSaveable { mutableStateOf(false) }
    var showPlayback by rememberSaveable { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = palette.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (includeHeader) {
                HiPlayerHeader(
                    testTag = "settings_header",
                    onSearchClick = { },
                    onRefreshClick = { libraryViewModel.refreshVideos() },
                    onStreamClick = { }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 0.dp, vertical = 0.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            SettingsCollapsibleSection(
                title = "Google account",
                icon = Icons.Default.AccountCircle,
                subtitle = "Your Hi Player profile and sign-in",
                expanded = showAccountSources,
                onToggle = { showAccountSources = !showAccountSources }
            ) {
                com.example.ui.components.GoogleAccountCard(onPlayDriveMedia = onPlayDriveMedia, includeDriveConnection = false)
            }
            SettingsCollapsibleSection(
                title = "Connections",
                icon = Icons.Default.CloudQueue,
                subtitle = "Cloud drives, network folders and media servers",
                expanded = showConnections,
                onToggle = { showConnections = !showConnections }
            ) {
                DiscoverSourceSettings(
                    discoverViewModel = discoverViewModel,
                    onPlayMedia = onPlayDriveMedia,
                    modifier = Modifier.padding(horizontal = 0.dp)
                )
            }

            // 1. Theme & Appearance (Horizontal Form)
            SettingsCollapsibleSection(
                title = "Themes & appearance",
                icon = Icons.Default.Palette,
                subtitle = "Choose presets or customize your colors",
                expanded = showThemes,
                onToggle = { showThemes = !showThemes }
            ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Text(
                    text = "Choose a theme",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textPrimary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
                )
                Text(
                    text = "System follows your device’s appearance.",
                    fontSize = 12.sp,
                    color = palette.textSecondary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Horizontal Theme Selector
                com.example.ui.components.HorizontalThemeSelector(
                    selectedTheme = settings.themeMode,
                    customPalette = getCustomPalette(settings),
                    onThemeSelected = { mode ->
                        libraryViewModel.setThemeMode(mode)
                        showCustomTheme = mode == AppThemeMode.CUSTOM
                    },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                )

                TextButton(
                    onClick = { showCustomTheme = !showCustomTheme },
                    modifier = Modifier.padding(horizontal = 6.dp).heightIn(min = 48.dp),
                ) {
                    Text(if (showCustomTheme) "Hide customization" else "Customize theme",
                        color = palette.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                if (showCustomTheme) {
                    ThemeCustomizationPanel(
                        settings = settings,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        onChange = { text, background, primary, surface, textOpacity, backgroundOpacity, primaryOpacity, surfaceOpacity ->
                            libraryViewModel.setCustomThemeColors(
                                text, background, primary, surface,
                                textOpacity, backgroundOpacity, primaryOpacity, surfaceOpacity,
                            )
                        },
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
            }

            // 2. Global Text Size
            SettingsCollapsibleSection(
                title = "Text size",
                icon = Icons.Default.FormatSize,
                subtitle = "Adjust text throughout Hi Player",
                expanded = showTextSize,
                onToggle = { showTextSize = !showTextSize }
            ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "App text size",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = palette.textPrimary
                    )
                    Text(
                        text = "${settings.uiTextSizeSp}sp",
                        fontSize = 13.sp,
                        color = palette.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = settings.uiTextSizeSp.toFloat(),
                    onValueChange = { libraryViewModel.setUiTextSize(it.roundToInt()) },
                    valueRange = AppTextSize.sliderRange,
                    steps = AppTextSize.SLIDER_STEPS,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_ui_text_size_slider")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Small", fontSize = 11.sp, color = palette.textSecondary)
                    Text("Large", fontSize = 11.sp, color = palette.textSecondary)
                }
            }
            }

            // 3. Hardware Decoder Mode
            SettingsCollapsibleSection(
                title = "Decoder & hardware acceleration",
                icon = Icons.Default.Memory,
                subtitle = "Choose automatic, hardware, or software decoding",
                expanded = showDecoder,
                onToggle = { showDecoder = !showDecoder }
            ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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

            // 4. Playback & Multitasking
            SettingsCollapsibleSection(
                title = "Playback & multitasking",
                icon = Icons.Default.Headphones,
                subtitle = "Background audio and picture-in-picture",
                expanded = showPlayback,
                onToggle = { showPlayback = !showPlayback }
            ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (android.os.Build.VERSION.SDK_INT >= 30) PreviousProcessExit()
                SettingsSwitchItem(
                    icon = Icons.Default.Headphones,
                    title = "Background Audio Playback",
                    subtitle = "Keep player audio and supported browser videos playing when the app is minimized or the screen is locked",
                    checked = settings.backgroundPlayEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) pendingPlaybackPermission = "background"
                        else libraryViewModel.setBackgroundPlay(false)
                    }
                )
                HorizontalDivider(color = palette.surfaceBorder)
                SettingsSwitchItem(
                    icon = Icons.Default.PictureInPictureAlt,
                    title = "Auto Picture-in-Picture",
                    subtitle = "Enter PiP automatically when returning to Home screen",
                    checked = settings.autoPipEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) pendingPlaybackPermission = "pip"
                        else libraryViewModel.setAutoPip(false)
                    }
                )
            }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    pendingPlaybackPermission?.let { mode ->
        val isBackground = mode == "background"
        AlertDialog(
            modifier = androidx.compose.ui.Modifier.hiContainerBorder(androidx.compose.foundation.shape.RoundedCornerShape(28.dp)),
            onDismissRequest = { pendingPlaybackPermission = null },
            title = { Text(if (isBackground) "Allow Background Play?" else "Allow Picture-in-Picture?") },
            text = {
                Text(if (isBackground) {
                    "Audio may continue when Hi Player is minimized or the screen is locked."
                } else {
                    "Allow Hi Player to enter a floating video window automatically when you leave the player? Android has no separate runtime PiP permission; tapping Allow is your consent."
                })
            },
            confirmButton = {
                Button(onClick = {
                    if (isBackground) libraryViewModel.setBackgroundPlay(true) else libraryViewModel.setAutoPip(true)
                    pendingPlaybackPermission = null
                }) { Text("Allow") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { pendingPlaybackPermission = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingsCollapsibleSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = LocalHiPalette.current
    Card(
        border = BorderStroke(0.5.dp, palette.surfaceBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = HiCardShape,
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.animateContentSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 76.dp)
                    .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                    .clickable(role = Role.Button, onClick = onToggle)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(40.dp).background(palette.selectionSurface, HiThumbnailShape),
                    contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = palette.primary, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = palette.textPrimary, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, color = palette.textSecondary, fontSize = 12.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = palette.textSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
            if (expanded) {
                HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = palette.surfaceBorder.copy(alpha = 0.55f))
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    content = content
                )
            }
        }
    }
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
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 8.dp),
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
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = palette.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    color = palette.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = contrastContentColor(palette.primary, palette.surface),
                checkedTrackColor = palette.primary,
                checkedBorderColor = palette.primary,
                uncheckedThumbColor = palette.textSecondary,
                uncheckedTrackColor = palette.surfaceElevated,
                uncheckedBorderColor = palette.surfaceBorder
            )
        )
    }
}


@androidx.annotation.RequiresApi(30)
@Composable
private fun PreviousProcessExit() {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext
    var report by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(app) {
        report = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val manager = app.getSystemService(android.app.ActivityManager::class.java)
                val exit = manager.getHistoricalProcessExitReasons(app.packageName, 0, 12)
                    .firstOrNull { it.processName == app.applicationInfo.processName } ?: return@runCatching null
                val reason = when (exit.reason) {
                    android.app.ApplicationExitInfo.REASON_CRASH -> "App crash"
                    android.app.ApplicationExitInfo.REASON_CRASH_NATIVE -> "Native crash"
                    android.app.ApplicationExitInfo.REASON_LOW_MEMORY -> "Low memory"
                    android.app.ApplicationExitInfo.REASON_ANR -> "App stopped responding"
                    else -> "Android reason ${exit.reason}"
                }
                "Last app exit · $reason · ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(exit.timestamp))}"
            }.getOrNull()
        }
    }
    report?.let { androidx.compose.material3.Text(it) }
}
