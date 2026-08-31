package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import com.example.model.FileType
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.model.ArchiveEntryInfo
import com.example.model.FileItem
import com.example.ui.theme.HiAccentAmber
import com.example.ui.theme.HiCinemaBlack
import com.example.ui.theme.HiPrimaryCyan
import com.example.ui.theme.HiSurfaceDark
import com.example.ui.theme.HiSurfaceElevated
import com.example.ui.theme.HiTextPrimary
import com.example.ui.theme.HiTextSecondary
import com.example.viewmodel.FileManagerViewModel
import com.example.ui.components.HiPlayerHeader
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    fileManagerViewModel: FileManagerViewModel,
    modifier: Modifier = Modifier,
    includeHeader: Boolean = true,
    onSearchRequested: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val uiMetrics = com.example.ui.theme.LocalHiUiMetrics.current
    val currentDir by fileManagerViewModel.currentDirectory.collectAsState()
    val folderFiles by fileManagerViewModel.folderFiles.collectAsState()
    val allArchives by fileManagerViewModel.allArchives.collectAsState()
    val allDocuments by fileManagerViewModel.allDocuments.collectAsState()
    val usedStorage by fileManagerViewModel.storageUsedBytes.collectAsState()
    val totalStorage by fileManagerViewModel.storageTotalBytes.collectAsState()
    val searchQuery by fileManagerViewModel.searchQuery.collectAsState()
    val isLoading by fileManagerViewModel.isLoading.collectAsState()

    val selectedArchive by fileManagerViewModel.selectedArchive.collectAsState()
    val activeArchiveEntries by fileManagerViewModel.activeArchiveEntries.collectAsState()
    val extractingProgress by fileManagerViewModel.extractingProgress.collectAsState()
    val selectedDocPreview by fileManagerViewModel.selectedDocumentText.collectAsState()
    val archiveAwaitingDestination by fileManagerViewModel.archiveAwaitingDestination.collectAsState()
    val extractionDestinationDirectory by fileManagerViewModel.extractionDestinationDirectory.collectAsState()
    val extractionDestinationFolders by fileManagerViewModel.extractionDestinationFolders.collectAsState()

    val isSelectionMode by fileManagerViewModel.isSelectionMode.collectAsState()
    val selectedPaths by fileManagerViewModel.selectedPaths.collectAsState()
    val detailsItem by fileManagerViewModel.detailsItem.collectAsState()
    var menuTargetItem by remember { mutableStateOf<FileItem?>(null) }
    val deleteConfirmItems by fileManagerViewModel.deleteConfirmItems.collectAsState()
    val deleteResultMessage by fileManagerViewModel.deleteResultMessage.collectAsState()

    var isSearchActive by remember { mutableStateOf(false) }
    val savedTab = remember { runCatching { FileViewTab.valueOf(fileManagerViewModel.preferredFileTab) }.getOrDefault(FileViewTab.FOLDERS_BROWSER) }
    var activeTab by remember { mutableStateOf(savedTab) }
    fun selectTab(tab: FileViewTab) {
        activeTab = tab
        fileManagerViewModel.setPreferredFileTab(tab.name)
    }
    var apkInstallCandidate by remember { mutableStateOf<FileItem?>(null) }
    var showQuickPanels by remember { mutableStateOf(true) }

    // Upper quick panels are restored whenever Files is revisited or navigation changes.
    // Scrolling must not permanently hide them; the user can dismiss them only through
    // an explicit UI action, and returning to Files always restores the default view.
    LaunchedEffect(activeTab, currentDir.absolutePath) {
        showQuickPanels = true
    }

    // Single back handler with an explicit priority order: any open dialog/sheet
    // must be dismissed (and its state cleared) before back is allowed to fall
    // through to folder navigation. Previously the archive inspector and the
    // "navigate up" handler were two independent BackHandlers; when the sheet's
    // own dismiss didn't fully clear selectedArchive before the up-navigation
    // handler ran, the archive extractor could reappear on the next screen visit.
    val canNavigateUp = currentDir.absolutePath != fileManagerViewModel.rootDir.absolutePath &&
        currentDir.parentFile?.canRead() == true
    val backHandlerEnabled = selectedArchive != null || archiveAwaitingDestination != null || apkInstallCandidate != null || menuTargetItem != null ||
        detailsItem != null || deleteConfirmItems != null || selectedDocPreview != null ||
        isSelectionMode || canNavigateUp
    BackHandler(enabled = backHandlerEnabled) {
        when {
            selectedArchive != null -> fileManagerViewModel.dismissArchiveViewer()
            archiveAwaitingDestination != null -> fileManagerViewModel.cancelExtractTo()
            apkInstallCandidate != null -> apkInstallCandidate = null
            menuTargetItem != null -> menuTargetItem = null
            detailsItem != null -> fileManagerViewModel.dismissDetails()
            deleteConfirmItems != null -> fileManagerViewModel.dismissDeleteConfirm()
            selectedDocPreview != null -> fileManagerViewModel.dismissDocumentPreview()
            isSelectionMode -> fileManagerViewModel.clearSelection()
            canNavigateUp -> fileManagerViewModel.navigateUp()
        }
    }

    // Toast-style delete result feedback
    LaunchedEffect(deleteResultMessage) {
        val msg = deleteResultMessage
        if (msg != null) {
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            fileManagerViewModel.dismissDeleteResult()
        }
    }

    // "All Files Access" permission state - without this, folder listings
    // return empty for anything outside the app's own scoped storage on
    // Android 11+, which is why archives/documents could silently show as
    // 0 even when real files exist on the device.
    var hasAllFilesAccess by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true
        )
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true
                if (granted != hasAllFilesAccess) {
                    hasAllFilesAccess = granted
                    if (granted) fileManagerViewModel.refreshAll()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        // Local header can be disabled when the root shell owns the global header.
        if (includeHeader && isSelectionMode) {
            TopAppBar(
                modifier = Modifier.height(uiMetrics.headerHeight),
                title = {
                    Text(
                        text = "${selectedPaths.size} selected",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = palette.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { fileManagerViewModel.clearSelection() }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel selection", tint = palette.textPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { fileManagerViewModel.selectAllInCurrentFolder() }) {
                        Icon(Icons.Default.SelectAll, contentDescription = "Select all", tint = palette.textPrimary)
                    }
                    if (selectedPaths.size == 1) {
                        IconButton(onClick = {
                            val item = folderFiles.find { it.path == selectedPaths.first() }
                                ?: allArchives.find { it.path == selectedPaths.first() }
                                ?: allDocuments.find { it.path == selectedPaths.first() }
                            if (item != null) fileManagerViewModel.showDetails(item)
                        }) {
                            Icon(Icons.Default.Info, contentDescription = "Details", tint = palette.textPrimary)
                        }
                    }
                    IconButton(onClick = {
                        val items = selectedPaths.mapNotNull { path ->
                            folderFiles.find { it.path == path }
                                ?: allArchives.find { it.path == path }
                                ?: allDocuments.find { it.path == path }
                        }
                        fileManagerViewModel.requestDelete(items)
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444))
                    }
                },
                windowInsets = TopAppBarDefaults.windowInsets,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.surface)
            )
        } else if (includeHeader) {
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
                IconButton(
                    onClick = {
                        showQuickPanels = true
                        fileManagerViewModel.refreshAll()
                    },
                    modifier = Modifier.testTag("refresh_files_button")
                ) {

                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = palette.textPrimary
                    )
                }
            },
            windowInsets = TopAppBarDefaults.windowInsets,
            colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.surface)
        )
        }

        // Permission banner - shown until "All Files Access" is granted,
        // since without it folder scanning silently returns nothing for
        // most real-world directories.
        if (!hasAllFilesAccess) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFB45309))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Full storage access needed",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF78350F)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Without this, folders can appear empty even when they contain files. Grant access to see archives, documents, and everything else.",
                        fontSize = 12.5.sp,
                        color = Color(0xFF78350F)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                try {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB45309))
                    ) {
                        Text("Grant Access", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Quick sections are hidden after the file list is scrolled to preserve space.
        if (!isInsideFolder && showQuickPanels) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("storage_overview_banner"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surfaceElevated)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
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

                    Spacer(modifier = Modifier.height(10.dp))

                    LinearProgressIndicator(
                        progress = { storageFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = palette.primary,
                        trackColor = Color(0x33FFFFFF)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Storage summary badges
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(palette.secondary.copy(alpha = 0.15f))
                                .clickable { selectTab(FileViewTab.ARCHIVES_ONLY) }
                                .padding(vertical = 6.dp, horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.FolderZip,
                                    contentDescription = null,
                                    tint = palette.secondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${allArchives.size} Archives",
                                    fontSize = 11.sp,
                                    color = palette.secondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x223B82F6))
                                .clickable { selectTab(FileViewTab.DOCUMENTS_ONLY) }
                                .padding(vertical = 6.dp, horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = Color(0xFF60A5FA),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${allDocuments.size} Documents",
                                    fontSize = 11.sp,
                                    color = Color(0xFF60A5FA),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x2210B981))
                                .clickable { selectTab(FileViewTab.FOLDERS_BROWSER) }
                                .padding(vertical = 6.dp, horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = Color(0xFF34D399),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${folderFiles.count { it.isDirectory }} Folders",
                                    fontSize = 11.sp,
                                    color = Color(0xFF34D399),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Filter chips bar
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = activeTab == FileViewTab.ALL_MERGED,
                        onClick = { selectTab(FileViewTab.ALL_MERGED) },
                        label = { Text("Merged View", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = palette.primary,
                            selectedLabelColor = if (palette.isDark) Color.Black else Color.White,
                            containerColor = palette.surfaceElevated,
                            labelColor = palette.textSecondary
                        ),
                        border = null
                    )
                }
                item {
                    FilterChip(
                        selected = activeTab == FileViewTab.ARCHIVES_ONLY,
                        onClick = { selectTab(FileViewTab.ARCHIVES_ONLY) },
                        label = { Text("Archives (${filteredArchives.size})", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = palette.secondary,
                            selectedLabelColor = Color.Black,
                            containerColor = palette.surfaceElevated,
                            labelColor = palette.textSecondary
                        ),
                        border = null
                    )
                }
                item {
                    FilterChip(
                        selected = activeTab == FileViewTab.DOCUMENTS_ONLY,
                        onClick = { selectTab(FileViewTab.DOCUMENTS_ONLY) },
                        label = { Text("Documents (${filteredDocuments.size})", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF3B82F6),
                            selectedLabelColor = Color.White,
                            containerColor = palette.surfaceElevated,
                            labelColor = palette.textSecondary
                        ),
                        border = null
                    )
                }
                item {
                    FilterChip(
                        selected = activeTab == FileViewTab.FOLDERS_BROWSER,
                        onClick = { selectTab(FileViewTab.FOLDERS_BROWSER) },
                        label = { Text("Android Folders", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF10B981),
                            selectedLabelColor = Color.Black,
                            containerColor = palette.surfaceElevated,
                            labelColor = palette.textSecondary
                        ),
                        border = null
                    )
                }
                        }
        }
        // Main Content Area

        Box(modifier = Modifier.weight(1f)) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = palette.primary)
                }
            } else if (isInsideFolder) {
                // WHEN INSIDE A FOLDER: HIDE ARCHIVES & DOCUMENTS LAYOUTS AND SHOW PURE FOLDER CONTENTS!
                FoldersBrowserView(
                    currentDir = currentDir,
                    items = filteredFolderItems,
                    onNavigateFolder = { fileManagerViewModel.navigateTo(it) },
                    onNavigateUp = { fileManagerViewModel.navigateUp() },
                    onInspectArchive = { fileManagerViewModel.inspectArchive(it) },
                    onExtractArchive = { fileManagerViewModel.beginExtractTo(it) },
                    onOpenDocument = { fileManagerViewModel.openDocument(it, context) },
                    onOpenFile = {
                        if (it.isApk) apkInstallCandidate = it
                        else fileManagerViewModel.openWithExternalApp(it, context)
                    },
                    isSelectionMode = isSelectionMode,
                    selectedPaths = selectedPaths,
                    onItemLongClick = { fileManagerViewModel.enterSelectionMode(it) },
                    onItemTap = { fileManagerViewModel.toggleSelection(it) },
                    onItemMenuClick = { menuTargetItem = it }
                )
            } else {
                when (activeTab) {
                    FileViewTab.ALL_MERGED -> {
                        MergedFilesView(
                            archives = filteredArchives,
                            documents = filteredDocuments,
                            folderItems = filteredFolderItems,
                            currentDir = currentDir,
                            onInspectArchive = { fileManagerViewModel.inspectArchive(it) },
                            onExtractArchive = { fileManagerViewModel.beginExtractTo(it) },
                            onOpenDocument = { fileManagerViewModel.openDocument(it, context) },
                            onInstallApk = { apkInstallCandidate = it },
                            onNavigateFolder = { fileManagerViewModel.navigateTo(it) },
                            onNavigateUp = { fileManagerViewModel.navigateUp() },
                            onOpenFile = {
                        if (it.isApk) apkInstallCandidate = it
                        else fileManagerViewModel.openWithExternalApp(it, context)
                    },
                            onContentScrolled = { /* Do not hide quick panels permanently on scroll. */ },
                            isSelectionMode = isSelectionMode,
                            selectedPaths = selectedPaths,
                            onItemLongClick = { fileManagerViewModel.enterSelectionMode(it) },
                            onItemTap = { fileManagerViewModel.toggleSelection(it) },
                            onItemMenuClick = { menuTargetItem = it }
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
                            items = filteredFolderItems,
                            onNavigateFolder = { fileManagerViewModel.navigateTo(it) },
                            onNavigateUp = { fileManagerViewModel.navigateUp() },
                            onInspectArchive = { fileManagerViewModel.inspectArchive(it) },
                            onExtractArchive = { fileManagerViewModel.beginExtractTo(it) },
                            onOpenDocument = { fileManagerViewModel.openDocument(it, context) },
                            onOpenFile = {
                        if (it.isApk) apkInstallCandidate = it
                        else fileManagerViewModel.openWithExternalApp(it, context)
                    },
                            onContentScrolled = { /* Do not hide quick panels permanently on scroll. */ },
                            isSelectionMode = isSelectionMode,
                            selectedPaths = selectedPaths,
                            onItemLongClick = { fileManagerViewModel.enterSelectionMode(it) },
                            onItemTap = { fileManagerViewModel.toggleSelection(it) },
                            onItemMenuClick = { menuTargetItem = it }
                        )
                    }
                }
            }
        }
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

    // APK installation confirmation. Installation is never triggered directly from a file row.
    if (apkInstallCandidate != null) {
        val apk = apkInstallCandidate!!
        AlertDialog(
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

    // Delete Confirmation Dialog
    if (deleteConfirmItems != null) {
        val items = deleteConfirmItems!!
        AlertDialog(
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
                        "\"${items.first().name}\" will be permanently deleted. This can't be undone."
                    } else {
                        "These ${items.size} items will be permanently deleted. This can't be undone."
                    },
                    color = palette.textSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { fileManagerViewModel.confirmDelete() }) {
                    Text("Delete", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
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

    // Document In-App Text Preview Dialog
    if (selectedDocPreview != null) {
        val (docTitle, docContent) = selectedDocPreview!!
        AlertDialog(
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

// -------------------------------------------------------------
// MERGED VIEW: Storage -> Archives Section -> Docs Section -> Android Folders
// -------------------------------------------------------------
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
    onContentScrolled: () -> Unit = {}
) {
    val palette = com.example.ui.theme.LocalHiPalette.current
    var isArchivesExpanded by remember { mutableStateOf(false) }
    val mergedListState = rememberLazyListState()
    LaunchedEffect(mergedListState.firstVisibleItemIndex, mergedListState.firstVisibleItemScrollOffset) {
        if (mergedListState.firstVisibleItemIndex > 0 || mergedListState.firstVisibleItemScrollOffset > 48) onContentScrolled()
    }
    var isDocumentsExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        state = mergedListState,
        modifier = Modifier.fillMaxSize()
    ) {
        // 2. SECOND: EXTRACTABLE ARCHIVES SECTION (COLLAPSIBLE ON CLICK)
        item {
            SectionHeader(
                title = "Extractable Archives",
                subtitle = if (isArchivesExpanded) "Tap to collapse / hide files" else "ZIP, RAR, 7Z, TAR, APK • Tap to view",
                icon = Icons.Default.FolderZip,
                accentColor = palette.secondary,
                isCollapsible = true,
                isExpanded = isArchivesExpanded,
                itemCount = archives.size,
                onToggle = { isArchivesExpanded = !isArchivesExpanded }
            )
        }

        if (isArchivesExpanded) {
            if (archives.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = palette.surfaceElevated)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Archive,
                                contentDescription = null,
                                tint = palette.textSecondary.copy(alpha = 0.6f),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "No zip or rar archive files detected in standard storage.",
                                color = palette.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            } else {
                items(archives, key = { "arch_${it.path}" }) { archive ->
                    if (archive.isApk) {
                        GenericFileItemCard(
                            item = archive,
                            onClick = { if (isSelectionMode) onItemTap(archive) else onInstallApk(archive) },
                            onLongClick = { onItemLongClick(archive) },
                            isSelected = selectedPaths.contains(archive.path),
                            onMenuClick = { onItemMenuClick(archive) }
                        )
                    } else {
                        ArchiveItemCard(
                            archive = archive,
                            onInspect = { if (isSelectionMode) onItemTap(archive) else onInspectArchive(archive) },
                            onExtract = { onExtractArchive(archive) },
                            onLongClick = { onItemLongClick(archive) },
                            isSelected = selectedPaths.contains(archive.path),
                            onMenuClick = { onItemMenuClick(archive) }
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Button(
                            onClick = { isArchivesExpanded = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.secondary.copy(alpha = 0.2f),
                                contentColor = palette.secondary
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.ExpandLess, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Hide Archives (${archives.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // 3. THIRD: DOCUMENTS SECTION (COLLAPSIBLE ON CLICK)
        item {
            Spacer(modifier = Modifier.height(2.dp))
            SectionHeader(
                title = "Documents & Text Files",
                subtitle = if (isDocumentsExpanded) "Tap to collapse / hide files" else "PDF, DOCX, TXT, Subtitles • Tap to view",
                icon = Icons.Default.Description,
                accentColor = Color(0xFF60A5FA),
                isCollapsible = true,
                isExpanded = isDocumentsExpanded,
                itemCount = documents.size,
                onToggle = { isDocumentsExpanded = !isDocumentsExpanded }
            )
        }

        if (isDocumentsExpanded) {
            if (documents.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = palette.surfaceElevated)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                                contentDescription = null,
                                tint = palette.textSecondary.copy(alpha = 0.6f),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "No document files found in quick scan.",
                                color = palette.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            } else {
                items(documents, key = { "doc_${it.path}" }) { doc ->
                    DocumentItemCard(
                        doc = doc,
                        onOpen = { if (isSelectionMode) onItemTap(doc) else onOpenDocument(doc) },
                        onLongClick = { onItemLongClick(doc) },
                        isSelected = selectedPaths.contains(doc.path),
                        onMenuClick = { onItemMenuClick(doc) }
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Button(
                            onClick = { isDocumentsExpanded = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0x223B82F6),
                                contentColor = Color(0xFF60A5FA)
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.ExpandLess, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Hide Documents (${documents.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // 4. FOURTH: ALL FOLDERS OF ANDROID
        item {
            Spacer(modifier = Modifier.height(4.dp))
            SectionHeader(
                title = "Android Folders & Storage Explorer",
                subtitle = "Browse Download, DCIM, Documents & system folders",
                icon = Icons.Default.Folder,
                accentColor = Color(0xFF34D399)
            )
        }

        // Breadcrumbs & Up bar
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surfaceElevated)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 0.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentDir.parentFile != null && currentDir.parentFile?.canRead() == true) {
                        IconButton(
                            onClick = onNavigateUp,
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("navigate_up_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Up",
                                tint = palette.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = Color(0xFF34D399),
                        modifier = Modifier.size(18.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = currentDir.name.ifEmpty { "Internal Storage" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = palette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = "${folderItems.size} items",
                        fontSize = 11.sp,
                        color = palette.textSecondary
                    )
                }
            }
        }

        if (folderItems.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No folders or supported files in this directory", color = palette.textSecondary, fontSize = 12.sp)
                }
            }
        } else {
            items(folderItems, key = { "folder_${it.path}" }) { item ->
                if (item.isDirectory) {
                    FolderItemCard(
                        folder = item,
                        onClick = { if (isSelectionMode) onItemTap(item) else onNavigateFolder(item) },
                        onLongClick = { onItemLongClick(item) },
                        isSelected = selectedPaths.contains(item.path),
                        onMenuClick = { onItemMenuClick(item) }
                    )
                } else if (item.isApk) {
                    GenericFileItemCard(
                        item = item,
                        onClick = { if (isSelectionMode) onItemTap(item) else onOpenFile(item) },
                        onLongClick = { onItemLongClick(item) },
                        isSelected = selectedPaths.contains(item.path),
                        onMenuClick = { onItemMenuClick(item) }
                    )
                } else if (item.isArchive) {
                    ArchiveItemCard(
                        archive = item,
                        onInspect = { if (isSelectionMode) onItemTap(item) else onInspectArchive(item) },
                        onExtract = { onExtractArchive(item) },
                        onLongClick = { onItemLongClick(item) },
                        isSelected = selectedPaths.contains(item.path),
                        onMenuClick = { onItemMenuClick(item) }
                    )
                } else if (item.isDocument) {
                    DocumentItemCard(
                        doc = item,
                        onOpen = { if (isSelectionMode) onItemTap(item) else onOpenDocument(item) },
                        onLongClick = { onItemLongClick(item) },
                        isSelected = selectedPaths.contains(item.path),
                        onMenuClick = { onItemMenuClick(item) }
                    )
                } else {
                    GenericFileItemCard(
                        item = item,
                        onClick = { if (isSelectionMode) onItemTap(item) else onOpenFile(item) },
                        onLongClick = { onItemLongClick(item) },
                        isSelected = selectedPaths.contains(item.path),
                        onMenuClick = { onItemMenuClick(item) }
                    )
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
    onContentScrolled: () -> Unit = {}
) {
    val palette = com.example.ui.theme.LocalHiPalette.current
    val folderListState = rememberLazyListState()
    LaunchedEffect(folderListState.firstVisibleItemIndex, folderListState.firstVisibleItemScrollOffset) {
        if (folderListState.firstVisibleItemIndex > 0 || folderListState.firstVisibleItemScrollOffset > 48) onContentScrolled()
    }
    Column(modifier = Modifier.fillMaxSize()) {
        // Folder Bar
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(0.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 0.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentDir.parentFile != null && currentDir.parentFile?.canRead() == true) {
                    IconButton(
                        onClick = onNavigateUp,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Up",
                            tint = palette.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }

                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = Color(0xFF34D399),
                    modifier = Modifier.size(18.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = currentDir.name.ifEmpty { "Internal Storage" },
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = "${items.size} items",
                    fontSize = 11.sp,
                    color = palette.textSecondary
                )
            }
        }

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
                items(items, key = { it.path }) { item ->
                    if (item.isDirectory) {
                        FolderItemCard(
                            folder = item,
                            onClick = { if (isSelectionMode) onItemTap(item) else onNavigateFolder(item) },
                            onLongClick = { onItemLongClick(item) },
                            isSelected = selectedPaths.contains(item.path),
                            onMenuClick = { onItemMenuClick(item) }
                        )
                    } else if (item.isApk) {
                        GenericFileItemCard(
                            item = item,
                            onClick = { if (isSelectionMode) onItemTap(item) else onOpenFile(item) },
                            onLongClick = { onItemLongClick(item) },
                            isSelected = selectedPaths.contains(item.path),
                            onMenuClick = { onItemMenuClick(item) }
                        )
                    } else if (item.isArchive) {
                        ArchiveItemCard(
                            archive = item,
                            onInspect = { if (isSelectionMode) onItemTap(item) else onInspectArchive(item) },
                            onExtract = { onExtractArchive(item) },
                            onLongClick = { onItemLongClick(item) },
                            isSelected = selectedPaths.contains(item.path),
                            onMenuClick = { onItemMenuClick(item) }
                        )
                    } else if (item.isDocument) {
                        DocumentItemCard(
                            doc = item,
                            onOpen = { if (isSelectionMode) onItemTap(item) else onOpenDocument(item) },
                            onLongClick = { onItemLongClick(item) },
                            isSelected = selectedPaths.contains(item.path),
                            onMenuClick = { onItemMenuClick(item) }
                        )
                    } else {
                        GenericFileItemCard(
                            item = item,
                            onClick = { if (isSelectionMode) onItemTap(item) else onOpenFile(item) },
                            onLongClick = { onItemLongClick(item) },
                            isSelected = selectedPaths.contains(item.path),
                            onMenuClick = { onItemMenuClick(item) }
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
        border = null,
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 0.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
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
                                .clip(RoundedCornerShape(6.dp))
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
                        .clip(RoundedCornerShape(6.dp))
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

@Composable
private fun MenuActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color? = null,
    onClick: () -> Unit
) {
    val palette = com.example.ui.theme.LocalHiPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint ?: palette.textPrimary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Text(label, fontSize = 14.sp, color = tint ?: palette.textPrimary)
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    val palette = com.example.ui.theme.LocalHiPalette.current
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(text = label.uppercase(Locale.ROOT), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = palette.textSecondary, letterSpacing = 0.5.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, fontSize = 13.sp, color = palette.textPrimary)
    }
}

@Composable
private fun SelectionCheckCircle(isSelected: Boolean, modifier: Modifier = Modifier) {
    val palette = com.example.ui.theme.LocalHiPalette.current
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(if (isSelected) palette.primary else Color.Transparent)
            .border(1.5.dp, if (isSelected) palette.primary else palette.surfaceBorder, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArchiveItemCard(
    archive: FileItem,
    onInspect: () -> Unit,
    onExtract: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    isSelected: Boolean = false,
    onMenuClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val palette = com.example.ui.theme.LocalHiPalette.current
    val ext = archive.extension.uppercase(Locale.ROOT)
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.US) }
    val dateStr = if (archive.lastModified > 0) dateFormat.format(Date(archive.lastModified)) else ""

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onInspect, onLongClick = onLongClick)
            .testTag("archive_card_${archive.name}"),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = null,
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 0.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onLongClick != null) {
                SelectionCheckCircle(isSelected = isSelected)
                Spacer(modifier = Modifier.width(8.dp))
            }
            // Archive Type Badge Icon
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.secondary.copy(alpha = 0.2f))
                    .border(1.dp, palette.secondary.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (archive.isApk) Icons.Default.Android else Icons.Default.FolderZip,
                        contentDescription = if (archive.isApk) "APK" else "Archive",
                        tint = if (archive.isApk) Color(0xFF34D399) else palette.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (archive.isApk) "APK" else ext.ifEmpty { "ZIP" },
                        color = if (archive.isApk) Color(0xFF34D399) else palette.secondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = archive.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = archive.formattedSize,
                        fontSize = 11.sp,
                        color = palette.secondary,
                        fontWeight = FontWeight.Medium
                    )
                    if (dateStr.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("•", fontSize = 10.sp, color = palette.textSecondary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(dateStr, fontSize = 10.sp, color = palette.textSecondary)
                    }
                }
            }

            // Quick Extract Button
            IconButton(
                onClick = onExtract,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("extract_archive_${archive.name}")
            ) {
                Icon(
                    imageVector = Icons.Default.Unarchive,
                    contentDescription = "Extract",
                    tint = palette.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (onMenuClick != null) {
                IconButton(onClick = onMenuClick, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = palette.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentItemCard(
    doc: FileItem,
    onOpen: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    isSelected: Boolean = false,
    onMenuClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val palette = com.example.ui.theme.LocalHiPalette.current
    val ext = doc.extension.uppercase(Locale.ROOT)
    val badgeColor = when (doc.extension) {
        "pdf" -> Color(0xFFEF4444)
        "doc", "docx" -> Color(0xFF3B82F6)
        "xls", "xlsx" -> Color(0xFF10B981)
        "ppt", "pptx" -> Color(0xFFF97316)
        "srt", "vtt", "ass" -> palette.primary
        else -> palette.textSecondary
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = onLongClick)
            .testTag("doc_card_${doc.name}"),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = null,
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 0.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onLongClick != null) {
                SelectionCheckCircle(isSelected = isSelected)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(badgeColor.copy(alpha = 0.2f))
                    .border(1.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = ext.take(4),
                        color = badgeColor,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = doc.name,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = doc.formattedSize,
                    fontSize = 11.sp,
                    color = badgeColor,
                    fontWeight = FontWeight.Medium
                )
            }

            Icon(
                imageVector = Icons.Default.OpenInNew,
                contentDescription = "Open",
                tint = palette.textSecondary,
                modifier = Modifier.size(16.dp)
            )

            if (onMenuClick != null) {
                IconButton(onClick = onMenuClick, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = palette.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderItemCard(
    folder: FileItem,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    isSelected: Boolean = false,
    onMenuClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val palette = com.example.ui.theme.LocalHiPalette.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .testTag("folder_card_${folder.name}"),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = null,
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 0.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onLongClick != null) {
                SelectionCheckCircle(isSelected = isSelected)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = palette.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = folder.formattedSize,
                    fontSize = 11.sp,
                    color = palette.primary,
                    fontWeight = FontWeight.Normal
                )
            }

            if (onMenuClick != null) {
                IconButton(onClick = onMenuClick, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = palette.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = palette.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GenericFileItemCard(
    item: FileItem,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    isSelected: Boolean = false,
    onMenuClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val palette = com.example.ui.theme.LocalHiPalette.current
    val (icon, badgeBg, badgeTint, typeLabel) = when {
        item.isApk -> Quadruple(Icons.Default.Android, Color(0xFF34D399).copy(alpha = 0.2f), Color(0xFF34D399), "APK")
        item.fileType == FileType.VIDEO -> Quadruple(Icons.Default.Movie, palette.primary.copy(alpha = 0.2f), palette.primary, "VIDEO")
        item.fileType == FileType.AUDIO -> Quadruple(Icons.Default.MusicNote, Color(0xFFC084FC).copy(alpha = 0.2f), Color(0xFFC084FC), "AUDIO")
        item.fileType == FileType.IMAGE -> Quadruple(Icons.Default.Image, Color(0xFF34D399).copy(alpha = 0.2f), Color(0xFF34D399), "IMAGE")
        else -> Quadruple(Icons.AutoMirrored.Filled.InsertDriveFile, palette.surfaceBorder, palette.textSecondary, item.extension.uppercase(Locale.ROOT))
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = null,
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 0.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onLongClick != null) {
                SelectionCheckCircle(isSelected = isSelected)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(badgeBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = badgeTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.formattedSize,
                        fontSize = 11.sp,
                        color = badgeTint,
                        fontWeight = FontWeight.Medium
                    )
                    if (typeLabel.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("•", fontSize = 10.sp, color = palette.textSecondary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = typeLabel,
                            fontSize = 10.sp,
                            color = palette.textSecondary
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.Default.OpenInNew,
                contentDescription = "Open",
                tint = palette.textSecondary,
                modifier = Modifier.size(16.dp)
            )

            if (onMenuClick != null) {
                IconButton(onClick = onMenuClick, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = palette.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

// -------------------------------------------------------------
// ARCHIVE INSPECTOR & EXTRACTOR BOTTOM SHEET
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveInspectorBottomSheet(
    archive: FileItem,
    entries: List<ArchiveEntryInfo>?,
    extractingProgress: Float?,
    onDismiss: () -> Unit,
    onExtract: () -> Unit
) {
    val palette = com.example.ui.theme.LocalHiPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.surfaceElevated,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.secondary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderZip,
                            contentDescription = null,
                            tint = palette.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = archive.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = palette.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${archive.formattedSize} • ${entries?.size ?: 0} entries",
                            fontSize = 11.sp,
                            color = palette.textSecondary
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = palette.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Extraction status or action
            if (extractingProgress != null) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Extracting archive...",
                        color = palette.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = palette.primary
                    )
                }
            } else {
                Button(
                    onClick = onExtract,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.primary,
                        contentColor = if (palette.isDark) Color.Black else Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Unarchive,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Extract All Files to Folder",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = palette.surfaceBorder)
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Archive Contents",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = palette.textPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (entries == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = palette.secondary, modifier = Modifier.size(32.dp))
                }
            } else if (entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No file entries listed or non-zip format", color = palette.textSecondary, fontSize = 12.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(entries) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (entry.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                                contentDescription = null,
                                tint = if (entry.isDirectory) palette.secondary else palette.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = entry.name,
                                fontSize = 12.sp,
                                color = palette.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = entry.formattedSize,
                                fontSize = 11.sp,
                                color = palette.textSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveDestinationPickerBottomSheet(
    archive: FileItem,
    destination: File,
    folders: List<FileItem>,
    atRoot: Boolean,
    onDismiss: () -> Unit,
    onNavigateUp: () -> Unit,
    onOpenFolder: (FileItem) -> Unit,
    onExtractHere: () -> Unit
) {
    val palette = com.example.ui.theme.LocalHiPalette.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = palette.surfaceElevated
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .heightIn(min = 240.dp, max = 620.dp)
        ) {
            Text("Extract ${archive.name} to…", color = palette.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!atRoot) {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up", tint = palette.primary)
                    }
                }
                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(destination.name.ifEmpty { "Internal Storage" }, color = palette.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            HorizontalDivider(color = palette.textSecondary.copy(alpha = 0.2f))
            if (folders.isEmpty()) {
                Text("No folders here. Extract directly into this location.", color = palette.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 20.dp))
            } else {
                Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    folders.forEach { folder ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onOpenFolder(folder) }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(folder.name, color = palette.textPrimary, fontSize = 14.sp)
                        }
                    }
                }
            }
            Button(
                onClick = onExtractHere,
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.primary, contentColor = if (palette.isDark) Color.Black else Color.White)
            ) { Text("Extract Here", fontWeight = FontWeight.Bold) }
        }
    }
}

private fun fileTypeIcon(item: FileItem): androidx.compose.ui.graphics.vector.ImageVector = when {
    item.isDirectory -> Icons.Default.Folder
    item.isApk -> Icons.Default.Android
    item.isArchive -> Icons.Default.FolderZip
    item.isDocument -> Icons.Default.Description
    item.isVideo -> Icons.Default.Movie
    item.isAudio -> Icons.Default.MusicNote
    item.extension in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp") -> Icons.Default.Image
    else -> Icons.AutoMirrored.Filled.InsertDriveFile
}

private fun fileTypeLabel(item: FileItem): String = when {
    item.isDirectory -> "Folder"
    item.isApk -> "APK"
    item.isArchive -> "Archive"
    item.isDocument -> "Document"
    item.isVideo -> "Video"
    item.isAudio -> "Audio"
    item.extension in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp") -> "Image"
    else -> "File"
}

@Composable
private fun FileTypeBadge(item: FileItem) {
    val palette = com.example.ui.theme.LocalHiPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(fileTypeIcon(item), contentDescription = fileTypeLabel(item), tint = palette.primary, modifier = Modifier.size(26.dp))
        Text(fileTypeLabel(item), color = palette.textSecondary, fontSize = 10.sp)
    }
}
