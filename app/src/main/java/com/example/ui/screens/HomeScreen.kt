package com.example.ui.screens

import android.net.Uri
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    libraryViewModel: LibraryViewModel,
    onVideoSelected: (VideoItem) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    includeHeader: Boolean = true,
    onSearchRequested: (() -> Unit)? = null
) {
    val allVideos by libraryViewModel.allVideos.collectAsState()
    val filteredVideos by libraryViewModel.filteredVideos.collectAsState()
    val folders by libraryViewModel.folders.collectAsState()
    val navMode by libraryViewModel.navMode.collectAsState()
    val viewMode by libraryViewModel.viewMode.collectAsState()
    val gridMinSize by libraryViewModel.gridMinSize.collectAsState()
    val sortOption by libraryViewModel.sortOption.collectAsState()
    val selectedFolder by libraryViewModel.selectedFolder.collectAsState()
    val searchQuery by libraryViewModel.searchQuery.collectAsState()
    val isLoading by libraryViewModel.isLoading.collectAsState()
    val historyRecords by libraryViewModel.historyRecords.collectAsState()
    val continueWatchingVideos by libraryViewModel.continueWatchingVideos.collectAsState()
    val playerSettings by libraryViewModel.playerSettings.collectAsState()
    val gridTransformState = androidx.compose.foundation.gestures.rememberTransformableState { zoomChange, _, _ ->
        if (viewMode == LibraryViewMode.GRADLE_GRID && zoomChange.isFinite() && kotlin.math.abs(zoomChange - 1f) > 0.005f) {
            libraryViewModel.setGridMinSize((gridMinSize / zoomChange).roundToInt())
        }
    }
    val pinchGridModifier = Modifier.transformable(
        state = gridTransformState,
        enabled = viewMode == LibraryViewMode.GRADLE_GRID
    )
    val treeCurrentPath by libraryViewModel.treeCurrentPath.collectAsState()
    val currentTreeNode by libraryViewModel.currentTreeNode.collectAsState()

    var isSearchActive by remember { mutableStateOf(false) }
    var showFolders by remember { mutableStateOf(false) }
    var inspectVideo by remember { mutableStateOf<VideoItem?>(null) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

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
    val favoriteCount = remember(filteredVideos, allVideos) {
        allVideos.count { it.isFavorite }
    }

    // Back handler to navigate back to folder directory when inside a folder
    BackHandler(enabled = selectedFolder != null) {
        libraryViewModel.selectFolder(null)
    }

    // Back handler for Tree Folders: step up one directory level at a time
    // instead of exiting the tree entirely.
    BackHandler(enabled = navMode == LibraryNavMode.TREE_FOLDERS && treeCurrentPath != null) {
        libraryViewModel.navigateTreeUp()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = palette.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (includeHeader) TopAppBar(
                modifier = Modifier.height(uiMetrics.headerHeight),
                title = {
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { libraryViewModel.setSearchQuery(it) },
                            placeholder = {
                                Text(
                                    "Search plan sheet items, codecs, folders...",
                                    color = palette.textSecondary,
                                    fontSize = 13.sp
                                )
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = palette.textPrimary,
                                unfocusedTextColor = palette.textPrimary,
                                focusedBorderColor = palette.primary,
                                unfocusedBorderColor = palette.surfaceBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("search_text_field"),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { libraryViewModel.setSearchQuery("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = palette.textSecondary)
                                    }
                                }
                            }
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            com.example.ui.components.HiPlayerLogoBadge(
                                size = uiMetrics.logoSize
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Hi Player",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp,
                                    color = palette.textPrimary
                                )
                            }
                        }
                    }
                },
                actions = {
                    // Search Button
                    IconButton(
                        onClick = { onSearchRequested?.invoke() ?: run { isSearchActive = !isSearchActive } },
                        modifier = Modifier.testTag("toggle_search_button")
                    ) {
                        Icon(
                            imageVector = if (isSearchActive) Icons.Default.Clear else Icons.Default.Search,
                            contentDescription = "Search",
                            tint = palette.textPrimary
                        )
                    }

                    // Refresh Button
                    IconButton(
                        onClick = { libraryViewModel.refreshVideos() },
                        modifier = Modifier.testTag("refresh_videos_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = palette.textPrimary
                        )
                    }
                },
                windowInsets = TopAppBarDefaults.windowInsets,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.surface
                )
            )
        },
        floatingActionButton = {}
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // UNIFIED PLAN SHEET HEADER & HORIZONTAL FILTER TABS
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.surface)
            ) {
                // Nav-mode dropdown + view/sort toggle - only shown at the
                // folder root. Previously this stayed visible even after
                // drilling into a folder, stacking on top of the folder's
                // own back/view header below and showing two overlapping
                // sets of view/sort controls at once.
                if (selectedFolder == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Dropdown Navigation
                    var expanded by remember { mutableStateOf(false) }
                    
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.surfaceElevated)
                            .clickable { expanded = true }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = when(navMode) {
                                    LibraryNavMode.FOLDERS -> Icons.Default.Folder
                                    LibraryNavMode.TREE_FOLDERS -> Icons.Default.FolderOpen
                                    LibraryNavMode.FAVORITES -> Icons.Default.Star
                                    else -> Icons.Default.VideoLibrary
                                },
                                contentDescription = null,
                                tint = palette.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = when(navMode) {
                                    LibraryNavMode.FOLDERS -> "All Folders (${folders.size})"
                                    LibraryNavMode.TREE_FOLDERS -> "Tree Folders (${folders.size})"
                                    LibraryNavMode.FAVORITES -> "Favorites (${filteredVideos.count { it.isFavorite }})"
                                    else -> "All Videos (${allVideos.size})"
                                },
                                fontWeight = FontWeight.Bold,
                                color = palette.textPrimary,
                                fontSize = 13.sp
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = palette.textSecondary
                            )
                        }

                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.background(palette.surfaceElevated)
                        ) {
                            val options = listOf(
                                LibraryNavMode.ALL_VIDEOS to ("All Videos (${allVideos.size})" to Icons.Default.VideoLibrary),
                                LibraryNavMode.TREE_FOLDERS to ("Tree Folders (${folders.size})" to Icons.Default.FolderOpen),
                                LibraryNavMode.FOLDERS to ("All Folders (${folders.size})" to Icons.Default.Folder),
                                LibraryNavMode.FAVORITES to ("Favorites (${filteredVideos.count { it.isFavorite }})" to Icons.Default.Star)
                            )
                            options.forEach { (mode, pair) ->
                                val (title, icon) = pair
                                val isSelected = navMode == mode
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                tint = if (isSelected) palette.primary else palette.textSecondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = title,
                                                color = if (isSelected) palette.primary else palette.textPrimary,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    },
                                    onClick = {
                                        expanded = false
                                        libraryViewModel.selectFolder(null)
                                        libraryViewModel.setNavMode(mode)
                                    }
                                )
                            }
                        }
                    }

                    // Right: View Mode Toggle & Sort
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { libraryViewModel.toggleViewMode() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (viewMode == LibraryViewMode.LAYER_LIST) Icons.Default.GridView else Icons.Default.ViewList,
                                contentDescription = "Toggle View",
                                tint = palette.textSecondary
                            )
                        }
                        IconButton(
                            onClick = { sortMenuExpanded = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Sort, contentDescription = "Sort By", tint = palette.textSecondary)
                        }
                        
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false },
                            modifier = Modifier.background(palette.surfaceElevated)
                        ) {
                            VideoSortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.displayName, color = palette.textPrimary) },
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
                            Column {
                                Text(
                                    text = selectedFolder ?: "",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = palette.textPrimary
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

                            IconButton(
                                onClick = { libraryViewModel.selectFolder(null) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Close Folder",
                                    tint = palette.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                } else {
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

            // 4. PLAN SHEET CONTENT CANVAS
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = palette.primary)
                }
            } else if (navMode == LibraryNavMode.FOLDERS && selectedFolder == null) {
                // CLEAN FOLDER SYSTEM VIEW (Matching User's Reference Image)
                if (folders.isEmpty()) {
                    EmptyState(onBrowse = { filePickerLauncher.launch(arrayOf("video/*")) })
                } else if (viewMode == LibraryViewMode.GRADLE_GRID) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = gridMinSize.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize().then(pinchGridModifier)
                    ) {
                        if (!isSearchActive && continueWatchingVideos.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                ContinueWatchingStrip(
                                    items = continueWatchingVideos,
                                    onVideoSelected = onVideoSelected,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }
                        itemsIndexed(folders, key = { _, folder -> folder.name }) { _, folder ->
                            FolderGridCard(
                                name = folder.name,
                                videoCount = folder.videoCount,
                                isSelected = selectedFolder == folder.name,
                                onClick = { libraryViewModel.selectFolder(folder.name) }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        if (!isSearchActive && continueWatchingVideos.isNotEmpty()) {
                            item {
                                ContinueWatchingStrip(
                                    items = continueWatchingVideos,
                                    onVideoSelected = onVideoSelected,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }
                        itemsIndexed(folders, key = { _, folder -> folder.name }) { _, folder ->
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
                    EmptyState(onBrowse = { filePickerLauncher.launch(arrayOf("video/*")) })
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize().then(pinchGridModifier)
                    ) {
                        if (!isSearchActive && continueWatchingVideos.isNotEmpty()) {
                            item {
                                ContinueWatchingStrip(
                                    items = continueWatchingVideos,
                                    onVideoSelected = onVideoSelected,
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
                                            sampleVideo = null
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
                            items(node.directVideos, key = { it.id }) { video ->
                                val record = historyMap[video.uri.toString()]
                                val progressFraction = if (record != null && record.durationMs > 0) {
                                    (record.lastPositionMs.toFloat() / record.durationMs.toFloat()).coerceIn(0f, 1f)
                                } else 0f
                                VideoItemCard(
                                    video = video.copy(isFavorite = record?.isFavorite ?: false),
                                    progressFraction = progressFraction,
                                    onClick = { onVideoSelected(video) },
                                    onFavoriteToggle = { isFav ->
                                        libraryViewModel.toggleFavorite(video, isFav)
                                    },
                                    onPlayInBackground = { onVideoSelected(video) },
                                    onShowDetails = { inspectVideo = video }
                                )
                            }
                        }
                    }
                }
            } else if (filteredVideos.isEmpty()) {
                EmptyState(onBrowse = { filePickerLauncher.launch(arrayOf("video/*")) })
            } else {
                // Unified media items (List or Grid). Continue Watching is kept
                // above both layouts so it never disappears when switching modes.
                if (viewMode == LibraryViewMode.GRADLE_GRID) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = gridMinSize.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize().then(pinchGridModifier)
                    ) {
                        itemsIndexed(filteredVideos, key = { _, video -> video.id }) { _, video ->
                            val record = historyMap[video.uri.toString()]
                            val progressFraction = if (record != null && record.durationMs > 0) {
                                (record.lastPositionMs.toFloat() / record.durationMs.toFloat()).coerceIn(0f, 1f)
                            } else 0f

                            VideoGridCard(
                                video = video.copy(isFavorite = record?.isFavorite ?: false),
                                progressFraction = progressFraction,
                                onClick = { onVideoSelected(video) },
                                onFavoriteToggle = { isFav ->
                                    libraryViewModel.toggleFavorite(video, isFav)
                                },
                                onPlayInBackground = { onVideoSelected(video) },
                                onShowDetails = { inspectVideo = video }
                            )
                        }
                    }
                } else {
                                            // Video rows

                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Video item rows
                        items(filteredVideos, key = { it.id }) { video ->
                            val record = historyMap[video.uri.toString()]
                            val progressFraction = if (record != null && record.durationMs > 0) {
                                (record.lastPositionMs.toFloat() / record.durationMs.toFloat()).coerceIn(0f, 1f)
                            } else 0f

                            VideoItemCard(
                                video = video.copy(isFavorite = record?.isFavorite ?: false),
                                progressFraction = progressFraction,
                                onClick = { onVideoSelected(video) },
                                onFavoriteToggle = { isFav ->
                                    libraryViewModel.toggleFavorite(video, isFav)
                                },
                                onPlayInBackground = { onVideoSelected(video) },
                                onShowDetails = { inspectVideo = video }
                            )
                        }
                    }
                }
            }
        }
    }

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
private fun EmptyState(onBrowse: () -> Unit) {
    val palette = LocalHiPalette.current
    val uiMetrics = LocalHiUiMetrics.current
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
                text = "No Videos in This Plan View",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Tap below to browse and play video files from your device storage.",
                fontSize = 12.sp,
                color = palette.textSecondary,
                modifier = Modifier.padding(horizontal = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = onBrowse,
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.primary,
                    contentColor = if (palette.isDark) Color.Black else Color.White
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Browse Storage", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun ContinueWatchingCard(
    item: LibraryViewModel.ContinueWatchingItem,
    onClick: () -> Unit,
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
        border = null,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            // Thumbnail with Play Icon overlay and Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
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
                    onClick = { onVideoSelected(item.video) }
                )
            }
        }
    }
}

