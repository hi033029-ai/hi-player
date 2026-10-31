package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalHiPalette

enum class SkeletonLayout { MEDIA_LIST, MEDIA_GRID, FILES, POSTERS }

/** Static, non-interactive placeholders; no shimmer loop or fake progress percentage. */
@Composable
fun ContentSkeleton(
    label: String,
    modifier: Modifier = Modifier,
    layout: SkeletonLayout = SkeletonLayout.MEDIA_LIST,
    gridMinSize: Dp = 128.dp,
) {
    val palette = LocalHiPalette.current
    Column(modifier.fillMaxSize().clearAndSetSemantics {
        contentDescription = label
        liveRegion = LiveRegionMode.Polite
    }) {
        Text(label, color = palette.textSecondary, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        if (layout == SkeletonLayout.MEDIA_GRID || layout == SkeletonLayout.POSTERS) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (layout == SkeletonLayout.POSTERS) 110.dp else gridMinSize),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                userScrollEnabled = false,
            ) {
                items(8) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(if (layout == SkeletonLayout.POSTERS) 2f / 3f else 16f / 9f))
                        SkeletonBlock(Modifier.fillMaxWidth(.8f).height(14.dp))
                        SkeletonBlock(Modifier.fillMaxWidth(.5f).height(10.dp))
                    }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp), userScrollEnabled = false) {
                items(8) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        SkeletonBlock(if (layout == SkeletonLayout.FILES) Modifier.size(36.dp) else Modifier.size(96.dp, 54.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SkeletonBlock(Modifier.fillMaxWidth(.8f).height(14.dp))
                            SkeletonBlock(Modifier.fillMaxWidth(.5f).height(10.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkeletonBlock(modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(LocalHiPalette.current.surfaceBorder.copy(alpha = .55f)))
}

/** Neutral webpage placeholder, not a fabricated preview of a third-party site. */
@Composable
fun BrowserPageSkeleton(label: String, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize().clearAndSetSemantics {
            contentDescription = label
            liveRegion = LiveRegionMode.Polite
        },
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        userScrollEnabled = false,
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                SkeletonBlock(Modifier.size(40.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SkeletonBlock(Modifier.fillMaxWidth(.6f).height(14.dp))
                    SkeletonBlock(Modifier.fillMaxWidth(.35f).height(10.dp))
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                SkeletonBlock(Modifier.fillMaxWidth(.85f).height(16.dp))
                SkeletonBlock(Modifier.fillMaxWidth(.6f).height(12.dp))
            }
        }
        items(5) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                SkeletonBlock(Modifier.size(88.dp, 52.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SkeletonBlock(Modifier.fillMaxWidth(.9f).height(14.dp))
                    SkeletonBlock(Modifier.fillMaxWidth(.55f).height(10.dp))
                }
            }
        }
    }
}
