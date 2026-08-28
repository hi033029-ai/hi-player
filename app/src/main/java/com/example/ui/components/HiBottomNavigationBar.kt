package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalHiPalette

@Composable
fun HiBottomNavigationBar(
    currentTab: NavTab,
    onTabSelected: (NavTab) -> Unit,
    onOpenHubSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalHiPalette.current

    NavigationBar(
        modifier = modifier
            .fillMaxWidth()
            .testTag("hi_bottom_navigation_bar"),
        containerColor = palette.surface,
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            selected = currentTab == NavTab.VIDEOS,
            onClick = { onTabSelected(NavTab.VIDEOS) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = "Videos",
                    modifier = Modifier.size(22.dp)
                )
            },
            label = {
                Text(
                    text = "Videos",
                    fontSize = 11.sp,
                    fontWeight = if (currentTab == NavTab.VIDEOS) FontWeight.Bold else FontWeight.Normal
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = if (palette.isDark) Color.Black else Color.White,
                selectedTextColor = palette.primary,
                indicatorColor = palette.primary,
                unselectedIconColor = palette.textSecondary,
                unselectedTextColor = palette.textSecondary
            ),
            modifier = Modifier.testTag("nav_item_videos")
        )

        NavigationBarItem(
            selected = currentTab == NavTab.MUSIC,
            onClick = { onTabSelected(NavTab.MUSIC) },
            icon = {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = "Music",
                    modifier = Modifier.size(22.dp)
                )
            },
            label = {
                Text(
                    text = "Music",
                    fontSize = 11.sp,
                    fontWeight = if (currentTab == NavTab.MUSIC) FontWeight.Bold else FontWeight.Normal
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = if (palette.isDark) Color.Black else Color.White,
                selectedTextColor = palette.primary,
                indicatorColor = palette.primary,
                unselectedIconColor = palette.textSecondary,
                unselectedTextColor = palette.textSecondary
            ),
            modifier = Modifier.testTag("nav_item_music")
        )

        NavigationBarItem(
            selected = currentTab == NavTab.FILE_MANAGER,
            onClick = { onTabSelected(NavTab.FILE_MANAGER) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = "Files",
                    modifier = Modifier.size(22.dp)
                )
            },
            label = {
                Text(
                    text = "Files",
                    fontSize = 11.sp,
                    fontWeight = if (currentTab == NavTab.FILE_MANAGER) FontWeight.Bold else FontWeight.Normal
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = if (palette.isDark) Color.Black else Color.White,
                selectedTextColor = palette.primary,
                indicatorColor = palette.primary,
                unselectedIconColor = palette.textSecondary,
                unselectedTextColor = palette.textSecondary
            ),
            modifier = Modifier.testTag("nav_item_files")
        )

        NavigationBarItem(
            selected = currentTab == NavTab.SETTINGS,
            onClick = { onTabSelected(NavTab.SETTINGS) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    modifier = Modifier.size(22.dp)
                )
            },
            label = {
                Text(
                    text = "Settings",
                    fontSize = 11.sp,
                    fontWeight = if (currentTab == NavTab.SETTINGS) FontWeight.Bold else FontWeight.Normal
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = if (palette.isDark) Color.Black else Color.White,
                selectedTextColor = palette.primary,
                indicatorColor = palette.primary,
                unselectedIconColor = palette.textSecondary,
                unselectedTextColor = palette.textSecondary
            ),
            modifier = Modifier.testTag("nav_item_settings")
        )
    }
}
