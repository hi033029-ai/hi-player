package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.viewmodel.AdvancedFileToolsViewModel
import java.io.File

@Composable
internal fun AdvancedFileTools(directory: File, selected: List<File>, onDismiss: () -> Unit, onChanged: () -> Unit,
    model: AdvancedFileToolsViewModel = viewModel()) {
    val state by model.state.collectAsState()
    var prefix by remember { mutableStateOf("Media") }
    var start by remember { mutableStateOf("1") }
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(directory.absolutePath, selected.map { it.absolutePath }) { model.resetIdle() }
    LaunchedEffect(state.revision) { if (state.revision > 0) onChanged() }
    Dialog(onDismissRequest = { if (state.busy) model.cancel() else onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row {
                    Text("File organization", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { if (state.busy) model.cancel() else onDismiss() }) { Text(if (state.busy) "Stop" else "Close") }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Batch rename", style = MaterialTheme.typography.titleMedium)
                    Text("Select files in the file manager first. Up to 200 regular files in one folder; extensions are preserved. Hidden files, folders and incomplete downloads are excluded. Safe renaming copies each file before removing its original; sufficient free space is required.")
                    Text("${selected.size} selected")
                    OutlinedTextField(prefix, { prefix = it.take(40); model.clearPreview() }, label = { Text("Name prefix") }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(start, { start = it.filter(Char::isDigit).take(6); model.clearPreview() }, label = { Text("Start number") }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
                    Button(enabled = !state.busy && selected.isNotEmpty() && start.toIntOrNull() != null && prefix.isNotBlank(), onClick = { model.previewRename(selected, prefix, start.toIntOrNull() ?: 1) }) { Text("Preview names") }
                    state.preview.forEach { Text("${it.source.file.name} → ${it.target.name}") }
                    if (state.preview.isNotEmpty()) Button(enabled = !state.busy, onClick = { confirm = true }) { Text("Rename ${state.preview.size} files…") }
                    HorizontalDivider()
                    Text("Find duplicates in ${directory.name}", style = MaterialTheme.typography.titleMedium)
                    Text("Checks matching-size files using SHA-256 in this folder only, not subfolders. Limit: 2,000 files and 20 GiB read per scan. Changed or unreadable files are skipped. Results are review-only; nothing is automatically deleted.")
                    Button(enabled = !state.busy, onClick = { model.scanDuplicates(directory) }) { Text("Scan this folder") }
                    Text(state.message)
                    state.duplicates.forEachIndexed { index, files ->
                        Text("Group ${index + 1} · ${files.size} copies", style = MaterialTheme.typography.titleSmall)
                        files.forEach { Text(it.name) }
                    }
                }
            }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Rename these files?") }, text = { Text("Names will change on disk. Completed renames are not undone if you stop. Existing target files are never overwritten.") },
        confirmButton = { TextButton(onClick = { confirm = false; model.rename() }) { Text("Rename") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
}
