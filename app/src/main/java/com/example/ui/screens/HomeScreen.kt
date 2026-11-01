package com.example.ui.screens

import com.example.ui.theme.hiContainerBorder

import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.AppThemeMode
import com.example.filemanager.StoragePermission
import com.example.model.VideoItem
import com.example.ui.components.FolderItemRow
import com.example.ui.components.FolderGridCard
import com.example.ui.components.HorizontalThemeSelector
import com.example.ui.components.VideoGridCard
import com.example.ui.components.VideoInfoDialog
import com.example.ui.components.VideoItemCard
import com.example.ui.components.VideoThumbnail
import com.example.ui.theme.LocalHiPalette
import com.example.ui.theme.LocalHiUiMetrics
import com.example.ui.components.HiPlayerHeader
import com.example.viewmodel.LibraryNavMode
import com.example.viewmodel.LibraryViewMode
import com.example.viewmodel.LibraryViewModel
import com.example.viewmodel.VideoSortOption
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    libraryViewModel: LibraryViewModel,
    onVideoSelected: (VideoItem) -> Unit,
    onPlayFromStart: (VideoItem) -> Unit,
    onOpenSettings: () -> Unit,
    onMoveVideosToRecycleBin: (Collection<VideoItem>) -> Unit = {},
    modifier: Modifier = Modifier,
    includeHeader: Boolean = true,
    onSearchRequested: (() -> Unit)? = null,
    onPlaybackQueueSelected: (List<VideoItem>) -> Unit = {},
    onPlayAsAudio: (VideoItem) -> Unit = {},
) {
    val activeMode by libraryViewModel.navMode.collectAsStateWithLifecycle()
    val selectedFolder by libraryViewModel.selectedFolder.collectAsStateWithLifecycle()
    val treePath by libraryViewModel.treeCurrentPath.collectAsStateWithLifecycle()
    val modes = remember { listOf(LibraryNavMode.FOLDERS, LibraryNavMode.ALL_VIDEOS,
        LibraryNavMode.RECENT, LibraryNavMode.TREE_FOLDERS, LibraryNavMode.FAVORITES) }
    val pager = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = modes.indexOf(activeMode).coerceAtLeast(0), pageCount = { modes.size })
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var selectionActive by remember { mutableStateOf(false) }
    // Warm the lightweight filtered snapshots, not players/WebViews/storage scans.
    remember(libraryViewModel) { modes.forEach { libraryViewModel.videosForMode(it) }; true }
    LaunchedEffect(pager.settledPage) {
        val mode = modes[pager.settledPage]
        if (libraryViewModel.navMode.value != mode) libraryViewModel.setNavMode(mode)
    }
    androidx.compose.foundation.pager.HorizontalPager(
        state = pager,
        key = { modes[it].name },
        beyondViewportPageCount = 2,
        flingBehavior = androidx.compose.foundation.pager.PagerDefaults.flingBehavior(pager,
            snapAnimationSpec = androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)),
        userScrollEnabled = !selectionActive && selectedFolder == null && treePath == null,
        modifier = modifier.fillMaxSize().testTag("video_library_pager"),
    ) { page ->
        VideoLibraryPage(
            libraryViewModel, onVideoSelected, onPlayFromStart, onOpenSettings,
            onMoveVideosToRecycleBin, Modifier.fillMaxSize(), includeHeader, onSearchRequested,
            pageMode = modes[page], isActive = pager.settledPage == page,
            onModeSelected = { mode ->
                scope.launch { pager.animateScrollToPage(modes.indexOf(mode).coerceAtLeast(0),
                    animationSpec = androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
            },
            onSelectionChanged = { if (pager.settledPage == page) selectionActive = it },
            onPlaybackQueueSelected = onPlaybackQueueSelected,
            onPlayAsAudio = onPlayAsAudio,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoLibraryPage(
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
    onModeSelected: (LibraryNavMode) -> Unit,
    onSelectionChanged: (Boolean) -> Unit,
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
    val mediaAccess = com.example.ui.components.rememberLibraryMediaAccess(audio = false) { libraryViewModel.refreshVideos() }
    val canShowLibrary = mediaAccess.granted && !(mediaAccess.limited && scanStatus == com.example.ui.components.LibraryScanStatus.SCANNING)
    val allVideos = if (canShowLibrary) availableVideos else emptyList()
    val folders = if (canShowLibrary) availableFolders else emptyList()
    val swipeTip = com.example.ui.components.rememberFirstUseTip("video_library_swipe_v1")
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
        enabled = viewMode == LibraryViewMode.GRADLE_GRID
    )
    val treeCurrentPath by libraryViewModel.treeCurrentPath.collectAsStateWithLifecycle()
    val currentTreeNode by libraryViewModel.currentTreeNode.collectAsStateWithLifecycle()

    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var showFolders by rememberSaveable { mutableStateOf(false) }
    var inspectVideo by remember { mutableStateOf<VideoItem?>(null) }
    var selectedVideoPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isVideoSelectionMode by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<VideoItem?>(null) }
    var renameText by rememberSaveable { mutableStateOf("") }
    // Overflow-menu deletion is independent from long-press multi-select.
    // A one-item delete must never turn on the library action bar.
    var pendingDeleteVideos by remember { mutableStateOf<List<VideoItem>?>(null) }
    var showSelectionProperties by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val selectionMode = isVideoSelectionMode
    androidx.compose.runtime.SideEffect { if (isActive) onSelectionChanged(selectionMode || inspectVideo != null || renameTarget != null || pendingDeleteVideos != null || showSelectionProperties) }
    // Imported, SAF, and growing downloads can share a MediaStore ID of zero.
    // Use their path/URI as the key so Compose never reuses a visible row's
    // thumbnail or overflow state when the library list is scrolled.
    fun videoKey(video: VideoItem): String = video.path.ifBlank {
        video.uri.toString().takeIf { it.isNotBlank() && it != Uri.EMPTY.toString() }
            ?: "video:${video.id}:${video.title}"
    }
    val selectedVideos = remember(selectedVideoPaths, allVideos) {
        allVideos.filter { selectedVideoPaths.contains(it.path.ifBlank { it.uri.toString() }) }
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
    val libraryModeRowState = rememberLazyListState()
    val searchFocusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearchActive, isActive) {
        if (isSearchActive && isActive) searchFocusRequester.requestFocus()
    }
    LaunchedEffect(navMode) {
        val selectedModeIndex = when (navMode) {
            LibraryNavMode.FOLDERS, LibraryNavMode.ALL_FOLDERS -> 0
            LibraryNavMode.ALL_VIDEOS -> 1
            LibraryNavMode.RECENT -> 2
            LibraryNavMode.TREE_FOLDERS -> 3
            LibraryNavMode.FAVORITES -> 4
        }
        libraryModeRowState.animateScrollToItem(selectedModeIndex)
    }
    fun toggleVideoSelection(video: VideoItem) {
        val key = videoKey(video)
        selectedVideoPaths = if (key in selectedVideoPaths) selectedVideoPaths - key else selectedVideoPaths + key
        isVideoSelectionMode = true
    }

    fun exitVideoSelectionMode() {
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
    val favoriteCount = remember(allVideos) {
        allVideos.count { it.isFavorite }
    }

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
            isSearchActive = false
            libraryViewModel.setSearchQuery("")
        }
    }
    val selectionCandidates = if (navMode == LibraryNavMode.TREE_FOLDERS) currentTreeNode?.directVideos.orEmpty() else filteredVideos
    LaunchedEffect(selectedFolder, treeCurrentPath, navMode) { exitVideoSelectionMode() }
    val videoSelectionActions: @Composable () -> Unit = {
        com.example.ui.components.InlineSelectionToolbar(
            count = selectedVideos.size,
            onClose = { exitVideoSelectionMode() },
            onSelectAll = { selectedVideoPaths = selectionCandidates.map(::videoKey).toSet() },
            onClear = { selectedVideoPaths = emptySet() },
            onProperties = { showSelectionProperties = true },
            onDelete = { pendingDeleteVideos = selectedVideos },
        )
    }

    // These controls must be a scrollable content item, not a sibling above a
    // Lazy list. Keeping them outside the list made the folder name and search
    // look permanently fixed; removing them on scroll made the viewport jump.
    val libraryControls: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (isVideoHome) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.surface)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clickable { isSearchActive = true }
                                .testTag("search_video_card"),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = palette.surfaceElevated),
                            border = BorderStroke(1.dp, palette.surfaceBorder),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            if (isSearchActive) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { libraryViewModel.setSearchQuery(it) },
                                    placeholder = {
                                        Text("Search video here", color = palette.textSecondary, fontSize = 13.sp)
                                    },
                                    singleLine = true,
                                    leadingIcon = {
                                        Icon(Icons.Default.Search, contentDescription = null, tint = palette.textSecondary)
                                    },
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            libraryViewModel.setSearchQuery("")
                                            isSearchActive = false
                                        }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Close search", tint = palette.textSecondary)
                                        }
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = palette.textPrimary,
                                        unfocusedTextColor = palette.textPrimary,
                                        focusedBorderColor = Color.Transparent,
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(searchFocusRequester)
                                )
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 56.dp)
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = null, tint = palette.textSecondary)
                                    Text("Search video here", color = palette.textSecondary, fontSize = 14.sp)
                                }
                            }
                        }
                }
            }
            // Keep these controls mounted while the media list scrolls. Removing
            // a toolbar above a Lazy list changes its viewport height mid-gesture
            // and was the source of the visible jump in the recording.
            if (isVideoHome && searchQuery.isBlank()) {
                val navOptions = listOf(
                    Triple(LibraryNavMode.FOLDERS, "Folders (${folders.size})", Icons.Default.Folder),
                    Triple(LibraryNavMode.ALL_VIDEOS, "Videos (${allVideos.size})", Icons.Default.VideoLibrary),
                    Triple(LibraryNavMode.RECENT, "Recent (${continueWatchingVideos.size})", Icons.Default.Refresh),
                    Triple(LibraryNavMode.TREE_FOLDERS, "Tree Folders (${folders.size})", Icons.Default.FolderOpen),
                    Triple(LibraryNavMode.FAVORITES, "Favorites ($favoriteCount)", Icons.Default.Star)
                )
                com.example.ui.components.LibraryToolbarRow(
                    categories = { categoryModifier ->
                    LazyRow(
                        state = libraryModeRowState,
                        modifier = categoryModifier,
                        contentPadding = PaddingValues(end = 4.dp, top = 4.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        userScrollEnabled = true
                    ) {
                        items(navOptions, key = { it.first.name }) { option ->
                            val (mode, label, icon) = option
                            val selected = navMode == mode
                            Row(
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .clickable {
                                        libraryViewModel.selectFolder(null)
                                        onModeSelected(mode)
                                    }
                                    .padding(vertical = 6.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) palette.primary else palette.surfaceElevated)
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(icon, contentDescription = label, tint = if (selected) com.example.ui.theme.contrastContentColor(palette.primary, palette.surface) else palette.textSecondary, modifier = Modifier.size(14.dp))
                                Text(label, color = if (selected) com.example.ui.theme.contrastContentColor(palette.primary, palette.surface) else palette.textPrimary, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                            }
                        }
                    }
                    },
                    actions = {
                        IconButton(onClick = { libraryViewModel.toggleViewMode() }, modifier = Modifier.size(48.dp)) {
                            Icon(if (viewMode == LibraryViewMode.LAYER_LIST) Icons.Default.GridView else Icons.Default.ViewList, "Toggle view", tint = palette.textSecondary, modifier = Modifier.size(18.dp))
                        }
                        Box {
                            IconButton(onClick = { sortMenuExpanded = true }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Default.Sort, "Sort videos", tint = palette.textSecondary, modifier = Modifier.size(18.dp))
                            }
                            DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }, modifier = Modifier.background(palette.surfaceElevated)) {
                                VideoSortOption.entries.forEach { option ->
                                    DropdownMenuItem(text = { Text(option.displayName, color = palette.textPrimary, fontSize = 11.sp) }, onClick = { sortMenuExpanded = false; libraryViewModel.setSortOption(option) })
                                }
                            }
                        }
                    },
                )
            }

            if (selectedFolder != null || navMode != LibraryNavMode.FOLDERS || selectionMode) {
                Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.CenterStart) {
                    if (selectionMode) videoSelectionActions() else {
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
                        } else if (navMode != LibraryNavMode.FOLDERS) {
                            // Status & Toolbar when in main view
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(palette.primary)
                                    )
                                    Text(
                                        text = when (navMode) {
                                            LibraryNavMode.FOLDERS -> "Folders Directory (${folders.size} folders)"
                                            LibraryNavMode.FAVORITES -> "Favorites (${filteredVideos.size} files)"
                                            LibraryNavMode.RECENT -> "Recent History (${continueWatchingVideos.size} files)"
                                            LibraryNavMode.TREE_FOLDERS -> {
                                                val label = treeCurrentPath?.substringAfterLast('/') ?: "Storage"
                                                val count = currentTreeNode?.totalVideoCount ?: 0
                                                "$label ($count videos)"
                                            }
                                            else -> "All Media (${filteredVideos.size} files)"
                                        },
                                        color = palette.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp
                                    )
                                }

                                // Tree navigation keeps only its back button here; All Videos and
                                // Tree Folders use the single control row above, avoiding duplicates.
                                if (false) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        // Up-one-level button while browsing inside the folder tree
                                        if (navMode == LibraryNavMode.TREE_FOLDERS && treeCurrentPath != null) {
                                            IconButton(
                                                onClick = { libraryViewModel.navigateTreeUp() },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                    contentDescription = "Up one folder",
                                                    tint = palette.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }

                                        IconButton(
                                            onClick = { libraryViewModel.toggleViewMode() },
                                            modifier = Modifier
                                                .size(32.dp)
                                                .testTag("view_mode_toggle")
                                        ) {
                                            Icon(
                                                imageVector = if (viewMode == LibraryViewMode.LAYER_LIST) Icons.Default.GridView else Icons.Default.ViewList,
                                                contentDescription = if (viewMode == LibraryViewMode.LAYER_LIST) "Switch to Grid" else "Switch to List",
                                                tint = palette.textSecondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        Box {
                                            IconButton(
                                                onClick = { sortMenuExpanded = true },
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .testTag("sort_by_button")
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
                                                Text(
                                                    text = "SORT MEDIA BY",
                                                    color = palette.primary,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                                )

                                                VideoSortOption.entries.forEach { option ->
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text(
                                                                text = option.displayName,
                                                                color = if (sortOption == option) palette.primary else palette.textPrimary,
                                                                fontWeight = if (sortOption == option) FontWeight.Bold else FontWeight.Normal,
                                                                fontSize = 12.5.sp
                                                            )
                                                        },
                                                        trailingIcon = {
                                                            if (sortOption == option) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(8.dp)
                                                                        .clip(CircleShape)
                                                                        .background(palette.primary)
                                                                )
                                                            }
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
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
                                isSelected = selectedFolder == folder.name,
                                onClick = { libraryViewModel.selectFolder(folder.name) }
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
                                        isSelected = selectedFolder == folder.name,
                                onClick = {
                                    libraryViewModel.selectFolder(folder.name)
                                }
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
                            item {
                                Text(
                                    text = "SUBFOLDERS",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.primary,
                                    letterSpacing = 0.5.sp,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                                )
                            }
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
                                                    onClick = { libraryViewModel.navigateTreeInto(child.dirPath) }
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
                                        onClick = { libraryViewModel.navigateTreeInto(child.dirPath) }
                                    )
                                }
                            }
                        }

                        if (node.directVideos.isNotEmpty()) {
                            item {
                                Text(
                                    text = "VIDEOS HERE",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.primary,
                                    letterSpacing = 0.5.sp,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp)
                                )
                            }
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
            text = { Text("This moves ${videosToDelete.size} file${if (videosToDelete.size == 1) "" else "s"} to Recycle Bin. You can restore them or permanently delete them later.") },
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

private fun formatVideoBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> String.format("%.2f GB", bytes / 1_000_000_000.0)
    bytes >= 1_000_000L -> String.format("%.2f MB", bytes / 1_000_000.0)
    bytes >= 1_000L -> String.format("%.2f KB", bytes / 1_000.0)
    else -> "$bytes B"
}

@Composable
private fun PlanSheetChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    palette: com.example.ui.theme.HiThemePalette,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) palette.primary else palette.surfaceElevated)
            .border(
                width = 1.dp,
                color = if (isSelected) palette.primary else palette.surfaceBorder,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) {
                    if (palette.isDark) Color.Black else Color.White
                } else palette.textSecondary,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = text,
                fontSize = 11.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) {
                    if (palette.isDark) Color.Black else Color.White
                } else palette.textPrimary
            )
        }
    }
}

@Composable
private fun EmptyState(onBrowse: () -> Unit, onRefresh: () -> Unit = {}) {
    val palette = LocalHiPalette.current
    val uiMetrics = LocalHiUiMetrics.current
    val emptyContext = androidx.compose.ui.platform.LocalContext.current
    val videoPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        android.Manifest.permission.READ_MEDIA_VIDEO
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var hasVideoAccess by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(emptyContext, videoPermission) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    var hasSelectedVideoAccess by remember {
        mutableStateOf(Build.VERSION.SDK_INT >= 34 && androidx.core.content.ContextCompat.checkSelfPermission(emptyContext, android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    }
    val accessOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(accessOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasVideoAccess = androidx.core.content.ContextCompat.checkSelfPermission(emptyContext, videoPermission) == android.content.pm.PackageManager.PERMISSION_GRANTED
                hasSelectedVideoAccess = Build.VERSION.SDK_INT >= 34 && androidx.core.content.ContextCompat.checkSelfPermission(emptyContext, android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        }
        accessOwner.lifecycle.addObserver(observer)
        onDispose { accessOwner.lifecycle.removeObserver(observer) }
    }
    val videoAccessLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasVideoAccess = grants[videoPermission] == true
        hasSelectedVideoAccess = Build.VERSION.SDK_INT >= 34 && grants[android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED] == true
        if (hasVideoAccess || hasSelectedVideoAccess) onRefresh()
        else runCatching {
            emptyContext.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", emptyContext.packageName, null)))
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(palette.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    tint = palette.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (hasVideoAccess) "No videos found" else if (hasSelectedVideoAccess) "Only selected videos are available" else "Allow access to your videos",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (hasVideoAccess) "Tap below to pick a video file from your device storage." else if (hasSelectedVideoAccess) "Allow all videos or select more videos to show them in the library." else "Hi Player needs permission to show the videos on your phone.",
                fontSize = 12.sp,
                color = palette.textSecondary,
                modifier = Modifier.padding(horizontal = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = { if (hasVideoAccess) onBrowse() else videoAccessLauncher.launch(if (Build.VERSION.SDK_INT >= 34) arrayOf(videoPermission, android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) else arrayOf(videoPermission)) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.primary,
                    contentColor = if (palette.isDark) Color.Black else Color.White
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(if (hasVideoAccess) "Browse Storage" else "Allow access", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun ContinueWatchingCard(
    item: LibraryViewModel.ContinueWatchingItem,
    onClick: () -> Unit,
    onRemove: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalHiPalette.current
    val uiMetrics = LocalHiUiMetrics.current

    Card(
        modifier = modifier
            .width(150.dp)
            .clickable(onClick = onClick)
            .testTag("continue_watching_item_${item.video.id}"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surfaceElevated),
        border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.LocalHiPalette.current.surfaceBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            // Thumbnail with Play Icon overlay and Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)).hiContainerBorder(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                    .background(palette.surface)
            ) {
                VideoThumbnail(
                    video = item.video,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp),
                    contentScale = ContentScale.Crop
                )

                // Dark gradient shade overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0x99000000))
                            )
                        )
                )

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.align(Alignment.TopEnd).size(28.dp)
                ) {
                    Icon(Icons.Default.Clear, contentDescription = "Remove from Continue Watching", tint = Color.White)
                }

                // Play Button in Center
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(palette.primary.copy(alpha = 0.9f))
                        .align(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Resume",
                        tint = if (palette.isDark) Color.Black else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Progress Bar at Bottom of Thumbnail
                if (item.progressFraction > 0f) {
                    LinearProgressIndicator(
                        progress = { item.progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .align(Alignment.BottomCenter),
                        color = palette.primary,
                        trackColor = Color(0x66FFFFFF)
                    )
                }
            }

            // Info text
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text(
                    text = item.video.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.5.sp,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.progressText,
                    fontSize = 9.5.sp,
                    color = palette.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}


@Composable
fun ContinueWatchingStrip(
    items: List<LibraryViewModel.ContinueWatchingItem>,
    onVideoSelected: (VideoItem) -> Unit,
    onRemove: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalHiPalette.current
    val uiMetrics = LocalHiUiMetrics.current
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CONTINUE WATCHING",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = palette.primary,
                letterSpacing = 0.5.sp
            )
            Text(
                text = "${items.size} active",
                fontSize = 10.sp,
                color = palette.textSecondary
            )
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(items, key = { it.video.uri.toString() }) { item ->
                ContinueWatchingCard(
                    item = item,
                    onClick = { onVideoSelected(item.video) },
                    onRemove = { onRemove(item.video.uri.toString()) }
                )
            }
        }
    }
}
