package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalHiPalette

/** Replaces a folder title in the same 64dp slot; never adds another app bar. */
@Composable
fun InlineSelectionToolbar(
    count: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onProperties: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalHiPalette.current
    Row(modifier.fillMaxWidth().height(64.dp).testTag("inline_selection_toolbar"),
        verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Exit selection", tint = palette.textPrimary) }
        Text("$count", Modifier.weight(1f).semantics { contentDescription = "$count selected" },
            color = palette.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1)
        IconButton(onClick = onSelectAll) { Icon(Icons.Default.SelectAll, "Select all", tint = palette.textPrimary) }
        IconButton(onClick = onClear, enabled = count > 0) { Icon(Icons.Default.Deselect, "Deselect all", tint = palette.textSecondary) }
        IconButton(onClick = onProperties, enabled = count > 0) { Icon(Icons.Default.Info, "Properties", tint = palette.textPrimary) }
        IconButton(onClick = onDelete, enabled = count > 0) { Icon(Icons.Default.Delete, "Delete", tint = palette.primary) }
    }
}
