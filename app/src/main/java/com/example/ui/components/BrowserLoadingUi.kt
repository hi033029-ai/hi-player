package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.example.ui.screens.BrowserPlaceholderLayout
import com.example.ui.theme.LocalHiPalette

/** Lifecycle mirrors WebView callbacks: redirects do not reset an active load. */
class BrowserProgressState {
    var loading by androidx.compose.runtime.mutableStateOf(false)
        private set
    var value by androidx.compose.runtime.mutableFloatStateOf(0f)
        private set
    var visible by androidx.compose.runtime.mutableStateOf(false)
        private set
    var generation by androidx.compose.runtime.mutableIntStateOf(0)
        private set

    private var acceptingProgress = true

    fun start() {
        if (loading) return
        acceptingProgress = true
        generation++
        loading = true
        visible = true
        value = 0f
    }
    fun onProgress(percent: Int) {
        if (!acceptingProgress) return
        if (percent >= 100) { finish(); return }
        if (!loading) start()
        value = maxOf(value, percent.coerceIn(0, 99) / 100f)
    }
    fun finish() {
        if (!loading) return
        loading = false
        value = 1f
    }
    fun restart() { loading = false; start() }
    fun cancel() { acceptingProgress = false; generation++; loading = false; visible = false; value = 0f }
    fun hide(completedGeneration: Int) {
        if (!loading && generation == completedGeneration) visible = false
    }
}

/** Real WebView progress plus a moving pixel highlight; no invented percentages. */
@Composable
fun BrowserLoadingBar(state: BrowserProgressState, modifier: Modifier = Modifier) {
    val palette = LocalHiPalette.current
    val fill = androidx.compose.runtime.remember(state) { Animatable(0f) }
    val alpha = androidx.compose.runtime.remember(state) { Animatable(0f) }
    var drawnGeneration by androidx.compose.runtime.remember(state) { androidx.compose.runtime.mutableIntStateOf(-1) }
    androidx.compose.runtime.LaunchedEffect(state.generation, state.value, state.loading) {
        val generation = state.generation
        if (drawnGeneration != generation) {
            fill.snapTo(0f)
            drawnGeneration = generation
        }
        if (state.visible) {
            alpha.snapTo(1f)
            fill.animateTo(state.value, tween(if (state.loading) 300 else 220))
            if (!state.loading) {
                alpha.animateTo(0f, tween(180))
                state.hide(generation)
            }
        }
    }
    if (!state.visible) return
    val transition = rememberInfiniteTransition(label = "browser-comet")
    val travel by transition.animateFloat(0f, 1f,
        animationSpec = infiniteRepeatable(tween(850, easing = LinearEasing), RepeatMode.Restart),
        label = "browser-comet-travel")
    BoxWithConstraints(modifier.progressSemantics().clip(RoundedCornerShape(2.dp))
        .background(palette.surfaceBorder.copy(alpha = .25f * alpha.value))) {
        // The highlight keeps moving even while the network-reported value stalls.
        val width = maxWidth * fill.value
        Box(Modifier.width(width).fillMaxHeight().background(palette.primary.copy(alpha = .6f * alpha.value)))
        val pixel = minOf(12.dp, maxWidth * .04f)
        Box(Modifier.offset(x = (maxWidth - pixel) * travel).width(pixel)
            .fillMaxHeight().background(palette.primary.copy(alpha = alpha.value)))
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
