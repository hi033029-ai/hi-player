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

enum class FileViewTab {
    ALL_MERGED,
    ARCHIVES_ONLY,
    DOCUMENTS_ONLY,
    FOLDERS_BROWSER
}

enum class SelectAllScope {
    ARCHIVES,
    DOCUMENTS,
    FOLDERS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    fileManagerViewModel: FileManagerViewModel,
    modifier: Modifier = Modifier,
    includeHeader: Boolean = true,
    onSearchRequested: (() -> Unit)? = null,
    onOpenVideo: ((FileItem) -> Unit)? = null,
    onFilesChanged: () -> Unit = {},
    recycleBinOpenRequest: Int = 0,
    onRecycleBinRequestConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasFullStorageAccess by remember {
        mutableStateOf(Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager())
    }
    val uiMetrics = com.example.ui.theme.LocalHiUiMetrics.current
    val currentDir by fileManagerViewModel.currentDirectory.collectAsStateWithLifecycle()
    val folderFiles by fileManagerViewModel.folderFiles.collectAsStateWithLifecycle()
    val newFolderPaths by fileManagerViewModel.newFolderPaths.collectAsStateWithLifecycle()
    val allArchives by fileManagerViewModel.allArchives.collectAsStateWithLifecycle()
    val allDocuments by fileManagerViewModel.allDocuments.collectAsStateWithLifecycle()
    val usedStorage by fileManagerViewModel.storageUsedBytes.collectAsStateWithLifecycle()
    val totalStorage by fileManagerViewModel.storageTotalBytes.collectAsStateWithLifecycle()
    val searchQuery by fileManagerViewModel.searchQuery.collectAsStateWithLifecycle()
    val isLoading by fileManagerViewModel.isLoading.collectAsStateWithLifecycle()
    val isRenaming by fileManagerViewModel.isRenaming.collectAsStateWithLifecycle()

    val selectedArchive by fileManagerViewModel.selectedArchive.collectAsStateWithLifecycle()
    val activeArchiveEntries by fileManagerViewModel.activeArchiveEntries.collectAsStateWithLifecycle()
    val extractingProgress by fileManagerViewModel.extractingProgress.collectAsStateWithLifecycle()
    val selectedDocPreview by fileManagerViewModel.selectedDocumentText.collectAsStateWithLifecycle()
    val archiveAwaitingDestination by fileManagerViewModel.archiveAwaitingDestination.collectAsStateWithLifecycle()
    val extractionDestinationDirectory by fileManagerViewModel.extractionDestinationDirectory.collectAsStateWithLifecycle()
    val extractionDestinationFolders by fileManagerViewModel.extractionDestinationFolders.collectAsStateWithLifecycle()

    val isSelectionMode by fileManagerViewModel.isSelectionMode.collectAsStateWithLifecycle()
    val selectedPaths by fileManagerViewModel.selectedPaths.collectAsStateWithLifecycle()
    val detailsItem by fileManagerViewModel.detailsItem.collectAsStateWithLifecycle()
    var menuTargetItem by remember { mutableStateOf<FileItem?>(null) }
    var showFileToolsMenu by remember { mutableStateOf(false) }
    var showAdvancedFileTools by remember { mutableStateOf(false) }
    var showSelectionProperties by remember { mutableStateOf(false) }
    val deleteConfirmItems by fileManagerViewModel.deleteConfirmItems.collectAsStateWithLifecycle()
    val deleteProgress by fileManagerViewModel.deleteProgress.collectAsStateWithLifecycle()
    val recycleBinItems by fileManagerViewModel.recycleBinItems.collectAsStateWithLifecycle()
    val selectedRecycleBinIds by fileManagerViewModel.selectedRecycleBinIds.collectAsStateWithLifecycle()

    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var showRecycleBin by rememberSaveable { mutableStateOf(false) }
    var showPermanentDeleteConfirm by remember { mutableStateOf(false) }
    var selectAllScope by remember { mutableStateOf(SelectAllScope.FOLDERS) }
    var sectionRequest by remember { mutableStateOf(0) }
    var renameTargetItem by remember { mutableStateOf<FileItem?>(null) }
    var renameText by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    val activeTab = FileViewTab.ALL_MERGED
    var apkInstallCandidate by remember { mutableStateOf<FileItem?>(null) }
    var showQuickPanels by remember { mutableStateOf(true) }
    fun openFile(item: FileItem) {
        when {
            item.isVideo && onOpenVideo != null -> onOpenVideo(item)
            item.isApk -> apkInstallCandidate = item
            else -> fileManagerViewModel.openWithExternalApp(item, context)
        }
    }

    // Upper quick panels are restored whenever Files is revisited or navigation changes.
    // Scrolling must not permanently hide them; the user can dismiss them only through
    // an explicit UI action, and returning to Files always restores the default view.
    LaunchedEffect(currentDir.absolutePath) {
        showQuickPanels = true
    }
    LaunchedEffect(showRecycleBin) {
        if (showRecycleBin) fileManagerViewModel.refreshRecycleBin()
    }
    LaunchedEffect(recycleBinOpenRequest) {
        if (recycleBinOpenRequest > 0) {
            showRecycleBin = true
            onRecycleBinRequestConsumed()
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasFullStorageAccess = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
                fileManagerViewModel.refreshCurrentDirectory(silent = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Single back handler with an explicit priority order: any open dialog/sheet
    // must be dismissed (and its state cleared) before back is allowed to fall
    // through to folder navigation. Previously the archive inspector and the
    // "navigate up" handler were two independent BackHandlers; when the sheet's
    // own dismiss didn't fully clear selectedArchive before the up-navigation
    // handler ran, the archive extractor could reappear on the next screen visit.
    val canNavigateUp = currentDir.absolutePath != fileManagerViewModel.rootDir.absolutePath &&
        currentDir.parentFile?.canRead() == true
    val hasSelection = isSelectionMode || selectedPaths.isNotEmpty()
    val backHandlerEnabled = selectedArchive != null || archiveAwaitingDestination != null || apkInstallCandidate != null || renameTargetItem != null || menuTargetItem != null ||
        detailsItem != null || deleteConfirmItems != null || selectedDocPreview != null ||
        hasSelection || canNavigateUp || showRecycleBin
    BackHandler(enabled = backHandlerEnabled) {
        when {
            selectedArchive != null -> fileManagerViewModel.dismissArchiveViewer()
            archiveAwaitingDestination != null -> fileManagerViewModel.cancelExtractTo()
            apkInstallCandidate != null -> apkInstallCandidate = null
            renameTargetItem != null -> renameTargetItem = null
            menuTargetItem != null -> menuTargetItem = null
            detailsItem != null -> fileManagerViewModel.dismissDetails()
            deleteConfirmItems != null -> fileManagerViewModel.dismissDeleteConfirm()
            selectedDocPreview != null -> fileManagerViewModel.dismissDocumentPreview()
            hasSelection -> fileManagerViewModel.clearSelection()
            showRecycleBin -> {
                showRecycleBin = false
                fileManagerViewModel.clearRecycleBinSelection()
            }
            canNavigateUp -> fileManagerViewModel.navigateUp()
        }
    }

    // File-action feedback is owned by MainActivity so it also appears when
    // a move starts in Videos/Music or the user changes tabs during the move.

    val storageFraction = if (totalStorage > 0) (usedStorage.toFloat() / totalStorage.toFloat()).coerceIn(0f, 1f) else 0.38f
    val usedGb = usedStorage / (1024.0 * 1024.0 * 1024.0)
    val totalGb = totalStorage / (1024.0 * 1024.0 * 1024.0)
    val freeGb = (totalStorage - usedStorage).coerceAtLeast(0L) / (1024.0 * 1024.0 * 1024.0)

    val filteredArchives = remember(allArchives, searchQuery) {
        if (searchQuery.isBlank()) allArchives
        else allArchives.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    val filteredDocuments = remember(allDocuments, searchQuery) {
        if (searchQuery.isBlank()) allDocuments
        else allDocuments.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    val filteredFolderItems = remember(folderFiles, searchQuery) {
        if (searchQuery.isBlank()) folderFiles
        else folderFiles.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    val palette = com.example.ui.theme.LocalHiPalette.current
    val isInsideFolder = currentDir.absolutePath != fileManagerViewModel.rootDir.absolutePath

    val rootFileSections = remember(filteredArchives, filteredDocuments, filteredFolderItems) {
        RootFileSections.from(filteredArchives, filteredDocuments, filteredFolderItems)
    }
    val visibleSelectableItems = (filteredFolderItems + filteredArchives + filteredDocuments).distinctBy { it.path }
    val selectedItems = selectedPaths.mapNotNull { path -> visibleSelectableItems.find { it.path == path } }
    val selectionActions: @Composable () -> Unit = {
        com.example.ui.components.InlineSelectionToolbar(
            count = selectedPaths.size,
            onClose = { fileManagerViewModel.clearSelection() },
            onSelectAll = {
                val items = if (isInsideFolder) filteredFolderItems else rootFileSections.items(selectAllScope)
                fileManagerViewModel.selectAll(items.map { it.path }.toSet())
            },
            onClear = { fileManagerViewModel.deselectAll() },
            onProperties = {
                if (selectedItems.size == 1) selectedItems.firstOrNull()?.let { fileManagerViewModel.showDetails(it) }
                else if (selectedItems.isNotEmpty()) showSelectionProperties = true
            },
            onDelete = { fileManagerViewModel.requestDelete(selectedItems) },
            onOrganize = { showAdvancedFileTools = true },
        )
    }
    if (showAdvancedFileTools) com.example.ui.components.AdvancedFileTools(
        directory = currentDir,
        selected = selectedItems.map { it.file },
        onDismiss = { showAdvancedFileTools = false },
        onChanged = { fileManagerViewModel.clearSelection(); fileManagerViewModel.refreshCurrentDirectory(silent = true); onFilesChanged() },
    )
    val storageOverview: @Composable () -> Unit = {
        if (!isInsideFolder && showQuickPanels) {
            Card(
                border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.LocalHiPalette.current.surfaceBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .testTag("storage_overview_banner"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surfaceElevated)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.CenterStart) {
                        run {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(8.dp)).hiContainerBorder(RoundedCornerShape(8.dp))
                                            .background(palette.primary.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Storage,
                                            contentDescription = null,
                                            tint = palette.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Internal Storage",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = palette.textPrimary
                                        )
                                        Text(
                                            text = String.format(Locale.US, "Free: %.1f GB", freeGb),
                                            fontSize = 11.sp,
                                            color = palette.textSecondary
                                        )
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = String.format(Locale.US, "%.1f / %.1f GB", usedGb, totalGb),
                                        fontSize = 13.sp,
                                        color = palette.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${(storageFraction * 100).toInt()}% Used",
                                        fontSize = 11.sp,
                                        color = palette.secondary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    LinearProgressIndicator(
                        progress = { storageFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = palette.primary,
                        trackColor = palette.textPrimary.copy(alpha = 0.10f),
                        drawStopIndicator = {}
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Shortcuts expand and scroll to the separate section below storage.
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            Triple(SelectAllScope.ARCHIVES, "Archives", allArchives.size),
                            Triple(SelectAllScope.DOCUMENTS, "Docs", allDocuments.size),
                            Triple(SelectAllScope.FOLDERS, "Folders", folderFiles.count { it.isDirectory }),
                        ).forEach { (scope, label, count) ->
                            val selected = scope == selectAllScope
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                    .background(palette.primary.copy(alpha = if (selected) 0.12f else 0.04f))
                                    .clickable {
                                        fileManagerViewModel.clearSelection()
                                        selectAllScope = scope
                                        sectionRequest++
                                    }
                                    .padding(horizontal = 4.dp, vertical = 8.dp)
                                    .testTag("storage_category_${scope.name}"),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(label, color = palette.textPrimary, fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("$count", color = palette.textSecondary, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        // Local header can be disabled when the root shell owns the global header.
        if (includeHeader) {
        TopAppBar(
            modifier = Modifier.height(uiMetrics.headerHeight),
            title = {
                if (isSearchActive) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { fileManagerViewModel.setSearchQuery(it) },
                        placeholder = {
                            Text("Search documents, archives, folders...", color = palette.textSecondary, fontSize = 13.sp)
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = palette.textPrimary,
                            unfocusedTextColor = palette.textPrimary,
                            focusedBorderColor = palette.primary,
                            unfocusedBorderColor = Color(0x44FFFFFF)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("file_search_input")
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isInsideFolder) {
                            IconButton(
                                onClick = { fileManagerViewModel.navigateUp() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = palette.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        } else {
                            com.example.ui.components.HiPlayerLogoBadge(size = uiMetrics.logoSize)
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Column {
                            Text(
                                text = if (isInsideFolder) currentDir.name else "Hi Player",
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp,
                                color = palette.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            },
            actions = {
                IconButton(
                    onClick = {
                        onSearchRequested?.invoke() ?: run {
                            isSearchActive = !isSearchActive
                            if (!isSearchActive) fileManagerViewModel.setSearchQuery("")
                        }
                    },
                    modifier = Modifier.testTag("toggle_file_search")
                ) {
                    Icon(
                        imageVector = if (isSearchActive) Icons.Default.Clear else Icons.Default.Search,
                        contentDescription = "Search",
                        tint = palette.textPrimary
                    )
                }
                                if (!isInsideFolder && !showQuickPanels) {
                    IconButton(onClick = { showQuickPanels = true }, modifier = Modifier.testTag("show_file_quick_panels")) {
                        Icon(Icons.Default.Visibility, contentDescription = "Show storage and quick sections", tint = palette.textPrimary)
                    }
                }
                Box {
                    IconButton(onClick = { showFileToolsMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "File tools", tint = palette.textPrimary)
                    }
                    androidx.compose.material3.DropdownMenu(expanded = showFileToolsMenu, onDismissRequest = { showFileToolsMenu = false }) {
                        androidx.compose.material3.DropdownMenuItem(text = { Text("Refresh") }, onClick = {
                            showFileToolsMenu = false; showQuickPanels = true; fileManagerViewModel.refreshAll()
                        })
                        androidx.compose.material3.DropdownMenuItem(text = { Text("File organization") }, onClick = {
                            showFileToolsMenu = false; showAdvancedFileTools = true
                        })
                    }
                }
                IconButton(onClick = { showRecycleBin = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Recycle Bin", tint = palette.textPrimary)
                }
            },
            windowInsets = TopAppBarDefaults.windowInsets,
            colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.surface)
        )
        }


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !hasFullStorageAccess) {
            Card(
                border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.LocalHiPalette.current.surfaceBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4D6))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Storage, contentDescription = null, tint = Color(0xFF9A6700))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Allow full storage access", fontWeight = FontWeight.Bold, color = Color(0xFF5F4300), fontSize = 13.sp)
                        Text("Required to delete and manage files reliably.", color = Color(0xFF7A5A12), fontSize = 11.sp)
                    }
                    Button(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                            )
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                    ) { Text("Allow", fontSize = 12.sp) }
                }
            }
        }

        if (isRenaming) {
            Text("Renaming file…", color = palette.textSecondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = palette.primary)
        }

        // Main Content Area

        Box(modifier = Modifier.weight(1f)) {
            if (showRecycleBin) {
                RecycleBinView(
                    items = recycleBinItems,
                    selectedIds = selectedRecycleBinIds,
                    onSelectAll = { fileManagerViewModel.selectAllRecycleBin() },
                    onDeselectAll = { fileManagerViewModel.clearRecycleBinSelection() },
                    onToggle = { fileManagerViewModel.toggleRecycleBinSelection(it) },
                    onRestore = { fileManagerViewModel.restoreRecycleBinItems() },
                    onDeletePermanently = { showPermanentDeleteConfirm = true }
                )
            } else if (isLoading) {
                com.example.ui.components.ContentSkeleton(
                    label = "Loading files…", layout = com.example.ui.components.SkeletonLayout.FILES,
                )
            } else if (isInsideFolder) {
                // WHEN INSIDE A FOLDER: HIDE ARCHIVES & DOCUMENTS LAYOUTS AND SHOW PURE FOLDER CONTENTS!
                FoldersBrowserView(
                    currentDir = currentDir,
                    selectionActions = if (isSelectionMode) selectionActions else null,
                    items = filteredFolderItems,
                    onNavigateFolder = { fileManagerViewModel.navigateTo(it) },
                    onNavigateUp = { fileManagerViewModel.navigateUp() },
                    onInspectArchive = { fileManagerViewModel.inspectArchive(it) },
                    onExtractArchive = { fileManagerViewModel.beginExtractTo(it) },
                    onOpenDocument = { fileManagerViewModel.openDocument(it, context) },
                    onOpenFile = { openFile(it) },
                    isSelectionMode = isSelectionMode,
                    selectedPaths = selectedPaths,
                    onItemLongClick = { fileManagerViewModel.enterSelectionMode(it) },
                    onItemTap = { fileManagerViewModel.toggleSelection(it) },
                    onItemMenuClick = { menuTargetItem = it },
                    newFolderPaths = newFolderPaths,
                    onReorder = { fromPath, toPath -> fileManagerViewModel.moveCurrentDirectoryItem(fromPath, toPath) }
                )
            } else {
                when (activeTab) {
                    FileViewTab.ALL_MERGED -> {
                        MergedFilesView(
                            archives = filteredArchives,
                            documents = filteredDocuments,
                            folderItems = filteredFolderItems,
                            currentDir = currentDir,
                            headerContent = storageOverview,
                            rootSection = selectAllScope,
                            sectionRequest = sectionRequest,
                            onSectionSelected = { selectAllScope = it },
                            selectionHeader = selectionActions,
                            onInspectArchive = { fileManagerViewModel.inspectArchive(it) },
                            onExtractArchive = { fileManagerViewModel.beginExtractTo(it) },
                            onOpenDocument = { fileManagerViewModel.openDocument(it, context) },
                            onInstallApk = { apkInstallCandidate = it },
                            onNavigateFolder = { fileManagerViewModel.navigateTo(it) },
                            onNavigateUp = { fileManagerViewModel.navigateUp() },
                            onOpenFile = { openFile(it) },
                            // Keep panels mounted: removing one above an active
                            // Lazy list changes its viewport height mid-gesture.
                            onContentScrolled = {},
                            isSelectionMode = isSelectionMode,
                            selectedPaths = selectedPaths,
                            onItemLongClick = { fileManagerViewModel.enterSelectionMode(it) },
                            onItemTap = { fileManagerViewModel.toggleSelection(it) },
                            onItemMenuClick = { menuTargetItem = it },
                            newFolderPaths = newFolderPaths,
                            onReorder = { fromPath, toPath -> fileManagerViewModel.moveMergedItem(fromPath, toPath) }
                        )
                    }
                    FileViewTab.ARCHIVES_ONLY -> {
                        ArchivesListView(
                            archives = filteredArchives,
                            onInspectArchive = { fileManagerViewModel.inspectArchive(it) },
                            onExtractArchive = { fileManagerViewModel.beginExtractTo(it) }
                        )
                    }
                    FileViewTab.DOCUMENTS_ONLY -> {
                        DocumentsListView(
                            documents = filteredDocuments,
                            onOpenDocument = { fileManagerViewModel.openDocument(it, context) }
                        )
                    }
                    FileViewTab.FOLDERS_BROWSER -> {
                        FoldersBrowserView(
                            currentDir = currentDir,
                            selectionActions = if (isSelectionMode) selectionActions else null,
                            items = filteredFolderItems,
                            onNavigateFolder = { fileManagerViewModel.navigateTo(it) },
                            onNavigateUp = { fileManagerViewModel.navigateUp() },
                            onInspectArchive = { fileManagerViewModel.inspectArchive(it) },
                            onExtractArchive = { fileManagerViewModel.beginExtractTo(it) },
                            onOpenDocument = { fileManagerViewModel.openDocument(it, context) },
                            onOpenFile = { openFile(it) },
                            onContentScrolled = {},
                            isSelectionMode = isSelectionMode,
                            selectedPaths = selectedPaths,
                            onItemLongClick = { fileManagerViewModel.enterSelectionMode(it) },
                            onItemTap = { fileManagerViewModel.toggleSelection(it) },
                            onItemMenuClick = { menuTargetItem = it },
                            newFolderPaths = newFolderPaths,
                            onReorder = { fromPath, toPath -> fileManagerViewModel.moveCurrentDirectoryItem(fromPath, toPath) }
                        )
                    }
                }
            }
        }
    }

    if (showSelectionProperties) {
        val selectedItems = selectedPaths.mapNotNull { path ->
            visibleSelectableItems.find { it.path == path }
        }
        AlertDialog(
            modifier = androidx.compose.ui.Modifier.hiContainerBorder(androidx.compose.foundation.shape.RoundedCornerShape(28.dp)),
            onDismissRequest = { showSelectionProperties = false },
            title = { Text("${selectedItems.size} selected properties") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Files: ${selectedItems.joinToString(", ") { it.name }}", maxLines = 5, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Total size: ${selectedItems.sumOf { it.sizeBytes }.let { bytes ->
                        when {
                            bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
                            bytes >= 1024L * 1024L -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
                            bytes >= 1024L -> String.format(Locale.US, "%.2f KB", bytes / 1024.0)
                            else -> "$bytes B"
                        }
                    }}")
                }
            },
            confirmButton = { TextButton(onClick = { showSelectionProperties = false }) { Text("Close") } }
        )
    }

    // Modal Archive Inspector / Extraction Sheet
    if (selectedArchive != null) {
        ArchiveInspectorBottomSheet(
            archive = selectedArchive!!,
            entries = activeArchiveEntries,
            extractingProgress = extractingProgress,
            onDismiss = { fileManagerViewModel.dismissArchiveViewer() },
            onExtract = { fileManagerViewModel.beginExtractTo(selectedArchive!!) }
        )
    }

    if (archiveAwaitingDestination != null && extractionDestinationDirectory != null) {
        ArchiveDestinationPickerBottomSheet(
            archive = archiveAwaitingDestination!!,
            destination = extractionDestinationDirectory!!,
            folders = extractionDestinationFolders,
            atRoot = extractionDestinationDirectory!!.absolutePath == fileManagerViewModel.rootDir.absolutePath,
            onDismiss = { fileManagerViewModel.cancelExtractTo() },
            onNavigateUp = { fileManagerViewModel.navigateExtractionDestinationUp() },
            onOpenFolder = { fileManagerViewModel.chooseExtractionDestination(it) },
            onExtractHere = { fileManagerViewModel.confirmExtractTo(context) }
        )
    }

    // Per-item Actions Menu (three-dot button on each row)
    if (menuTargetItem != null) {
        val item = menuTargetItem!!
        AlertDialog(
            modifier = androidx.compose.ui.Modifier.hiContainerBorder(androidx.compose.foundation.shape.RoundedCornerShape(28.dp)),
            onDismissRequest = { menuTargetItem = null },
            title = {
                Text(item.name, color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            text = {
                Column {
                    MenuActionRow(icon = Icons.Default.Info, label = "Details") {
                        menuTargetItem = null
                        fileManagerViewModel.showDetails(item)
                    }
                    if (item.isApk) {
                        MenuActionRow(icon = Icons.Default.Android, label = "Install APK") {
                            menuTargetItem = null
                            apkInstallCandidate = item
                        }
                    } else if (item.isArchive) {
                        MenuActionRow(icon = Icons.Default.Unarchive, label = "Extract Here") {
                            menuTargetItem = null
                            fileManagerViewModel.extractArchive(item, context)
                        }
                        MenuActionRow(icon = Icons.Default.Download, label = "Extract To…") {
                            menuTargetItem = null
                            fileManagerViewModel.beginExtractTo(item)
                        }
                    }
                    MenuActionRow(icon = Icons.Default.Edit, label = "Rename") {
                        menuTargetItem = null
                        renameTargetItem = item
                        renameText = TextFieldValue(
                            text = item.name,
                            selection = TextRange(item.name.length)
                        )
                    }
                    MenuActionRow(icon = Icons.Default.Delete, label = "Delete", tint = Color(0xFFEF4444)) {
                        menuTargetItem = null
                        fileManagerViewModel.requestDelete(listOf(item))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { menuTargetItem = null }) {
                    Text("Cancel", color = palette.textSecondary)
                }
            },
            containerColor = palette.surfaceElevated
        )
    }

    if (renameTargetItem != null) {
        val item = renameTargetItem!!
        AlertDialog(
            modifier = androidx.compose.ui.Modifier.hiContainerBorder(androidx.compose.foundation.shape.RoundedCornerShape(28.dp)),
            onDismissRequest = { renameTargetItem = null },
            title = {
                Text(
                    text = "Rename \"${item.name}\"",
                    color = palette.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("New name") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rename_file_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary,
                        focusedBorderColor = palette.primary,
                        unfocusedBorderColor = palette.textSecondary
                    )
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isRenaming,
                    onClick = {
                        fileManagerViewModel.renameItem(item, renameText.text)
                        renameTargetItem = null
                    }
                ) {
                    Text("Rename", color = palette.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTargetItem = null }) {
                    Text("Cancel", color = palette.textSecondary)
                }
            },
            containerColor = palette.surfaceElevated
        )
    }

    // APK installation confirmation. Installation is never triggered directly from a file row.
    if (apkInstallCandidate != null) {
        val apk = apkInstallCandidate!!
        AlertDialog(
            modifier = androidx.compose.ui.Modifier.hiContainerBorder(androidx.compose.foundation.shape.RoundedCornerShape(28.dp)),
            onDismissRequest = { apkInstallCandidate = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Android, contentDescription = null, tint = Color(0xFF34D399))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Install APK?", color = palette.textPrimary, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    "Install \"${apk.name}\" using Android's package installer? Only install apps from sources you trust.",
                    color = palette.textSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        apkInstallCandidate = null
                        fileManagerViewModel.installApk(apk, context)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF34D399),
                        contentColor = Color.Black
                    )
                ) { Text("Install") }
            },
            dismissButton = {
                TextButton(onClick = { apkInstallCandidate = null }) {
                    Text("Cancel", color = palette.textSecondary)
                }
            },
            containerColor = palette.surfaceElevated
        )
    }

    // File / Folder Details Dialog (long-press -> Details)
    if (detailsItem != null) {
        val item = detailsItem!!
        val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.US) }
        AlertDialog(
            modifier = androidx.compose.ui.Modifier.hiContainerBorder(androidx.compose.foundation.shape.RoundedCornerShape(28.dp)),
            onDismissRequest = { fileManagerViewModel.dismissDetails() },
            title = {
                Text(item.name, color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            },
            text = {
                Column {
                    DetailRow(
                        "Type",
                        when {
                            item.isDirectory -> "Folder"
                            item.isApk -> "Android package (APK)"
                            else -> item.fileType.name.lowercase(Locale.ROOT).replaceFirstChar { it.uppercase() }
                        }
                    )
                    DetailRow("Location", item.path)
                    if (item.isDirectory) {
                        DetailRow("Contains", "${item.childCount} item${if (item.childCount == 1) "" else "s"}")
                    } else {
                        DetailRow("Size", item.formattedSize)
                    }
                    if (item.lastModified > 0) {
                        DetailRow("Modified", dateFormat.format(Date(item.lastModified)))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { fileManagerViewModel.dismissDetails() }) {
                    Text("Close", color = palette.primary, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = palette.surfaceElevated
        )
    }

    deleteProgress?.let { progress ->
        val remaining = (progress.total - progress.completed - progress.failed).coerceAtLeast(0)
        val progressFraction = if (progress.totalBytes > 0L) {
            (progress.processedBytes.toFloat() / progress.totalBytes.toFloat()).coerceIn(0f, 1f)
        } else if (progress.total > 0) {
            (progress.completed + progress.failed).toFloat() / progress.total
        } else 0f
        AlertDialog(
            modifier = androidx.compose.ui.Modifier.hiContainerBorder(androidx.compose.foundation.shape.RoundedCornerShape(28.dp)),
            onDismissRequest = { fileManagerViewModel.cancelDelete() },
            title = { Text("Deleting files", color = palette.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "${progress.completed + progress.failed} of ${progress.total} processed",
                        color = palette.textPrimary
                    )
                    Text(
                        text = "Remaining: $remaining",
                        color = palette.textSecondary,
                        fontSize = 13.sp
                    )
                    if (progress.totalBytes > 0L) {
                        Text(
                            text = "${formatRecycleBinSize(progress.processedBytes)} of ${formatRecycleBinSize(progress.totalBytes)}",
                            color = palette.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                    progress.currentItemName?.let { name ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = name,
                            color = palette.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier.fillMaxWidth(),
                        color = palette.primary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { fileManagerViewModel.cancelDelete() }) {
                    Text("Cancel", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            containerColor = palette.surfaceElevated
        )
    }

    // Delete Confirmation Dialog
    if (deleteConfirmItems != null) {
        val items = deleteConfirmItems!!
        AlertDialog(
            modifier = androidx.compose.ui.Modifier.hiContainerBorder(androidx.compose.foundation.shape.RoundedCornerShape(28.dp)),
            onDismissRequest = { fileManagerViewModel.dismissDeleteConfirm() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete ${items.size} item${if (items.size == 1) "" else "s"}?", color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    text = if (items.size == 1) {
                        "\"${items.first().name}\" will be moved to Recycle Bin. You can restore it later."
                    } else {
                        val folderCount = items.count { it.isDirectory }
                        val folderWarning = if (folderCount > 0) {
                            " This includes $folderCount folder${if (folderCount == 1) "" else "s"} and all of its contents."
                        } else ""
                        "These ${items.size} items will be moved to Recycle Bin and can be restored later.$folderWarning"
                    },
                    color = palette.textSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { fileManagerViewModel.confirmDelete() }) {
                    Text("Move to Recycle Bin", color = palette.primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { fileManagerViewModel.dismissDeleteConfirm() }) {
                    Text("Cancel", color = palette.textSecondary)
                }
            },
            containerColor = palette.surfaceElevated
        )
    }

    if (showPermanentDeleteConfirm) {
        AlertDialog(
            modifier = androidx.compose.ui.Modifier.hiContainerBorder(androidx.compose.foundation.shape.RoundedCornerShape(28.dp)),
            onDismissRequest = { showPermanentDeleteConfirm = false },
            title = { Text("Delete permanently?", color = palette.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "The selected Recycle Bin items will be erased permanently and cannot be restored.",
                    color = palette.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showPermanentDeleteConfirm = false
                    fileManagerViewModel.permanentlyDeleteRecycleBinItems()
                }) { Text("Delete permanently", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showPermanentDeleteConfirm = false }) {
                    Text("Cancel", color = palette.textSecondary)
                }
            },
            containerColor = palette.surfaceElevated
        )
    }

    // Document In-App Text Preview Dialog
    if (selectedDocPreview != null) {
        val (docTitle, docContent) = selectedDocPreview!!
        AlertDialog(
            modifier = androidx.compose.ui.Modifier.hiContainerBorder(androidx.compose.foundation.shape.RoundedCornerShape(28.dp)),
            onDismissRequest = { fileManagerViewModel.dismissDocumentPreview() },
            title = {
                Text(
                    text = docTitle,
                    color = palette.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = docContent.ifEmpty { "Empty document content" },
                        color = palette.textPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { fileManagerViewModel.dismissDocumentPreview() },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primary, contentColor = if (palette.isDark) Color.Black else Color.White)
                ) {
                    Text("Close")
                }
            },
            containerColor = palette.surfaceElevated
        )
    }
}
