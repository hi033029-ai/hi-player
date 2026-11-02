package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.LibraryToolbarRow
import com.example.ui.theme.LocalHiPalette
import com.example.ui.theme.contrastContentColor
import com.example.viewmodel.LibraryNavMode
import com.example.viewmodel.LibraryViewMode
import com.example.viewmodel.VideoSortOption

/** Stable controls: only the library content below these controls participates in the pager. */
@Composable
internal fun VideoLibraryHeaderControls(
    modes: List<LibraryNavMode>, selected: LibraryNavMode, folderCount: Int, videoCount: Int,
    recentCount: Int, favoriteCount: Int, query: String, onQueryChange: (String) -> Unit,
    viewMode: LibraryViewMode, onToggleView: () -> Unit, onSort: (VideoSortOption) -> Unit,
    onSelect: (LibraryNavMode) -> Unit,
) {
    val palette = LocalHiPalette.current
    var searching by rememberSaveable { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val rowState = rememberLazyListState()
    LaunchedEffect(searching) { if (searching) focus.requestFocus() }
    LaunchedEffect(selected) { modes.indexOf(selected).takeIf { it >= 0 }?.let { rowState.animateScrollToItem(it) } }
    Column(Modifier.fillMaxWidth().testTag("video_library_stable_controls")) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                .heightIn(min = 56.dp).clickable { searching = true }.testTag("search_video_card"),
            shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = palette.surfaceElevated),
            border = androidx.compose.foundation.BorderStroke(1.dp, palette.surfaceBorder),
        ) {
            if (searching) {
                OutlinedTextField(value = query, onValueChange = onQueryChange, singleLine = true,
                    placeholder = { Text("Search video here", color = palette.textSecondary, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = palette.textSecondary) },
                    trailingIcon = { IconButton(onClick = { onQueryChange(""); searching = false }) {
                        Icon(Icons.Default.Clear, "Close search", tint = palette.textSecondary)
                    } },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary, focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus))
            } else Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Search, null, tint = palette.textSecondary)
                Text(if (query.isBlank()) "Search video here" else query, color = palette.textSecondary, fontSize = 14.sp)
            }
        }
        if (query.isBlank()) LibraryToolbarRow(
            categories = { categoryModifier ->
                LazyRow(state = rowState, modifier = categoryModifier.testTag("video_category_row"),
                    contentPadding = PaddingValues(end = 4.dp, top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    items(modes, key = { it.name }) { mode ->
                        val label = when (mode) {
                            LibraryNavMode.FOLDERS -> "Folders ($folderCount)"
                            LibraryNavMode.ALL_VIDEOS -> "Videos ($videoCount)"
                            LibraryNavMode.RECENT -> "Recent ($recentCount)"
                            LibraryNavMode.TREE_FOLDERS -> "Tree Folders ($folderCount)"
                            LibraryNavMode.FAVORITES -> "Favorites ($favoriteCount)"
                            LibraryNavMode.ALL_FOLDERS -> "Folders ($folderCount)"
                        }
                        val icon = when (mode) {
                            LibraryNavMode.FOLDERS, LibraryNavMode.ALL_FOLDERS -> Icons.Default.Folder
                            LibraryNavMode.ALL_VIDEOS -> Icons.Default.VideoLibrary
                            LibraryNavMode.RECENT -> Icons.Default.Refresh
                            LibraryNavMode.TREE_FOLDERS -> Icons.Default.FolderOpen
                            LibraryNavMode.FAVORITES -> Icons.Default.Star
                        }
                        val chosen = selected == mode
                        val background by animateColorAsState(if (chosen) palette.primary else palette.surfaceElevated,
                            animationSpec = tween(220), label = "category_background")
                        Row(Modifier.heightIn(min = 48.dp).clickable { onSelect(mode) }.padding(vertical = 6.dp)
                            .clip(RoundedCornerShape(8.dp)).background(background).padding(horizontal = 8.dp)
                            .testTag("video_category_${mode.name}"), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val tint = if (chosen) contrastContentColor(palette.primary, palette.surface) else palette.textSecondary
                            Icon(icon, label, tint = tint, modifier = Modifier.size(14.dp))
                            Text(label, color = if (chosen) tint else palette.textPrimary,
                                fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                        }
                    }
                }
            },
            actions = {
                IconButton(onClick = onToggleView, modifier = Modifier.size(48.dp).testTag("view_mode_toggle")) {
                    Icon(if (viewMode == LibraryViewMode.LAYER_LIST) Icons.Default.GridView else Icons.Default.ViewList,
                        "Toggle view", tint = palette.textSecondary, modifier = Modifier.size(18.dp))
                }
                Box {
                    IconButton(onClick = { sortOpen = true }, modifier = Modifier.size(48.dp).testTag("sort_by_button")) {
                        Icon(Icons.Default.Sort, "Sort videos", tint = palette.textSecondary, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false },
                        modifier = Modifier.background(palette.surfaceElevated)) {
                        VideoSortOption.entries.forEach { option ->
                            DropdownMenuItem(text = { Text(option.displayName, color = palette.textPrimary, fontSize = 11.sp) },
                                onClick = { sortOpen = false; onSort(option) })
                        }
                    }
                }
            },
        )
    }
}
