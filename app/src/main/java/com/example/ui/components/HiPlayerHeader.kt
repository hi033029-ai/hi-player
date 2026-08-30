package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalHiPalette
import com.example.ui.theme.LocalHiUiMetrics

/**
 * Shared compact header. It owns the single status-bar inset for every screen.
 *
 * The "Hi Player" title and logo always stay put - screens never replace them
 * with a folder name or a back arrow. Per-section context (e.g. "4K Video",
 * "Audio", "Files", "Settings") goes in [subtitle], rendered small underneath
 * the title, same as it used to be before the header was reworked.
 */
@Composable
fun HiPlayerHeader(
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showSearch: Boolean = false,
    searchActive: Boolean = false,
    onSearchClick: (() -> Unit)? = null,
    onRefreshClick: (() -> Unit)? = null,
    testTag: String = "hi_player_header",
    extraActions: @Composable (() -> Unit)? = null
) {
    val palette = LocalHiPalette.current
    val metrics = LocalHiUiMetrics.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag),
        color = palette.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(metrics.headerHeight)
                .statusBarsPadding()
                .padding(start = 16.dp, top = 10.dp, end = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            HiPlayerLogoBadge(size = metrics.logoSize)
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Hi Player",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary,
                    maxLines = 1
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = palette.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            if (showSearch && onSearchClick != null) {
                IconButton(onClick = onSearchClick, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = if (searchActive) Icons.Default.Clear else Icons.Default.Search,
                        contentDescription = if (searchActive) "Close search" else "Search",
                        tint = palette.textPrimary
                    )
                }
            }
            if (onRefreshClick != null) {
                IconButton(onClick = onRefreshClick, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = palette.textPrimary
                    )
                }
            }
            extraActions?.invoke()
        }
    }
}
