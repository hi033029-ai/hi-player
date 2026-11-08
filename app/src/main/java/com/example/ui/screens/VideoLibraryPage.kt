package com.example.ui.screens

import com.example.ui.theme.hiContainerBorder

import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.filemanager.StoragePermission
import com.example.model.VideoItem
import com.example.ui.components.FolderItemRow
import com.example.ui.components.FolderGridCard
import com.example.ui.components.VideoGridCard
import com.example.ui.components.VideoInfoDialog
import com.example.ui.components.VideoItemCard
import com.example.ui.theme.LocalHiPalette
import com.example.ui.theme.LocalHiUiMetrics
import com.example.viewmodel.LibraryNavMode
import com.example.viewmodel.LibraryViewMode
import com.example.viewmodel.LibraryViewModel
import com.example.viewmodel.VideoSortOption
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import sh.calvin.reorderable.rememberReorderableLazyListState

internal data class VideoSelectionToolbarState(
    val count: Int, val onClose: () -> Unit, val onSelectAll: () -> Unit,
    val onClear: () -> Unit, val onProperties: () -> Unit, val onDelete: () -> Unit,
)

@Composable
internal fun VideoSelectionToolbar(state: VideoSelectionToolbarState) {
    com.example.ui.components.InlineSelectionToolbar(
        count = state.count, onClose = state.onClose, onSelectAll = state.onSelectAll,
        onClear = state.onClear, onProperties = state.onProperties, onDelete = state.onDelete,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoLibraryPage(
    libraryViewModel: LibraryViewModel,
    onOpenVideo: (VideoItem) -> Unit,
    onOpenFromStart: (VideoItem) -> Unit,
    onOpenSettings: () -> Unit,
    onMoveVideosToRecycleBin: (Collection<VideoItem>) -> Unit = {},
    modifier: Modifier = Modifier,
    includeHeader: Boolean = true,
    onSearchRequested: (() -> Unit)? = null,
    pageMode: LibraryNavMode,
    isActive: Boolean,
    mediaAccess: com.example.ui.components.LibraryMediaAccess,
    onSelectionChanged: (Boolean) -> Unit,
    onSelectionToolbarChanged: (VideoSelectionToolbarState?) -> Unit,
    onPlaybackQueueSelected: (List<VideoItem>) -> Unit,
    onPlayAsAudio: (VideoItem) -> Unit,
) {
    val context = LocalContext.current
    val availableVideos by libraryViewModel.allVideos.collectAsStateWithLifecycle()
    val pageVideos = remember(libraryViewModel, pageMode) { libraryViewModel.videosForMode(pageMode) }
    val filteredVideos by pageVideos.collectAsStateWithLifecycle()
    val availableFolders by libraryViewModel.folders.collectAsStateWithLifecycle()
    val navMode = pageMode
    val viewMode by libraryViewModel.viewMode.collectAsStateWithLifecycle()
    val gridMinSize by libraryViewModel.gridMinSize.collectAsStateWithLifecycle()
    val sortOption by libraryViewModel.sortOption.collectAsStateWithLifecycle()
    val sharedFolder by libraryViewModel.selectedFolder.collectAsStateWithLifecycle()
    val selectedFolder = sharedFolder.takeIf { pageMode == LibraryNavMode.FOLDERS }
    val searchQuery by libraryViewModel.searchQuery.collectAsStateWithLifecycle()
    val isLoading by libraryViewModel.isLoading.collectAsStateWithLifecycle()
    val scanStatus by libraryViewModel.scanStatus.collectAsStateWithLifecycle()
    val showScanFeedback by libraryViewModel.showScanFeedback.collectAsStateWithLifecycle()
    val canShowLibrary = mediaAccess.granted && !(mediaAccess.limited && scanStatus == com.example.ui.components.LibraryScanStatus.SCANNING)
    val allVideos = if (canShowLibrary) availableVideos else emptyList()
    val folders = if (canShowLibrary) availableFolders else emptyList()
    // Share one library guide with MusicScreen instead of showing it again on
    // every destination or pager tab.
    val swipeTip = com.example.ui.components.rememberFirstUseTip("library_swipe_tabs_v1")
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(5_000)
        swipeTip.dismiss()
    }
    val historyRecords by libraryViewModel.historyRecords.collectAsStateWithLifecycle()
    val availableContinueWatching by libraryViewModel.continueWatchingVideos.collectAsStateWithLifecycle()
    val continueWatchingVideos = if (canShowLibrary) availableContinueWatching else emptyList()
    val playerSettings by libraryViewModel.playerSettings.collectAsStateWithLifecycle()
    val deleteProgress by libraryViewModel.deleteProgress.collectAsStateWithLifecycle()
    val gridTransformState = androidx.compose.foundation.gestures.rememberTransformableState { zoomChange, _, _ ->
        if (viewMode == LibraryViewMode.GRADLE_GRID && zoomChange.isFinite() && kotlin.math.abs(zoomChange - 1f) > 0.005f) {
            libraryViewModel.setGridMinSize((gridMinSize / zoomChange).roundToInt())
        }
    }
    val pinchGridModifier = Modifier.transformable(
        state = gridTransformState,
        canPan = { false },
        enabled = viewMode == LibraryViewMode.GRADLE_GRID
    )
    val treeCurrentPath by libraryViewModel.treeCurrentPath.collectAsStateWithLifecycle()
    val currentTreeNode by libraryViewModel.currentTreeNode.collectAsStateWithLifecycle()

    var showFolders by rememberSaveable { mutableStateOf(false) }
    var inspectVideo by remember { mutableStateOf<VideoItem?>(null) }
    var selectedVideoPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedFolderKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isVideoSelectionMode by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<VideoItem?>(null) }
    var renameText by rememberSaveable { mutableStateOf("") }
    // Overflow-menu deletion is independent from long-press multi-select.
    // A one-item delete must never turn on the library action bar.
    var pendingDeleteVideos by remember { mutableStateOf<List<VideoItem>?>(null) }
    var showSelectionProperties by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val selectionMode = isVideoSelectionMode
    androidx.compose.runtime.SideEffect {
        if (isActive) {
            onSelectionChanged(selectionMode || inspectVideo != null || renameTarget != null || pendingDeleteVideos != null || showSelectionProperties)
        }
    }
    // Imported, SAF, and growing downloads can share a MediaStore ID of zero.
    // Use their path/URI as the key so Compose never reuses a visible row's
    // thumbnail or overflow state when the library list is scrolled.
    fun videoKey(video: VideoItem): String = video.path.ifBlank {
        video.uri.toString().takeIf { it.isNotBlank() && it != Uri.EMPTY.toString() }
            ?: "video:${video.id}:${video.title}"
    }
    val selectedVideos = remember(selectedVideoPaths, selectedFolderKeys, allVideos) {
        allVideos.filter { video ->
            videoKey(video) in selectedVideoPaths || selectedFolderKeys.any { key ->
                videoBelongsToSelectedFolder(key, video.path, video.folderName)
            }
        }
    }
    // Filtering, sorting and saved-order application already completed off-main.
    val orderedFilteredVideos = filteredVideos
    val currentOrderedVideos = rememberUpdatedState(orderedFilteredVideos)
    val displayedQueue = if (navMode == LibraryNavMode.TREE_FOLDERS)
        currentTreeNode?.directVideos.orEmpty() else orderedFilteredVideos
    val onVideoSelected: (VideoItem) -> Unit = { video ->
        onPlaybackQueueSelected(displayedQueue)
        onOpenVideo(video)
    }
    val onPlayFromStart: (VideoItem) -> Unit = { video ->
        onPlaybackQueueSelected(displayedQueue)
        onOpenFromStart(video)
    }
    val onAudioSelected: (VideoItem) -> Unit = { video ->
        onPlaybackQueueSelected(displayedQueue)
        onPlayAsAudio(video)
    }
    var hasFullDownloadAccess by remember { mutableStateOf(StoragePermission.isGranted()) }
    val allFilesAccessLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasFullDownloadAccess = StoragePermission.isGranted()
        libraryViewModel.refreshVideos(silent = true)
    }
    val showContinueWatching = searchQuery.isBlank() &&
        selectedFolder == null &&
        (navMode != LibraryNavMode.TREE_FOLDERS || treeCurrentPath == null) &&
        continueWatchingVideos.isNotEmpty()
    // The controls item remains mounted during selection, keeping row indices stable.
    val currentVideoHeaderOffset = rememberUpdatedState(1 + if (showContinueWatching) 1 else 0)
    val videoListState = rememberLazyListState()
    val reorderableVideoListState = rememberReorderableLazyListState(videoListState) { from, to ->
        val items = currentOrderedVideos.value
        val fromIndex = from.index - currentVideoHeaderOffset.value
        val toIndex = to.index - currentVideoHeaderOffset.value
        if (fromIndex in items.indices && toIndex in items.indices) {
            libraryViewModel.moveVideoKeysInDisplayOrder(
                videoKey(items[fromIndex]),
                videoKey(items[toIndex])
            )
        }
    }
    val videoGridState = rememberLazyGridState()
    val reorderableVideoGridState = rememberReorderableLazyGridState(videoGridState) { from, to ->
        val items = currentOrderedVideos.value
        val fromIndex = from.index - currentVideoHeaderOffset.value
        val toIndex = to.index - currentVideoHeaderOffset.value
        if (fromIndex in items.indices && toIndex in items.indices) {
            libraryViewModel.moveVideoKeysInDisplayOrder(
                videoKey(items[fromIndex]),
                videoKey(items[toIndex])
            )
        }
    }
    val folderListState = rememberLazyListState()
    val folderGridState = rememberLazyGridState()
    val treeListState = rememberLazyListState()
    fun toggleVideoSelection(video: VideoItem) {
        val key = videoKey(video)
        selectedVideoPaths = if (key in selectedVideoPaths) selectedVideoPaths - key else selectedVideoPaths + key
        isVideoSelectionMode = true
    }

    fun toggleFolderSelection(key: String) {
        selectedFolderKeys = if (key in selectedFolderKeys) selectedFolderKeys - key else selectedFolderKeys + key
        isVideoSelectionMode = true
    }

    fun exitVideoSelectionMode() {
        selectedFolderKeys = emptySet()
        selectedVideoPaths = emptySet()
        isVideoSelectionMode = false
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            libraryViewModel.addExternalVideo(it) { item ->
                onVideoSelected(item)
            }
        }
    }

    val historyMap = remember(historyRecords) {
        historyRecords.associateBy { it.uriString }
    }

    val palette = LocalHiPalette.current
    val uiMetrics = LocalHiUiMetrics.current

    // Back handler to navigate back to folder directory when inside a folder
    BackHandler(enabled = isActive && selectedFolder != null) {
        libraryViewModel.selectFolder(null)
    }

    // Back handler for Tree Folders: step up one directory level at a time
    // instead of exiting the tree entirely.
    BackHandler(enabled = isActive && navMode == LibraryNavMode.TREE_FOLDERS && treeCurrentPath != null) {
        libraryViewModel.navigateTreeUp()
    }

    // The last enabled handler wins: cancel selection before navigating a tree folder.
    BackHandler(enabled = isActive && selectionMode) { exitVideoSelectionMode() }

    val isVideoHome = selectedFolder == null &&
        (navMode != LibraryNavMode.TREE_FOLDERS || treeCurrentPath == null)
    LaunchedEffect(isVideoHome, isActive) {
        if (isActive && !isVideoHome) {
            libraryViewModel.setSearchQuery("")
        }
    }
    val selectionCandidates = if (navMode == LibraryNavMode.TREE_FOLDERS) currentTreeNode?.directVideos.orEmpty() else filteredVideos
    LaunchedEffect(selectedFolder, treeCurrentPath, navMode) { exitVideoSelectionMode() }
    val toolbarState = remember(selectionMode, selectedVideos, selectionCandidates, selectedFolderKeys, folders, currentTreeNode, selectedFolder, navMode, searchQuery) {
        if (!selectionMode) null else VideoSelectionToolbarState(
            count = selectedVideos.size,
            onClose = { exitVideoSelectionMode() },
            onSelectAll = {
                selectedVideoPaths = selectionCandidates.map(::videoKey).toSet()
                selectedFolderKeys = when {
                    navMode == LibraryNavMode.FOLDERS && selectedFolder == null && searchQuery.isBlank() -> folders.map { "name:${it.name}" }.toSet()
                    navMode == LibraryNavMode.TREE_FOLDERS -> currentTreeNode?.childFolders.orEmpty().map { "path:${it.dirPath}" }.toSet()
                    else -> emptySet()
                }
            },
            onClear = { selectedVideoPaths = emptySet(); selectedFolderKeys = emptySet() },
            onProperties = { showSelectionProperties = true },
            onDelete = { pendingDeleteVideos = selectedVideos },
        )
    }
    androidx.compose.runtime.SideEffect { if (isActive) onSelectionToolbarChanged(toolbarState) }
    val videoSelectionActions: @Composable () -> Unit = { toolbarState?.let { VideoSelectionToolbar(it) } }

    // Folder breadcrumbs and contextual selection controls scroll with their
    // content. The shared search/category toolbar remains outside the pager.
    val libraryControls: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (selectedFolder != null || (navMode == LibraryNavMode.TREE_FOLDERS && treeCurrentPath != null)) {
                Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.CenterStart) {
                    run {
                        // 3. FOLDER NAVIGATION BREADCRUMB & STATUS BAR
                        if (selectedFolder != null) {
                            // Inside Folder Header Bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(palette.surfaceElevated)
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { libraryViewModel.selectFolder(null) }
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back to Folders",
                                        tint = palette.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = selectedFolder ?: "",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = palette.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${filteredVideos.size} videos in folder",
                                            fontSize = 11.sp,
                                            color = palette.textSecondary
                                        )
                                    }
                                }

                                // Right: View Mode Toggle & Sort Dropdown
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    IconButton(
                                        onClick = { libraryViewModel.toggleViewMode() },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (viewMode == LibraryViewMode.LAYER_LIST) Icons.Default.GridView else Icons.Default.ViewList,
                                            contentDescription = "Toggle View",
                                            tint = palette.textSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Box {
                                        IconButton(
                                            onClick = { sortMenuExpanded = true },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Sort,
                                                contentDescription = "Sort By",
                                                tint = palette.textSecondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = sortMenuExpanded,
                                            onDismissRequest = { sortMenuExpanded = false },
                                            modifier = Modifier.background(palette.surfaceElevated)
                                        ) {
                                            VideoSortOption.entries.forEach { option ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            option.displayName,
                                                            color = if (sortOption == option) palette.primary else palette.textPrimary,
                                                            fontWeight = if (sortOption == option) FontWeight.Bold else FontWeight.Normal,
                                                            fontSize = 12.5.sp
                                                        )
                                                    },
                                                    onClick = {
                                                        sortMenuExpanded = false
                                                        libraryViewModel.setSortOption(option)
                                                    }
                                                )
                                            }
                                        }
                                    }

                                }
                            }
                        } else if (navMode == LibraryNavMode.TREE_FOLDERS && treeCurrentPath != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable(onClickLabel = "Up one folder") { libraryViewModel.navigateTreeUp() }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Up one folder", tint = palette.primary,
                                    modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(treeCurrentPath?.substringAfterLast('/').orEmpty(), color = palette.textPrimary,
                                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = palette.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (includeHeader) TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        com.example.ui.components.HiPlayerLogoBadge(size = uiMetrics.logoSize)
                        Spacer(Modifier.width(10.dp))
                        Text("Hi Player", color = palette.textPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(enabled = !isLoading, onClick = { libraryViewModel.refreshVideos() }, modifier = Modifier.testTag("refresh_videos_button")) {
                        if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = palette.primary)
                        else Icon(Icons.Default.Refresh, "Refresh", tint = palette.textPrimary)
                    }
                },
                windowInsets = TopAppBarDefaults.windowInsets,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.surface),
            )
        },
        floatingActionButton = {}
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {

            // 4. PLAN SHEET CONTENT CANVAS
            // Keep branch-specific fillMaxSize() lists below the toolbar/header.
            // A weighted canvas prevents them from drawing underneath the controls.
            Box(Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.fillMaxSize()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !hasFullDownloadAccess && allVideos.any { it.isPartialDownload }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Allow all-files access to show unfinished browser downloads.",
                        modifier = Modifier.weight(1f),
                        color = palette.textSecondary,
                        fontSize = 12.sp
                    )
                    TextButton(
                        onClick = {
                            allFilesAccessLauncher.launch(StoragePermission.buildRequestIntent(context))
                        }
                    ) {
                        Text("Allow", color = palette.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (mediaAccess.granted) {
                com.example.ui.components.LibraryAccessExplanation(audio = false)
                if (isActive) com.example.ui.components.FirstUseTip(swipeTip, "Swipe to explore your videos",
                    "Swipe left or right across the library to move between Folders, All Videos, Recent, Tree Folders and Favourites. You can also tap the tabs.",
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp))
                // Cached-content and periodic refreshes remain silent.
            }
            if (!mediaAccess.granted || (showScanFeedback && scanStatus == com.example.ui.components.LibraryScanStatus.SCANNING && allVideos.isEmpty())) {
                libraryControls()
            }
            if (!mediaAccess.granted) {
                com.example.ui.components.LibraryPermissionPanel(audio = false, onOpenSettings = mediaAccess.openSettings, error = mediaAccess.error)
            } else if (showScanFeedback && scanStatus == com.example.ui.components.LibraryScanStatus.SCANNING && allVideos.isEmpty()) {
                com.example.ui.components.LibraryScanFeedback(scanStatus, 0, audio = false, centered = true)
            } else if (
                navMode == LibraryNavMode.FOLDERS &&
                    selectedFolder == null &&
                    searchQuery.isBlank()
            ) {
                // CLEAN FOLDER SYSTEM VIEW (Matching User's Reference Image)
                if (folders.isEmpty()) {
                    EmptyState(onBrowse = { filePickerLauncher.launch(arrayOf("video/*")) }, onRefresh = { libraryViewModel.refreshVideos() })
                } else if (viewMode == LibraryViewMode.GRADLE_GRID) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = gridMinSize.dp),
                        state = folderGridState,
                        contentPadding = PaddingValues(start = 12.dp, top = 6.dp, end = 12.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize().then(pinchGridModifier)
                    ) {
                        item(key = "library_controls", span = { GridItemSpan(maxLineSpan) }) { libraryControls() }
                        if (showContinueWatching) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                ContinueWatchingStrip(
                                    items = continueWatchingVideos,
                                    onVideoSelected = { video ->
                                        onPlaybackQueueSelected(continueWatchingVideos.map { it.video })
                                        onOpenVideo(video)
                                    },
                                    onRemove = { libraryViewModel.removeFromContinueWatching(it) },
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }
                        itemsIndexed(
                            items = folders,
                            key = { _, folder -> folder.name },
                            contentType = { _, _ -> "folder_card" }
                        ) { _, folder ->
                            FolderGridCard(
                                name = folder.name,
                                videoCount = folder.videoCount,
                                hasNewVideo = folder.hasNewVideo,
                                isSelected = "name:${folder.name}" in selectedFolderKeys,
                                onClick = { if (selectionMode) toggleFolderSelection("name:${folder.name}") else libraryViewModel.selectFolder(folder.name) },
                                onLongClick = { toggleFolderSelection("name:${folder.name}") }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = folderListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item(key = "library_controls") { libraryControls() }
                        if (showContinueWatching) {
                            item {
                                ContinueWatchingStrip(
                                    items = continueWatchingVideos,
                                    onVideoSelected = { video ->
                                        onPlaybackQueueSelected(continueWatchingVideos.map { it.video })
                                        onOpenVideo(video)
                                    },
                                    onRemove = { libraryViewModel.removeFromContinueWatching(it) },
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }
                        itemsIndexed(
                            items = folders,
                            key = { _, folder -> folder.name },
                            contentType = { _, _ -> "folder_item" }
                        ) { _, folder ->
                                    FolderItemRow(
                                        folder = folder,
                                        isSelected = "name:${folder.name}" in selectedFolderKeys,
                                onClick = { if (selectionMode) toggleFolderSelection("name:${folder.name}") else libraryViewModel.selectFolder(folder.name) },
                                onLongClick = { toggleFolderSelection("name:${folder.name}") }
                            )
                        }
                    }
                }
            } else if (navMode == LibraryNavMode.TREE_FOLDERS) {
                // REAL NESTED FOLDER TREE - browse actual on-disk subfolders,
                // descending into them, instead of a flat "Recent" list.
                val node = currentTreeNode
                if (node == null || (node.childFolders.isEmpty() && node.directVideos.isEmpty())) {
                    EmptyState(onBrowse = { filePickerLauncher.launch(arrayOf("video/*")) }, onRefresh = { libraryViewModel.refreshVideos() })
                } else {
                    LazyColumn(
                        state = treeListState,
                        contentPadding = PaddingValues(start = 12.dp, top = 6.dp, end = 12.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize().then(pinchGridModifier)
                    ) {
                        item(key = "library_controls") { libraryControls() }
                        if (showContinueWatching) {
                            item {
                                ContinueWatchingStrip(
                                    items = continueWatchingVideos,
                                    onVideoSelected = { video ->
                                        onPlaybackQueueSelected(continueWatchingVideos.map { it.video })
                                        onOpenVideo(video)
                                    },
                                    onRemove = { libraryViewModel.removeFromContinueWatching(it) },
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }
                        if (node.childFolders.isNotEmpty()) {
                            if (viewMode == LibraryViewMode.GRADLE_GRID) {
                                val columns = (360 / gridMinSize).coerceIn(1, 3)
                                val rows = node.childFolders.chunked(columns)
                                items(rows, key = { row -> row.first().dirPath }) { row ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        row.forEach { child ->
                                            Box(modifier = Modifier.weight(1f)) {
                                                FolderGridCard(
                                                    name = child.name,
                                                    videoCount = child.totalVideoCount,
                                                    hasNewVideo = child.hasNewVideo,
                                                    isSelected = "path:${child.dirPath}" in selectedFolderKeys,
                                                    onClick = { if (selectionMode) toggleFolderSelection("path:${child.dirPath}") else libraryViewModel.navigateTreeInto(child.dirPath) },
                                                    onLongClick = { toggleFolderSelection("path:${child.dirPath}") }
                                                )
                                            }
                                        }
                                        if (row.size == 1) {
                                            Box(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            } else {
                                items(node.childFolders, key = { it.dirPath }) { child ->
                                    FolderItemRow(
                                        folder = com.example.viewmodel.VideoFolder(
                                            name = child.name,
                                            videoCount = child.totalVideoCount,
                                            sampleVideo = null,
                                            hasNewVideo = child.hasNewVideo
                                        ),
                                        isSelected = "path:${child.dirPath}" in selectedFolderKeys,
                                        onClick = { if (selectionMode) toggleFolderSelection("path:${child.dirPath}") else libraryViewModel.navigateTreeInto(child.dirPath) },
                                        onLongClick = { toggleFolderSelection("path:${child.dirPath}") }
                                    )
                                }
                            }
                        }

                        if (node.directVideos.isNotEmpty()) {
                            items(node.directVideos, key = { videoKey(it) }, contentType = { "video" }) { video ->
                                val record = historyMap[video.uri.toString()]
                                val progressFraction = if (record != null && record.durationMs > 0) {
                                    (record.lastPositionMs.toFloat() / record.durationMs.toFloat()).coerceIn(0f, 1f)
                                } else 0f
                                VideoItemCard(
                                    video = video.copy(isFavorite = record?.isFavorite ?: false),
                                    progressFraction = progressFraction,
                                    isSelected = selectedVideoPaths.contains(videoKey(video)),
                                    onClick = { if (selectionMode) toggleVideoSelection(video) else onVideoSelected(video) },
                                    onLongClick = { toggleVideoSelection(video) },
                                    onFavoriteToggle = { isFav ->
                                        libraryViewModel.toggleFavorite(video, isFav)
                                    },
                                    onPlayInBackground = { onAudioSelected(video) },
                                    onPlayFromStart = { onPlayFromStart(video) },
                                    onShowDetails = { inspectVideo = video }
                                )
                            }
                        }
                    }
                }
            } else if (filteredVideos.isEmpty()) {
                EmptyState(onBrowse = { filePickerLauncher.launch(arrayOf("video/*")) }, onRefresh = { libraryViewModel.refreshVideos() })
            } else {
                // Unified media items (List or Grid). Continue Watching is kept
                // above both layouts so it never disappears when switching modes.
                if (viewMode == LibraryViewMode.GRADLE_GRID) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = gridMinSize.dp),
                        state = videoGridState,
                        contentPadding = PaddingValues(start = 12.dp, top = 6.dp, end = 12.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize().then(pinchGridModifier)
                    ) {
                        item(key = "library_controls", span = { GridItemSpan(maxLineSpan) }) { libraryControls() }
                        if (showContinueWatching) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                ContinueWatchingStrip(
                                    items = continueWatchingVideos,
                                    onVideoSelected = { video ->
                                        onPlaybackQueueSelected(continueWatchingVideos.map { it.video })
                                        onOpenVideo(video)
                                    },
                                    onRemove = { libraryViewModel.removeFromContinueWatching(it) },
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }
                        itemsIndexed(
                            items = orderedFilteredVideos,
                            key = { _, video -> videoKey(video) },
                            contentType = { _, _ -> "video_grid_item" }
                        ) { _, video ->
                            ReorderableItem(reorderableVideoGridState, key = videoKey(video)) { isDragging ->
                                val record = historyMap[video.uri.toString()]
                                val progressFraction = if (record != null && record.durationMs > 0) {
                                    (record.lastPositionMs.toFloat() / record.durationMs.toFloat()).coerceIn(0f, 1f)
                                } else 0f
                                VideoGridCard(
                                    video = video.copy(isFavorite = record?.isFavorite ?: false),
                                    progressFraction = progressFraction,
                                    isSelected = selectedVideoPaths.contains(videoKey(video)),
                                    onClick = { if (selectionMode) toggleVideoSelection(video) else onVideoSelected(video) },
                                    onLongClick = { toggleVideoSelection(video) },
                                    onFavoriteToggle = { isFav -> libraryViewModel.toggleFavorite(video, isFav) },
                                    onPlayInBackground = { onAudioSelected(video) },
                                    onPlayFromStart = { onPlayFromStart(video) },
                                    onShowDetails = { inspectVideo = video },
                                    onRename = {
                                        renameTarget = video
                                        renameText = video.title.substringBeforeLast('.', video.title)
                                    },
                                    onDelete = {
                                        exitVideoSelectionMode()
                                        pendingDeleteVideos = listOf(video)
                                    },
                                    reorderHandle = {
                                        Icon(
                                            Icons.Default.DragHandle,
                                            contentDescription = "Hold to reorder video",
                                            tint = palette.textSecondary,
                                            modifier = with(this) {
                                                Modifier.size(32.dp).then(
                                                    if (!selectionMode) Modifier.longPressDraggableHandle() else Modifier
                                                )
                                            },
                                        )
                                    },
                                    modifier = with(this) {
                                        Modifier
                                            .graphicsLayer {
                                                scaleX = if (isDragging) 1.03f else 1f
                                                scaleY = if (isDragging) 1.03f else 1f
                                            }
                                    }
                                )
                            }
                        }
                    }
                } else {
                                            // Video rows

                    LazyColumn(
                        state = videoListState,
                        contentPadding = PaddingValues(start = 12.dp, top = 6.dp, end = 12.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item(key = "library_controls") { libraryControls() }
                        if (showContinueWatching) {
                            item {
                                ContinueWatchingStrip(
                                    items = continueWatchingVideos,
                                    onVideoSelected = { video ->
                                        onPlaybackQueueSelected(continueWatchingVideos.map { it.video })
                                        onOpenVideo(video)
                                    },
                                    onRemove = { libraryViewModel.removeFromContinueWatching(it) },
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }
                        // Video item rows
                        itemsIndexed(
                            items = orderedFilteredVideos,
                            key = { _, video -> videoKey(video) },
                            contentType = { _, _ -> "video_item" }
                        ) { _, video ->
                            ReorderableItem(reorderableVideoListState, key = videoKey(video)) { isDragging ->
                                val record = historyMap[video.uri.toString()]
                                val progressFraction = if (record != null && record.durationMs > 0) {
                                    (record.lastPositionMs.toFloat() / record.durationMs.toFloat()).coerceIn(0f, 1f)
                                } else 0f
                                VideoItemCard(
                                    video = video.copy(isFavorite = record?.isFavorite ?: false),
                                    progressFraction = progressFraction,
                                    isSelected = selectedVideoPaths.contains(videoKey(video)),
                                    onClick = { if (selectionMode) toggleVideoSelection(video) else onVideoSelected(video) },
                                    onLongClick = { toggleVideoSelection(video) },
                                    onFavoriteToggle = { isFav -> libraryViewModel.toggleFavorite(video, isFav) },
                                    onPlayInBackground = { onAudioSelected(video) },
                                    onPlayFromStart = { onPlayFromStart(video) },
                                    onShowDetails = { inspectVideo = video },
                                    onRename = {
                                        renameTarget = video
                                        renameText = video.title.substringBeforeLast('.', video.title)
                                    },
                                    onDelete = {
                                        exitVideoSelectionMode()
                                        pendingDeleteVideos = listOf(video)
                                    },
                                    reorderHandle = {
                                        Icon(
                                            Icons.Default.DragHandle,
                                            contentDescription = "Hold to reorder video",
                                            tint = palette.textSecondary,
                                            modifier = with(this) {
                                                Modifier.size(32.dp).then(
                                                    if (!selectionMode) Modifier.longPressDraggableHandle() else Modifier
                                                )
                                            },
                                        )
                                    },
                                    modifier = with(this) {
                                        Modifier
                                            .graphicsLayer {
                                                scaleX = if (isDragging) 1.03f else 1f
                                                scaleY = if (isDragging) 1.03f else 1f
                                            }
                                    }
                                )
                            }
                        }
                    }
                }
            }
            }
            if (selectionMode && (selectedFolder != null || treeCurrentPath != null)) {
                Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(palette.surface)) {
                    videoSelectionActions()
                }
            }
            }
        }
    }

    if (isActive) {
    // Video Technical Telemetry Inspector Dialog
    inspectVideo?.let { video ->
        VideoInfoDialog(
            video = video,
            telemetry = com.example.player.DecoderTelemetry(
                codecName = video.codec,
                resolution = video.resolutionString,
                fps = if (video.frameRate > 0) video.frameRate else 60.0f,
                bitrateMbps = if (video.bitrate > 0) video.bitrate / 1_000_000f else 65.0f,
                colorSpace = if (video.isHdr) "BT.2020 / HDR10" else "Rec.709",
                audioFormat = "DTS-HD MA / TrueHD (${video.audioChannels} Ch)"
            ),
            onDismiss = { inspectVideo = null }
        )
    }

    renameTarget?.let { video ->
        AlertDialog(
            modifier = androidx.compose.ui.Modifier.hiContainerBorder(androidx.compose.foundation.shape.RoundedCornerShape(28.dp)),
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename video") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("File name") },
                    supportingText = { Text("The original .${video.title.substringAfterLast('.', "video")} extension is kept") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    libraryViewModel.renameVideo(video, renameText)
                    renameTarget = null
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } }
        )
    }

    if (showSelectionProperties) {
        val totalBytes = selectedVideos.sumOf { it.sizeBytes }
        AlertDialog(
            modifier = androidx.compose.ui.Modifier.hiContainerBorder(androidx.compose.foundation.shape.RoundedCornerShape(28.dp)),
            onDismissRequest = { showSelectionProperties = false },
            title = { Text("Video properties") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${selectedVideos.size} video${if (selectedVideos.size == 1) "" else "s"}")
                    Text("Total size: ${formatVideoBytes(totalBytes)}")
                    Text(selectedVideos.joinToString("\n") { it.title }.take(1200), maxLines = 12, overflow = TextOverflow.Ellipsis)
                }
            },
            confirmButton = { TextButton(onClick = { showSelectionProperties = false }) { Text("Close") } }
        )
    }

    deleteProgress?.let { progress ->
        val remaining = (progress.total - progress.completed - progress.failed).coerceAtLeast(0)
        AlertDialog(
            modifier = androidx.compose.ui.Modifier.hiContainerBorder(androidx.compose.foundation.shape.RoundedCornerShape(28.dp)),
            onDismissRequest = { libraryViewModel.cancelDeleteVideos() },
            title = { Text("Deleting videos", color = palette.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Deleted ${progress.completed} of ${progress.total}", color = palette.textPrimary)
                    Text("Remaining: $remaining", color = palette.textSecondary, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { if (progress.total == 0) 1f else (progress.completed + progress.failed).toFloat() / progress.total },
                        modifier = Modifier.fillMaxWidth(),
                        color = palette.primary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { libraryViewModel.cancelDeleteVideos() }) {
                    Text("Cancel", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    pendingDeleteVideos?.let { videosToDelete ->
        AlertDialog(
            modifier = androidx.compose.ui.Modifier.hiContainerBorder(androidx.compose.foundation.shape.RoundedCornerShape(28.dp)),
            onDismissRequest = { pendingDeleteVideos = null },
            title = { Text("Delete video${if (videosToDelete.size == 1) "" else "s"}?") },
            text = { Text("This moves ${videosToDelete.size} video file${if (videosToDelete.size == 1) "" else "s"} to Recycle Bin. Folders and other files remain. Folder groups may include locations with the same name. You can restore the videos later.") },
            confirmButton = {
                TextButton(onClick = {
                    onMoveVideosToRecycleBin(videosToDelete)
                    exitVideoSelectionMode()
                    pendingDeleteVideos = null
                }) { Text("Delete", color = Color(0xFFEF4444)) }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteVideos = null }) { Text("Cancel") } }
        )
    }
}
}
