package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalHiPalette

internal fun musicSelectedLabelColor(background: Color): Color =
    com.example.ui.theme.contrastContentColor(background)

@Composable
internal fun MusicLibraryToolbar(
    selected: String, onCategory: (String) -> Unit,
    grid: Boolean, onGridChange: (Boolean) -> Unit,
    sort: String, onSort: (String) -> Unit,
) {
    val palette = LocalHiPalette.current
    var viewMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
        .clip(RoundedCornerShape(18.dp)).background(palette.surfaceElevated)
        .padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        LazyRow(Modifier.weight(1f).testTag("music_library_categories"),
            horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf("All Music", "Folders", "Playlists", "Albums", "Artists").forEach { category ->
                item(key = category) {
                    val active = selected == category
                    Box(Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (active) palette.primary else Color.Transparent)
                        .clickable { onCategory(category) }.padding(horizontal = 14.dp)
                        .testTag("music_library_category_$category"), contentAlignment = Alignment.Center) {
                        Text(if (category == "All Music") "Songs" else category,
                            color = if (active) com.example.ui.theme.contrastContentColor(palette.primary, palette.surface) else palette.textSecondary,
                            fontSize = 12.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1)
                    }
                }
            }
        }
        Box {
            IconButton(onClick = { viewMenu = true }, modifier = Modifier.size(48.dp).testTag("audio_view_button")) {
                Icon(if (grid) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                    if (grid) "Grid view; change layout" else "List view; change layout", tint = palette.textPrimary)
            }
            DropdownMenu(expanded = viewMenu, onDismissRequest = { viewMenu = false }) {
                listOf("List" to false, "Grid" to true).forEach { (name, value) ->
                    DropdownMenuItem(text = { Text(if (grid == value) "$name ✓" else name) },
                        onClick = { onGridChange(value); viewMenu = false })
                }
            }
        }
        Box {
            IconButton(onClick = { sortMenu = true }, modifier = Modifier.size(48.dp).testTag("audio_sort_button")) {
                Icon(Icons.AutoMirrored.Filled.Sort, "Sort music: $sort", tint = palette.textPrimary)
            }
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                listOf("Custom", "Name", "Artist", "Duration", "Newest").forEach { option ->
                    DropdownMenuItem(text = { Text(if (sort == option) "$option ✓" else option) },
                        onClick = { onSort(option); sortMenu = false })
                }
            }
        }
    }
}
