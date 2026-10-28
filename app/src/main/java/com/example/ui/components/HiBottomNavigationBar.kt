package com.example.ui.components

import com.example.ui.theme.hiContainerBorder

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalHiPalette

private data class TabSpec(
    val tab: NavTab,
    val label: String,
    val icon: ImageVector,
    val tag: String
)

private data class TabMetrics(
    val center: Dp = 0.dp,
    val width: Dp = 0.dp
)

@Composable
fun HiBottomNavigationBar(
    currentTab: NavTab,
    onTabSelected: (NavTab) -> Unit,
    onOpenHubSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalHiPalette.current
    val tabs = remember {
        listOf(
            TabSpec(NavTab.VIDEOS, "Videos", Icons.Default.Movie, "nav_item_videos"),
            TabSpec(NavTab.MUSIC, "Music", Icons.Default.MusicNote, "nav_item_music"),
            TabSpec(NavTab.BROWSER, "Browser", Icons.Default.Language, "nav_item_browser"),
            TabSpec(NavTab.FILE_MANAGER, "File Manager", Icons.Default.Folder, "nav_item_files"),
            TabSpec(NavTab.SETTINGS, "Settings", Icons.Default.Settings, "nav_item_settings")
        )
    }
    val density = androidx.compose.ui.platform.LocalDensity.current
    var metrics by remember { mutableStateOf(List(tabs.size) { TabMetrics() }) }
    val activeIndex = tabs.indexOfFirst { it.tab == currentTab }.coerceAtLeast(0)
    val activeMetrics = metrics[activeIndex]
    val progressWidth by animateDpAsState(
        targetValue = (activeMetrics.width * 0.42f).coerceAtLeast(18.dp),
        animationSpec = tween(durationMillis = 300),
        label = "tab-progress-width"
    )
    val progressX by animateDpAsState(
        targetValue = activeMetrics.center - (progressWidth / 2),
        animationSpec = tween(durationMillis = 300),
        label = "tab-progress-x"
    )

    // The bar floats in a see-through, outlined pill instead of sitting on a
    // solid slab, so the screen colour shows around and through it.
    val pillShape = RoundedCornerShape(30.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(82.dp)
            .testTag("hi_bottom_navigation_bar"),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 9.dp)
                .fillMaxWidth()
                .height(64.dp)
                .clip(pillShape)
                .background(palette.surface.copy(alpha = 0.62f))
                .border(1.dp, palette.surfaceBorder.copy(alpha = 0.95f), pillShape)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, spec ->
                    val selected = index == activeIndex
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .height(56.dp)
                            .onGloballyPositioned { coordinates ->
                                val centerPx = coordinates.positionInParent().x + coordinates.size.width / 2f
                                val centerDp = with(density) { centerPx.toDp() } + 4.dp
                                val widthDp = with(density) { coordinates.size.width.toDp() }
                                if (metrics[index].center != centerDp || metrics[index].width != widthDp) {
                                    metrics = metrics.toMutableList().also {
                                        it[index] = TabMetrics(centerDp, widthDp)
                                    }
                                }
                            }
                            .clip(RoundedCornerShape(24.dp)).hiContainerBorder(RoundedCornerShape(24.dp))
                            .testTag(spec.tag)
                            .clickable { onTabSelected(spec.tab) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.offset(y = if (selected) (-3).dp else 0.dp)
                        ) {
                            Icon(
                                imageVector = spec.icon,
                                contentDescription = spec.label,
                                modifier = Modifier.size(22.dp),
                                tint = if (selected) palette.primary else palette.textSecondary
                            )
                            Text(
                                text = spec.label,
                                color = if (selected) palette.primary else palette.textSecondary,
                                fontSize = 10.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // A short active segment glides along the bottom edge of the pill.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 5.dp)
                    .offset(x = progressX)
                    .width(progressWidth)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)).hiContainerBorder(RoundedCornerShape(2.dp))
                    .background(palette.primary)
                    .testTag("nav_progress_segment")
            )
        }
    }
}
