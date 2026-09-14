package com.example.filemanager

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * Full file manager screen: browse, multi-select, create/rename/delete, copy/move
 * (cut-paste), properties, and zip extract/compose. Tap a zip file to extract it;
 * select one or more items and use "Compress" in the selection toolbar to zip them.
 */
@OptIn(
    ExperimentalComposeUiApi::class,
    ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)
@Composable
fun PortableFileManagerScreen(
    viewModel: PortableFileManagerViewModel,
    rootDir: File,
    onOpenFile: (File) -> Unit, // e.g. hand off .mp4 to the player, open images, etc.
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val event by viewModel.events.collectAsState()

    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FileEntry?>(null) }
    var propertiesTarget by remember { mutableStateOf<FileEntry?>(null) }
    var showCompressDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(event) {
        when (val e = event) {
            is FileManagerEvent.Error -> snackbarHostState.showSnackbar(e.message)
            is FileManagerEvent.Info -> snackbarHostState.showSnackbar(e.message)
            null -> {}
        }
        if (event != null) viewModel.consumeEvent()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (state.isSelectionMode) {
                SelectionTopBar(
                    selectedCount = state.selectedPaths.size,
                    onCancel = { viewModel.unselectAll() },
                    onSelectAll = { viewModel.selectAll() },
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            state.currentDir.name.ifEmpty { state.currentDir.path },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (!viewModel.navigateUp(rootDir)) onBack()
                        }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                    actions = {
                        if (state.clipboard != null) {
                            IconButton(onClick = { viewModel.pasteClipboard() }) {
                                Icon(Icons.Filled.ContentPaste, contentDescription = null)
                            }
                        }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = null)
                            }
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                DropdownMenuItem(text = { Text("New folder") }, onClick = {
                                    menuExpanded = false; showNewFolderDialog = true
                                })
                                DropdownMenuItem(text = { Text("New file") }, onClick = {
                                    menuExpanded = false; showNewFileDialog = true
                                })
                                DropdownMenuItem(text = { Text("Select all") }, onClick = {
                                    menuExpanded = false; viewModel.selectAll()
                                })
                            }
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (state.isSelectionMode) {
                SelectionBottomBar(
                    selectedCount = state.selectedPaths.size,
                    onDelete = { showDeleteConfirm = true },
                    onRename = {
                        state.entries.firstOrNull { it.path in state.selectedPaths }?.let { renameTarget = it }
                    },
                    onCopy = { viewModel.copySelectedToClipboard() },
                    onCut = { viewModel.cutSelectedToClipboard() },
                    onCompress = { showCompressDialog = true },
                    onProperties = {
                        state.entries.firstOrNull { it.path in state.selectedPaths }?.let { propertiesTarget = it }
                    },
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (state.entries.isEmpty()) {
                Text("Empty folder", modifier = Modifier.align(Alignment.Center), color = Color.Gray)
            } else {
                LazyColumn {
                    items(state.entries, key = { it.path }) { entry ->
                        FileListItem(
                            entry = entry,
                            isSelected = entry.path in state.selectedPaths,
                            isSelectionMode = state.isSelectionMode,
                            onClick = {
                                when {
                                    state.isSelectionMode -> viewModel.toggleSelection(entry)
                                    entry.isDirectory -> viewModel.navigateTo(entry.file)
                                    entry.isZip -> viewModel.extractZip(entry)
                                    else -> onOpenFile(entry.file)
                                }
                            },
                            onLongClick = {
                                if (!state.isSelectionMode) viewModel.enterSelectionMode(entry)
                            },
                        )
                    }
                }
            }

            state.progress?.let { progress ->
                OperationProgressOverlay(progress)
            }
        }
    }

    if (showNewFolderDialog) {
        NameInputDialog(
            title = "New folder",
            onConfirm = { name -> viewModel.createFolder(name); showNewFolderDialog = false },
            onDismiss = { showNewFolderDialog = false },
        )
    }
    if (showNewFileDialog) {
        NameInputDialog(
            title = "New file",
            onConfirm = { name -> viewModel.createFile(name); showNewFileDialog = false },
            onDismiss = { showNewFileDialog = false },
        )
    }
    renameTarget?.let { target ->
        NameInputDialog(
            title = "Rename",
            initialValue = target.name,
            onConfirm = { name -> viewModel.rename(target, name); renameTarget = null },
            onDismiss = { renameTarget = null },
        )
    }
    if (showCompressDialog) {
        NameInputDialog(
            title = "Compress to zip",
            initialValue = "archive.zip",
            onConfirm = { name -> viewModel.compressSelectedToZip(name); showCompressDialog = false },
            onDismiss = { showCompressDialog = false },
        )
    }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete ${state.selectedPaths.size} item(s)?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteSelected(); showDeleteConfirm = false }) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
        )
    }
    propertiesTarget?.let { target ->
        var props by remember(target) { mutableStateOf<FileProperties?>(null) }
        LaunchedEffect(target) { props = viewModel.getProperties(target) }
        PropertiesDialog(properties = props, onDismiss = { propertiesTarget = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(selectedCount: Int, onCancel: () -> Unit, onSelectAll: () -> Unit) {
    TopAppBar(
        title = { Text("$selectedCount selected") },
        navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = null) } },
        actions = {
            TextButton(onClick = onSelectAll) { Text("Select all") }
        },
    )
}

@Composable
private fun SelectionBottomBar(
    selectedCount: Int,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onCompress: () -> Unit,
    onProperties: () -> Unit,
) {
    BottomAppBar {
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
        IconButton(onClick = onCopy) { Icon(Icons.Filled.ContentCopy, contentDescription = "Copy") }
        IconButton(onClick = onCut) { Icon(Icons.Filled.ContentCut, contentDescription = "Cut") }
        IconButton(onClick = onCompress) { Icon(Icons.Filled.FolderZip, contentDescription = "Compress") }
        if (selectedCount == 1) {
            IconButton(onClick = onRename) { Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = "Rename") }
            IconButton(onClick = onProperties) { Icon(Icons.Filled.Info, contentDescription = "Properties") }
        }
    }
}

@OptIn(
    ExperimentalComposeUiApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)
@Composable
private fun FileListItem(
    entry: FileEntry,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isSelectionMode) {
            Checkbox(checked = isSelected, onCheckedChange = { onClick() })
            Spacer(modifier = Modifier.width(4.dp))
        }
        Icon(
            imageVector = iconFor(entry.kind),
            contentDescription = null,
            modifier = Modifier.size(28.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = if (entry.isDirectory) {
                    DateFormat.getDateInstance().format(Date(entry.lastModified))
                } else {
                    "${formatFileSize(entry.sizeBytes)} · ${DateFormat.getDateInstance().format(Date(entry.lastModified))}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
            )
        }
    }
}

private fun iconFor(kind: FileKind): ImageVector = when (kind) {
    FileKind.FOLDER -> Icons.Filled.Folder
    FileKind.ARCHIVE -> Icons.Filled.FolderZip
    FileKind.VIDEO -> Icons.Filled.Movie
    FileKind.AUDIO -> Icons.Filled.AudioFile
    FileKind.IMAGE -> Icons.Filled.Image
    FileKind.DOCUMENT -> Icons.Filled.Description
    FileKind.OTHER -> Icons.Filled.InsertDriveFile
}

@Composable
private fun OperationProgressOverlay(progress: OperationProgress) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.85f))
            .padding(16.dp),
    ) {
        Column {
            Text(progress.currentItemName, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress.currentIndex.toFloat() / progress.total.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("${progress.currentIndex} / ${progress.total}", color = Color.White, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun NameInputDialog(
    title: String,
    initialValue: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PropertiesDialog(properties: FileProperties?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Properties") },
        text = {
            if (properties == null) {
                CircularProgressIndicator()
            } else {
                Column {
                    PropertyRow("Name", properties.name)
                    PropertyRow("Path", properties.path)
                    PropertyRow("Type", if (properties.isDirectory) "Folder" else "File")
                    PropertyRow("Size", formatFileSize(properties.sizeBytes))
                    properties.itemCount?.let { PropertyRow("Items", it.toString()) }
                    PropertyRow("Modified", DateFormat.getDateTimeInstance().format(Date(properties.lastModified)))
                    PropertyRow("Readable", if (properties.readable) "Yes" else "No")
                    PropertyRow("Writable", if (properties.writable) "Yes" else "No")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun PropertyRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, modifier = Modifier.width(90.dp), color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        Text(value, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
