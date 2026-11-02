package com.example.ui.screens

import com.example.ui.theme.hiContainerBorder

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import com.example.model.FileType
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.model.ArchiveEntryInfo
import com.example.model.FileItem
import com.example.ui.components.FilePreviewThumbnail
import com.example.ui.theme.HiAccentAmber
import com.example.ui.theme.HiCinemaBlack
import com.example.ui.theme.HiPrimaryCyan
import com.example.ui.theme.HiSurfaceDark
import com.example.ui.theme.HiSurfaceElevated
import com.example.ui.theme.HiTextPrimary
import com.example.ui.theme.HiTextSecondary
import com.example.ui.components.reorderableDrag
import com.example.viewmodel.FileManagerViewModel
import com.example.ui.components.HiPlayerHeader
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// -------------------------------------------------------------
// ROOT VIEW: Scrollable storage overview and three independent file sections.
// -------------------------------------------------------------
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MergedFilesView(
    archives: List<FileItem>,
    documents: List<FileItem>,
    folderItems: List<FileItem>,
    currentDir: File,
    onInspectArchive: (FileItem) -> Unit,
    onExtractArchive: (FileItem) -> Unit,
    onOpenDocument: (FileItem) -> Unit,
    onInstallApk: (FileItem) -> Unit = {},
    onNavigateFolder: (FileItem) -> Unit,
    onNavigateUp: () -> Unit,
    onOpenFile: (FileItem) -> Unit = {},
    isSelectionMode: Boolean = false,
    selectedPaths: Set<String> = emptySet(),
    onItemLongClick: (FileItem) -> Unit = {},
    onItemTap: (FileItem) -> Unit = {},
    onItemMenuClick: (FileItem) -> Unit = {},
    newFolderPaths: Set<String> = emptySet(),
    onContentScrolled: (Boolean) -> Unit = {},
    headerContent: @Composable () -> Unit = {},
    rootSection: SelectAllScope = SelectAllScope.FOLDERS,
    onSectionSelected: (SelectAllScope) -> Unit = {},
    selectionHeader: @Composable () -> Unit = {},
    onReorder: (String, String) -> Unit = { _, _ -> }
) {
    val palette = com.example.ui.theme.LocalHiPalette.current
    val sections = remember(archives, documents, folderItems) { RootFileSections.from(archives, documents, folderItems) }
    var archivesOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var documentsOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var foldersOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(true) }
    val mergedListState = rememberLazyListState()
    val reorderThresholdPx = with(LocalDensity.current) { 96.dp.toPx() }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        state = mergedListState,
        modifier = Modifier.fillMaxSize().testTag("files_root_list"),
    ) {
        item(key = "storage_overview", contentType = "storage_overview") { headerContent() }
        listOf(SelectAllScope.ARCHIVES, SelectAllScope.DOCUMENTS, SelectAllScope.FOLDERS).forEach { scope ->
            val displayedItems = sections.items(scope)
            val expanded = when (scope) {
                SelectAllScope.ARCHIVES -> archivesOpen
                SelectAllScope.DOCUMENTS -> documentsOpen
                SelectAllScope.FOLDERS -> foldersOpen
            }
            stickyHeader(key = "file_section_${scope.name}", contentType = "file_section_header") {
                val title = when (scope) {
                    SelectAllScope.ARCHIVES -> "Archives"
                    SelectAllScope.DOCUMENTS -> "Documents"
                    SelectAllScope.FOLDERS -> "Folders & files"
                }
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("file_section_${scope.name}").clickable(enabled = !isSelectionMode) {
                        onSectionSelected(scope)
                        when (scope) {
                            SelectAllScope.ARCHIVES -> archivesOpen = !archivesOpen
                            SelectAllScope.DOCUMENTS -> documentsOpen = !documentsOpen
                            SelectAllScope.FOLDERS -> foldersOpen = !foldersOpen
                        }
                    },
                    colors = CardDefaults.cardColors(containerColor = palette.surfaceElevated),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.CenterStart) {
                        if (isSelectionMode && rootSection == scope) selectionHeader() else {
                            Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(when (scope) {
                                    SelectAllScope.ARCHIVES -> Icons.Default.FolderZip
                                    SelectAllScope.DOCUMENTS -> Icons.Default.Description
                                    SelectAllScope.FOLDERS -> Icons.Default.Folder
                                }, null, tint = palette.primary, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                                Text("${displayedItems.size}", color = palette.textSecondary, fontSize = 12.sp)
                                Spacer(Modifier.width(8.dp))
                                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    if (expanded) "Collapse $title" else "Expand $title", tint = palette.textSecondary)
                            }
                        }
                    }
                }
            }
            if (expanded) {
                if (displayedItems.isEmpty()) item(key = "empty_${scope.name}") {
                    Text("No ${when (scope) {
                        SelectAllScope.ARCHIVES -> "archives"
                        SelectAllScope.DOCUMENTS -> "documents"
                        SelectAllScope.FOLDERS -> "files"
                    }} found", color = palette.textSecondary, modifier = Modifier.padding(16.dp))
                }
                itemsIndexed(displayedItems, key = { _, item -> "${scope.name}:${item.path}" }) { index, item ->
                    val selected = selectedPaths.contains(item.path)
                    val dragModifier = Modifier.reorderableDrag(index, displayedItems.size, reorderThresholdPx,
                        { from, to -> onReorder(displayedItems[from].path, displayedItems[to].path) })
                    when {
                        item.isDirectory -> FolderItemCard(
                            folder = item,
                            onClick = { if (isSelectionMode) onItemTap(item) else onNavigateFolder(item) },
                            onLongClick = { onSectionSelected(scope); onItemLongClick(item) },
                            isSelected = selected, showSelectionControl = isSelectionMode,
                            isNew = newFolderPaths.contains(item.path),
                            onMenuClick = { onSectionSelected(scope); onItemMenuClick(item) }, modifier = dragModifier,
                        )
                        item.isApk -> GenericFileItemCard(
                            item = item,
                            onClick = { if (isSelectionMode) onItemTap(item) else onInstallApk(item) },
                            onLongClick = { onSectionSelected(scope); onItemLongClick(item) },
                            isSelected = selected, showSelectionControl = isSelectionMode,
                            onMenuClick = { onSectionSelected(scope); onItemMenuClick(item) }, modifier = dragModifier,
                        )
                        item.isArchive -> ArchiveItemCard(
                            archive = item,
                            onInspect = { if (isSelectionMode) onItemTap(item) else onInspectArchive(item) },
                            onExtract = { onExtractArchive(item) }, onLongClick = { onSectionSelected(scope); onItemLongClick(item) },
                            isSelected = selected, showSelectionControl = isSelectionMode,
                            onMenuClick = { onSectionSelected(scope); onItemMenuClick(item) }, modifier = dragModifier,
                        )
                        item.isDocument -> DocumentItemCard(
                            doc = item,
                            onOpen = { if (isSelectionMode) onItemTap(item) else onOpenDocument(item) },
                            onLongClick = { onSectionSelected(scope); onItemLongClick(item) },
                            isSelected = selected, showSelectionControl = isSelectionMode,
                            onMenuClick = { onSectionSelected(scope); onItemMenuClick(item) }, modifier = dragModifier,
                        )
                        else -> GenericFileItemCard(
                            item = item,
                            onClick = { if (isSelectionMode) onItemTap(item) else onOpenFile(item) },
                            onLongClick = { onSectionSelected(scope); onItemLongClick(item) },
                            isSelected = selected, showSelectionControl = isSelectionMode,
                            onMenuClick = { onSectionSelected(scope); onItemMenuClick(item) }, modifier = dragModifier,
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// DEDICATED ARCHIVES LIST VIEW
// -------------------------------------------------------------
@Composable
fun ArchivesListView(
    archives: List<FileItem>,
    onInspectArchive: (FileItem) -> Unit,
    onExtractArchive: (FileItem) -> Unit
) {
    val palette = com.example.ui.theme.LocalHiPalette.current
    if (archives.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.FolderZip,
                    contentDescription = null,
                    tint = palette.textSecondary.copy(alpha = 0.4f),
                    modifier = Modifier.size(54.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text("No Archive Files Found", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Supports .zip, .rar, .7z, .tar, .gz, .apk", color = palette.textSecondary, fontSize = 12.sp)
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(archives, key = { it.path }) { archive ->
                ArchiveItemCard(
                    archive = archive,
                    onInspect = { onInspectArchive(archive) },
                    onExtract = { onExtractArchive(archive) }
                )
            }
        }
    }
}

// -------------------------------------------------------------
// DEDICATED DOCUMENTS LIST VIEW
// -------------------------------------------------------------
@Composable
fun DocumentsListView(
    documents: List<FileItem>,
    onOpenDocument: (FileItem) -> Unit
) {
    val palette = com.example.ui.theme.LocalHiPalette.current
    if (documents.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = palette.textSecondary.copy(alpha = 0.4f),
                    modifier = Modifier.size(54.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text("No Documents Found", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Supports PDF, Word, TXT, Subtitles, Spreadsheets", color = palette.textSecondary, fontSize = 12.sp)
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(documents, key = { it.path }) { doc ->
                DocumentItemCard(
                    doc = doc,
                    onOpen = { onOpenDocument(doc) }
                )
            }
        }
    }
}

// -------------------------------------------------------------
// DEDICATED FOLDERS BROWSER VIEW
// -------------------------------------------------------------
@Composable
fun FoldersBrowserView(
    currentDir: File,
    items: List<FileItem>,
    onNavigateFolder: (FileItem) -> Unit,
    onNavigateUp: () -> Unit,
    onInspectArchive: (FileItem) -> Unit,
    onExtractArchive: (FileItem) -> Unit,
    onOpenDocument: (FileItem) -> Unit,
    onOpenFile: (FileItem) -> Unit = {},
    isSelectionMode: Boolean = false,
    selectedPaths: Set<String> = emptySet(),
    onItemLongClick: (FileItem) -> Unit = {},
    onItemTap: (FileItem) -> Unit = {},
        onItemMenuClick: (FileItem) -> Unit = {},
    newFolderPaths: Set<String> = emptySet(),
    onContentScrolled: (Boolean) -> Unit = {},
    selectionActions: (@Composable () -> Unit)? = null,
    onReorder: (String, String) -> Unit = { _, _ -> }
) {
    val palette = com.example.ui.theme.LocalHiPalette.current
    val reorderThresholdPx = with(LocalDensity.current) { 96.dp.toPx() }
    val folderListState = rememberLazyListState()
    LaunchedEffect(folderListState.firstVisibleItemIndex, folderListState.firstVisibleItemScrollOffset) {
        val atTop = folderListState.firstVisibleItemIndex == 0 && folderListState.firstVisibleItemScrollOffset <= 48
        onContentScrolled(atTop)
    }
    Column(modifier = Modifier.fillMaxSize()) {
        FileFolderHeader(currentDir, onNavigateUp, selectionActions)

        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Directory is empty", color = palette.textSecondary, fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                state = folderListState,
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(items, key = { _, it -> it.path }) { index, item ->
                    val dragModifier = Modifier.reorderableDrag(
                        itemIndex = index,
                        itemCount = items.size,
                        thresholdPx = reorderThresholdPx,
                        onMove = { from, to -> onReorder(items[from].path, items[to].path) }
                    )
                    if (item.isDirectory) {
                        FolderItemCard(
                            folder = item,
                            onClick = { if (isSelectionMode) onItemTap(item) else onNavigateFolder(item) },
                            onLongClick = { onItemLongClick(item) },
                            isSelected = selectedPaths.contains(item.path),
                            showSelectionControl = isSelectionMode,
                            isNew = newFolderPaths.contains(item.path),
                            onMenuClick = { onItemMenuClick(item) },
                            modifier = dragModifier
                        )
                    } else if (item.isApk) {
                        GenericFileItemCard(
                            item = item,
                            onClick = { if (isSelectionMode) onItemTap(item) else onOpenFile(item) },
                            onLongClick = { onItemLongClick(item) },
                            isSelected = selectedPaths.contains(item.path),
                            showSelectionControl = isSelectionMode,
                            onMenuClick = { onItemMenuClick(item) },
                            modifier = dragModifier
                        )
                    } else if (item.isArchive) {
                        ArchiveItemCard(
                            archive = item,
                            onInspect = { if (isSelectionMode) onItemTap(item) else onInspectArchive(item) },
                            onExtract = { onExtractArchive(item) },
                            onLongClick = { onItemLongClick(item) },
                            isSelected = selectedPaths.contains(item.path),
                            showSelectionControl = isSelectionMode,
                            onMenuClick = { onItemMenuClick(item) },
                            modifier = dragModifier
                        )
                    } else if (item.isDocument) {
                        DocumentItemCard(
                            doc = item,
                            onOpen = { if (isSelectionMode) onItemTap(item) else onOpenDocument(item) },
                            onLongClick = { onItemLongClick(item) },
                            isSelected = selectedPaths.contains(item.path),
                            showSelectionControl = isSelectionMode,
                            onMenuClick = { onItemMenuClick(item) },
                            modifier = dragModifier
                        )
                    } else {
                        GenericFileItemCard(
                            item = item,
                            onClick = { if (isSelectionMode) onItemTap(item) else onOpenFile(item) },
                            onLongClick = { onItemLongClick(item) },
                            isSelected = selectedPaths.contains(item.path),
                            showSelectionControl = isSelectionMode,
                            onMenuClick = { onItemMenuClick(item) },
                            modifier = dragModifier
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// UI COMPONENTS & CARDS
// -------------------------------------------------------------
@Composable
fun SectionHeader(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    isCollapsible: Boolean = false,
    isExpanded: Boolean = true,
    itemCount: Int = 0,
    onToggle: (() -> Unit)? = null
) {
    val palette = com.example.ui.theme.LocalHiPalette.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isCollapsible && onToggle != null) {
                    Modifier.clickable(onClick = onToggle)
                } else Modifier
            ),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surfaceElevated),
        border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.LocalHiPalette.current.surfaceBorder),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp)).hiContainerBorder(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = palette.textPrimary
                    )
                    if (isCollapsible) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp)).hiContainerBorder(RoundedCornerShape(6.dp))
                                .background(accentColor.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "$itemCount",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = accentColor
                            )
                        }
                    }
                }
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = palette.textSecondary
                )
            }

            if (isCollapsible && onToggle != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp)).hiContainerBorder(RoundedCornerShape(6.dp))
                        .background(if (isExpanded) accentColor.copy(alpha = 0.2f) else palette.surfaceBorder)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isExpanded) "Hide" else "Show ($itemCount)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isExpanded) accentColor else palette.textSecondary
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = if (isExpanded) accentColor else palette.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
