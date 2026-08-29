package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.LocalHiPalette

enum class NavTab {
    VIDEOS,
    MUSIC,
    FILE_MANAGER,
    SETTINGS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiNavigationBottomSheet(
    sheetState: SheetState,
    currentTab: NavTab,
    onTabSelected: (NavTab) -> Unit,
    onDismiss: () -> Unit,
    onRefreshStorage: () -> Unit,
    videoCount: Int = 0,
    audioCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val palette = LocalHiPalette.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(palette.textTertiary.copy(alpha = 0.4f))
            )
        },
        modifier = modifier.testTag("hi_navigation_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 6.dp)
        ) {
            // Header with Hi Logo & Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    com.example.ui.components.HiPlayerLogoBadge(
                        size = 38.dp
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Hi Player Hub",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = palette.textPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .background(
                                        palette.primary,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "PRO",
                                    color = if (palette.isDark) Color.Black else Color.White,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            text = "Videos • Music • File Manager • Settings",
                            fontSize = 10.5.sp,
                            color = palette.textSecondary
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_sheet_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = palette.textSecondary)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = palette.surfaceBorder)
            Spacer(modifier = Modifier.height(10.dp))

            // Navigation Hub Tiles
            Text(
                text = "MAIN NAVIGATION",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = palette.primary,
                letterSpacing = 0.8.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 1. VIDEOS
            NavHubTile(
                title = "Videos",
                subtitle = "4K UHD Blu-ray, HDR10+, Tree Folders",
                badge = if (videoCount > 0) "$videoCount files" else "Library",
                icon = Icons.Default.Movie,
                iconColor = palette.primary,
                isSelected = currentTab == NavTab.VIDEOS,
                onClick = {
                    onTabSelected(NavTab.VIDEOS)
                },
                testTag = "hub_tab_videos"
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 2. MUSIC
            NavHubTile(
                title = "Music",
                subtitle = "Lossless Audio, Equalizer & Background Play",
                badge = if (audioCount > 0) "$audioCount tracks" else "Hi-Fi",
                icon = Icons.Default.MusicNote,
                iconColor = Color(0xFF0284C7),
                isSelected = currentTab == NavTab.MUSIC,
                onClick = {
                    onTabSelected(NavTab.MUSIC)
                },
                testTag = "hub_tab_music"
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 3. FILE MANAGER
            NavHubTile(
                title = "File Manager",
                subtitle = "Explore Storage, Downloads & Media Folders",
                badge = "Storage",
                icon = Icons.Default.Folder,
                iconColor = Color(0xFFE67E22),
                isSelected = currentTab == NavTab.FILE_MANAGER,
                onClick = {
                    onTabSelected(NavTab.FILE_MANAGER)
                },
                testTag = "hub_tab_file_manager"
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 4. SETTINGS
            NavHubTile(
                title = "Settings",
                subtitle = "HW+ Decoder, 128MB Ultra-Buffer & Themes",
                badge = "Config",
                icon = Icons.Default.Settings,
                iconColor = Color(0xFF10B981),
                isSelected = currentTab == NavTab.SETTINGS,
                onClick = {
                    onTabSelected(NavTab.SETTINGS)
                },
                testTag = "hub_tab_settings"
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Actions Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.surfaceElevated)
                    .clickable {
                        onRefreshStorage()
                        onDismiss()
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = palette.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Rescan Media Storage",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.textPrimary
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = palette.textSecondary,
                    modifier = Modifier.size(14.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun NavHubTile(
    title: String,
    subtitle: String,
    badge: String,
    icon: ImageVector,
    iconColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalHiPalette.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) palette.surfaceElevated else palette.surface
        ),
        border = null,
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp,
                        color = if (isSelected) palette.primary else palette.textPrimary
                    )
                    Box(
                        modifier = Modifier
                            .background(palette.surfaceElevated, RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = badge,
                            fontSize = 9.5.sp,
                            color = palette.textSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = subtitle,
                    fontSize = 10.5.sp,
                    color = palette.textSecondary,
                    maxLines = 1
                )
            }
        }
    }
}
