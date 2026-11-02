package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalHiPalette

/** One measured row. Only categories scroll; 48dp actions never overlay their viewport. */
@Composable
fun LibraryToolbarRow(
    categories: @Composable (Modifier) -> Unit,
    actions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().background(LocalHiPalette.current.surface)
        .heightIn(min = 48.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        categories(Modifier.weight(1f).clipToBounds())
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}
