package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalHiPalette
import com.example.ui.theme.LocalHiUiMetrics

/** One status-bar inset. Narrow/large-text headers wrap actions instead of squeezing text. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HiPlayerHeader(
    title: String = "Hi Player",
    modifier: Modifier = Modifier,
    showSearch: Boolean = true,
    searchActive: Boolean = false,
    refreshing: Boolean = false,
    onSearchClick: (() -> Unit)? = {},
    onRefreshClick: (() -> Unit)? = {},
    onStreamClick: (() -> Unit)? = {},
    testTag: String = "hi_player_header",
    onAccountClick: (() -> Unit)? = null,
    extraActions: @Composable (() -> Unit)? = null,
) {
    val palette = LocalHiPalette.current
    val metrics = LocalHiUiMetrics.current
    val fontScale = LocalDensity.current.fontScale
    val brand: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HiPlayerLogoBadge(size = metrics.logoSize)
            Spacer(Modifier.width(10.dp))
            Text(title, modifier = Modifier.weight(1f), fontSize = metrics.titleTextSize,
                fontWeight = FontWeight.Bold, color = palette.primary)
        }
    }
    val actions: @Composable () -> Unit = {
        if (onRefreshClick != null) IconButton(enabled = !refreshing, onClick = onRefreshClick, modifier = Modifier.size(48.dp)) {
            if (refreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = palette.primary)
            else Icon(Icons.Default.Refresh, "Refresh", tint = palette.textPrimary)
        }
        if (showSearch && onSearchClick != null) IconButton(onClick = onSearchClick, modifier = Modifier.size(48.dp)) {
            Icon(if (searchActive) Icons.Default.Clear else Icons.Default.Search,
                if (searchActive) "Close search" else "Search", tint = palette.textPrimary)
        }
        if (onStreamClick != null) IconButton(onClick = onStreamClick, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Cast, "Stream and Google Cast", tint = palette.textPrimary)
        }
        extraActions?.invoke()
        onAccountClick?.let { GoogleAccountAvatar(onClick = it) }
    }
    Surface(modifier.fillMaxWidth().statusBarsPadding().testTag(testTag), color = palette.surface) {
        BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = metrics.headerHeight)
            .padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (maxWidth < 420.dp || fontScale > 1.3f) {
                Column(Modifier.fillMaxWidth()) {
                    brand()
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { actions() }
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { brand() }
                    Row(verticalAlignment = Alignment.CenterVertically) { actions() }
                }
            }
        }
    }
}
