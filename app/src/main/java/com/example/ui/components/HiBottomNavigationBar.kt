package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalHiPalette

private data class TabSpec(
    val tab: NavTab,
    val label: String,
    val icon: ImageVector,
    val tag: String,
)

@Composable
fun HiBottomNavigationBar(
    currentTab: NavTab,
    onTabSelected: (NavTab) -> Unit,
    onOpenHubSheet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalHiPalette.current
    val tabs = remember {
        listOf(
            TabSpec(NavTab.VIDEOS, "Home", Icons.Default.Home, "nav_item_home"),
            TabSpec(NavTab.MUSIC, "Music", Icons.Default.MusicNote, "nav_item_music"),
            TabSpec(NavTab.BROWSER, "Browser", Icons.Default.Language, "nav_item_browser"),
            TabSpec(NavTab.FILE_MANAGER, "Files", Icons.Default.Folder, "nav_item_files"),
            TabSpec(NavTab.SETTINGS, "Settings", Icons.Default.Settings, "nav_item_settings"),
        )
    }
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("hi_bottom_navigation_bar"),
        shape = RoundedCornerShape(26.dp),
        color = palette.surface,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup().padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { spec ->
                val selected = spec.tab == currentTab
                val indicator by animateColorAsState(
                    targetValue = if (selected) palette.selectionSurface else Color.Transparent,
                    animationSpec = tween(180),
                    label = "navigation_indicator",
                )
                Column(
                    modifier = Modifier.weight(1f).heightIn(min = 64.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .selectable(selected = selected, role = Role.Tab, onClick = { onTabSelected(spec.tab) })
                        .padding(horizontal = 2.dp, vertical = 6.dp)
                        .testTag(spec.tag),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                ) {
                    Box(
                        modifier = Modifier.size(width = 44.dp, height = 30.dp)
                            .background(indicator, RoundedCornerShape(15.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(spec.icon, contentDescription = null, modifier = Modifier.size(22.dp),
                            tint = if (selected) palette.primary else palette.textSecondary)
                    }
                    Text(
                        spec.label,
                        color = if (selected) palette.textPrimary else palette.textSecondary,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
