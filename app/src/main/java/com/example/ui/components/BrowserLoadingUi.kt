package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.example.ui.screens.BrowserPlaceholderLayout
import com.example.ui.theme.LocalHiPalette

/** Indeterminate activity, not a percentage inferred from WebView resource counts. */
@Composable
fun BrowserLoadingBar(modifier: Modifier = Modifier) {
    val palette = LocalHiPalette.current
    val transition = rememberInfiniteTransition(label = "browser-loading")
    val travel by transition.animateFloat(0f, 1f,
        animationSpec = infiniteRepeatable(tween(850, easing = LinearEasing), RepeatMode.Reverse),
        label = "browser-loading-travel")
    BoxWithConstraints(modifier.progressSemantics().clip(RoundedCornerShape(2.dp))
        .background(palette.surfaceBorder.copy(alpha = .4f))) {
        val segment = maxWidth * .24f
        Box(Modifier.offset(x = (maxWidth - segment) * travel).width(segment)
            .fillMaxHeight().background(palette.primary))
    }
}

/** Route-specific approximate structure only; removed on actual first paint. */
@Composable
fun BrowserSiteSkeleton(layout: BrowserPlaceholderLayout, modifier: Modifier = Modifier) {
    if (layout == BrowserPlaceholderLayout.NONE) return
    LazyColumn(modifier.fillMaxSize().clearAndSetSemantics {
        contentDescription = "YouTube page content loading"
    }, userScrollEnabled = false, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        if (layout == BrowserPlaceholderLayout.YOUTUBE_FEED) {
            item { Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                SiteSkeletonBlock(Modifier.size(90.dp, 24.dp))
                SiteSkeletonBlock(Modifier.size(24.dp))
            } }
            items(3) { YoutubeVideoPlaceholder() }
        } else {
            item { SiteSkeletonBlock(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) }
            item { Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SiteSkeletonBlock(Modifier.fillMaxWidth(.85f).height(18.dp))
                SiteSkeletonBlock(Modifier.fillMaxWidth(.55f).height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SiteSkeletonBlock(Modifier.size(36.dp))
                    SiteSkeletonBlock(Modifier.width(120.dp).height(24.dp))
                }
            } }
            items(2) { YoutubeVideoPlaceholder() }
        }
    }
}

@Composable
private fun YoutubeVideoPlaceholder() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SiteSkeletonBlock(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SiteSkeletonBlock(Modifier.size(36.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SiteSkeletonBlock(Modifier.fillMaxWidth(.9f).height(14.dp))
                SiteSkeletonBlock(Modifier.fillMaxWidth(.55f).height(10.dp))
            }
        }
    }
}

@Composable
private fun SiteSkeletonBlock(modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(5.dp))
        .background(LocalHiPalette.current.surfaceBorder.copy(alpha = .55f)))
}
