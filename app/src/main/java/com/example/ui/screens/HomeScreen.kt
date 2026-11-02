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
    val availableVideos by libraryViewModel.allVideos.collectAsStateWithLifecycle()
    val availableFolders by libraryViewModel.folders.collectAsStateWithLifecycle()
    val recent by libraryViewModel.continueWatchingVideos.collectAsStateWithLifecycle()
    val searchQuery by libraryViewModel.searchQuery.collectAsStateWithLifecycle()
    val viewMode by libraryViewModel.viewMode.collectAsStateWithLifecycle()
    val scanStatus by libraryViewModel.scanStatus.collectAsStateWithLifecycle()
    val mediaAccess = com.example.ui.components.rememberLibraryMediaAccess(audio = false) { libraryViewModel.refreshVideos() }
    val canShow = mediaAccess.granted && !(mediaAccess.limited && scanStatus == com.example.ui.components.LibraryScanStatus.SCANNING)
    val visibleVideos = if (canShow) availableVideos else emptyList()
    val visibleFolders = if (canShow) availableFolders else emptyList()
    val favoriteCount = remember(visibleVideos) { visibleVideos.count { it.isFavorite } }
    var selectionToolbarVisible by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxSize()) {
        if (selectedFolder == null && treePath == null && !selectionToolbarVisible) {
            VideoLibraryHeaderControls(
                modes = modes, selected = modes[pager.currentPage], folderCount = visibleFolders.size,
                videoCount = visibleVideos.size, recentCount = if (canShow) recent.size else 0,
                favoriteCount = favoriteCount, query = searchQuery,
                onQueryChange = libraryViewModel::setSearchQuery, viewMode = viewMode,
                onToggleView = libraryViewModel::toggleViewMode, onSort = libraryViewModel::setSortOption,
                onSelect = { mode -> libraryViewModel.selectFolder(null); scope.launch {
                    pager.animateScrollToPage(modes.indexOf(mode).coerceAtLeast(0),
                        animationSpec = androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                } },
            )
        }
    androidx.compose.foundation.pager.HorizontalPager(
        state = pager,
        key = { modes[it].name },
        beyondViewportPageCount = 2,
        flingBehavior = androidx.compose.foundation.pager.PagerDefaults.flingBehavior(pager,
            snapAnimationSpec = androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)),
        userScrollEnabled = !selectionActive && selectedFolder == null && treePath == null,
        modifier = Modifier.weight(1f).fillMaxWidth().testTag("video_library_pager"),
    ) { page ->
        VideoLibraryPage(
            libraryViewModel, onVideoSelected, onPlayFromStart, onOpenSettings,
            onMoveVideosToRecycleBin, Modifier.fillMaxSize(), includeHeader, onSearchRequested,
            pageMode = modes[page], isActive = pager.settledPage == page, mediaAccess = mediaAccess,
            onSelectionChanged = { if (pager.settledPage == page) selectionActive = it },
            onSelectionToolbarChanged = { if (pager.settledPage == page) selectionToolbarVisible = it },
            onPlaybackQueueSelected = onPlaybackQueueSelected,
            onPlayAsAudio = onPlayAsAudio,
        )
    }
    }
}
