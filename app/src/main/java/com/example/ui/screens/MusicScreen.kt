package com.example.ui.screens

import com.example.ui.components.AudioLyricsPanel
import com.example.ui.theme.hiContainerBorder

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Subtitles
import com.example.ui.components.WavyAudioWaveform
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.model.AudioItem
import com.example.model.VideoItem
import com.example.ui.components.AudioTrackThumbnail
import com.example.ui.components.reorderableDrag
import com.example.ui.theme.LocalHiPalette
import com.example.viewmodel.LibraryViewModel
import com.example.viewmodel.MusicViewModel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen(
    musicViewModel: MusicViewModel,
    onPlayVideo: (android.net.Uri) -> Unit = {},
    modifier: Modifier = Modifier,
    includeHeader: Boolean = true,
    onSearchRequested: (() -> Unit)? = null,
    libraryViewModel: LibraryViewModel? = null,
    onMoveAudioToRecycleBin: (AudioItem) -> Unit = {},
) {
    val context = LocalContext.current
    val palette = LocalHiPalette.current
    val availableAudio by musicViewModel.audioList.collectAsStateWithLifecycle()
    val scanStatus by musicViewModel.scanStatus.collectAsStateWithLifecycle()
    val showScanFeedback by musicViewModel.showScanFeedback.collectAsStateWithLifecycle()
    val mediaAccess = com.example.ui.components.rememberLibraryMediaAccess(audio = true) { musicViewModel.loadAudioTracks(showFeedback = true) }
    val audioList = if (mediaAccess.granted) availableAudio else emptyList()
    val swipeTip = com.example.ui.components.rememberFirstUseTip("music_library_swipe_v1")
    val currentTrack by musicViewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by musicViewModel.isPlaying.collectAsStateWithLifecycle()
    val searchQuery by musicViewModel.searchQuery.collectAsStateWithLifecycle()
    val continueWatchingVideos by (libraryViewModel?.continueWatchingVideos
        ?: flowOf(emptyList<LibraryViewModel.ContinueWatchingItem>()))
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val equalizerState by musicViewModel.equalizerState.collectAsStateWithLifecycle()
    val expandedPlayer by musicViewModel.isFullScreenPlayerOpen.collectAsStateWithLifecycle()
    val lyricsEnabled by musicViewModel.lyricsEnabled.collectAsStateWithLifecycle()

    var showEqDialog by remember { mutableStateOf(false) }
    val toggleLyrics: () -> Unit = {
        val enabled = !lyricsEnabled
        musicViewModel.setLyricsEnabled(enabled)
    }
    val modes = remember { listOf("All Music", "Folders", "Playlists", "Albums", "Artists") }
    val pager = androidx.compose.foundation.pager.rememberPagerState(pageCount = { modes.size })
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val libraryMode = modes[pager.settledPage]
    var pendingDeleteTrack by remember { mutableStateOf<AudioItem?>(null) }
    val musicPreferences = remember {
        context.getSharedPreferences("music_preferences", android.content.Context.MODE_PRIVATE)
    }
    var compactAudioView by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(musicPreferences.getBoolean("horizontal_view", false))
    }
    var audioGridMinSize by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(musicPreferences.getInt("grid_min_size_dp", 160).coerceIn(120, 240))
    }
    var audioSort by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("Name") }
    val reorderThresholdPx = with(LocalDensity.current) { 96.dp.toPx() }

    val audioGridTransformState = rememberTransformableState { zoomChange, _, _ ->
        if (compactAudioView && zoomChange.isFinite() && kotlin.math.abs(zoomChange - 1f) > 0.005f) {
            val next = (audioGridMinSize / zoomChange).roundToInt().coerceIn(120, 240)
            audioGridMinSize = next
            musicPreferences.edit().putInt("grid_min_size_dp", next).apply()
        }
    }
    val audioGridGestureModifier = Modifier.transformable(
        state = audioGridTransformState,
        enabled = compactAudioView
    )

    val presentations by androidx.compose.runtime.produceState<Map<String, AudioLibraryPresentation>>(
        emptyMap(), audioList, searchQuery, audioSort,
    ) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            modes.associateWith { mode ->
                buildAudioLibraryPresentation(audioList, searchQuery, audioSort, mode, null)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        val audioToolbar: @Composable () -> Unit = {
            MusicLibraryToolbar(
                selected = libraryMode,
                onCategory = { category -> scope.launch { pager.animateScrollToPage(modes.indexOf(category),
                    animationSpec = androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)) } },
                grid = compactAudioView,
                onGridChange = {
                    compactAudioView = it
                    musicPreferences.edit().putBoolean("horizontal_view", it).apply()
                },
                sort = audioSort,
                onSort = { audioSort = it },
            )

        }

        if (currentTrack == null || !expandedPlayer) {
        // Keep the toolbar outside the list so it remains available when a
        // library mode has no matching tracks (for example an empty playlist).
        audioToolbar()
        if (mediaAccess.granted) {
                com.example.ui.components.LibraryAccessExplanation(audio = true)
            com.example.ui.components.FirstUseTip(swipeTip, "Swipe to explore your music",
                "Swipe left or right across the library to move between Songs, Folders, Playlists, Albums and Artists. Tap a tab to jump straight there.",
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp))
            // Background refreshes must not add a banner above the current library.
        }

        // Track List
        androidx.compose.foundation.pager.HorizontalPager(
            state = pager, key = { modes[it] }, beyondViewportPageCount = 2,
            flingBehavior = androidx.compose.foundation.pager.PagerDefaults.flingBehavior(pager,
                snapAnimationSpec = androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)),
            userScrollEnabled = pendingDeleteTrack == null,
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("music_library_pager"),
        ) { page ->
            val pageMode = modes[page]
            var selectedCollection by androidx.compose.runtime.saveable.rememberSaveable(pageMode) {
                mutableStateOf<String?>(null)
            }
            androidx.activity.compose.BackHandler(enabled = pager.settledPage == page && selectedCollection != null) {
                selectedCollection = null
            }
            val basePresentation = presentations[pageMode]
            // Root snapshots already contain sorted group members; opening a
            // collection is a map lookup, not another scan/filter/loading pass.
            val collection = selectedCollection
            val presentation = if (collection == null) basePresentation else
                basePresentation?.let { AudioLibraryPresentation(
                    tracks = it.groups[collection].orEmpty(), groups = emptyMap()) }
            val trackListState = androidx.compose.foundation.lazy.rememberLazyListState()
            val trackGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
            val collectionGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
            val filteredList = presentation?.tracks.orEmpty()
            val groupedAudio = presentation?.groups.orEmpty()
            val playbackQueue = if (groupedAudio.isNotEmpty()) groupedAudio.values.flatten() else filteredList
            Column(Modifier.fillMaxSize()) {
            if (selectedCollection != null) {
                TextButton(onClick = { selectedCollection = null }) {
                    Text("‹ All $pageMode", color = palette.textPrimary)
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
            if (!mediaAccess.granted) {
                com.example.ui.components.LibraryPermissionPanel(audio = true, onOpenSettings = mediaAccess.openSettings, error = mediaAccess.error)
            } else if (showScanFeedback && scanStatus == com.example.ui.components.LibraryScanStatus.SCANNING && audioList.isEmpty()) {
                com.example.ui.components.LibraryScanFeedback(scanStatus, 0, audio = true, centered = true)
            } else if (presentation == null) {
                com.example.ui.components.ContentSkeleton(label = "Preparing music…",
                    layout = com.example.ui.components.SkeletonLayout.MEDIA_LIST)
            } else if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = palette.textTertiary.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = when (pageMode) {
                                "Playlists" -> "No playlist tracks yet"
                                "Folders" -> "No folder tracks found"
                                "Albums" -> "No album tracks found"
                                "Artists" -> "No artist tracks found"
                                else -> "No audio tracks found"
                            },
                            color = palette.textSecondary,
                            fontSize = 14.sp
                        )
                        androidx.compose.material3.TextButton(onClick = {
                            musicViewModel.setSearchQuery("")
                            scope.launch { pager.animateScrollToPage(0) }
                            selectedCollection = null
                            musicViewModel.loadAudioTracks(showFeedback = true)
                        }) { Text(if (audioList.isEmpty()) "Scan music" else "Show all music") }
                    }
                }
            } else if (groupedAudio.isNotEmpty() && selectedCollection == null) {
                // Artists, albums, and folders are collections, not simply a
                // flat track list sorted by one field. Open a collection to
                // show only the tracks that belong to it.
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 148.dp),
                    state = collectionGridState,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    gridItems(groupedAudio.entries.toList(), key = { entry -> "audio_collection_${entry.key}" }) { entry ->
                        Card(
                            border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.LocalHiPalette.current.surfaceBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedCollection = entry.key },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = palette.surfaceElevated),
                            elevation = CardDefaults.cardElevation(0.dp),
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Icon(
                                    imageVector = when (pageMode) {
                                        "Artists" -> Icons.Default.Headphones
                                        "Albums" -> Icons.Default.Audiotrack
                                        else -> Icons.Default.MusicNote
                                    },
                                    contentDescription = null,
                                    tint = palette.primary,
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = entry.key,
                                    color = palette.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "${entry.value.size} ${if (entry.value.size == 1) "track" else "tracks"}",
                                    color = palette.textSecondary,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
            } else {
                if (compactAudioView) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = audioGridMinSize.dp),
                        state = trackGridState,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize().then(audioGridGestureModifier)
                    ) {
                        if (groupedAudio.isNotEmpty()) {
                            groupedAudio.forEach { (groupName, tracks) ->
                                item(key = "audio_grid_group_$groupName", span = { GridItemSpan(maxLineSpan) }) {
                                    Text(
                                        text = groupName,
                                        color = palette.primary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                                    )
                                }
                                gridItems(tracks, key = { track -> "audio_grid_${groupName}_${audioLibraryKey(track)}" }, contentType = { "audio_track" }) { track ->
                                    val isSelected = currentTrack?.uri == track.uri
                                    AudioTrackCard(
                                        track = track,
                                        isSelected = isSelected,
                                        isPlaying = isSelected && isPlaying,
                                        onDelete = { pendingDeleteTrack = track },
                                    onClick = { musicViewModel.playTrack(track, playbackQueue) },
                                        modifier = Modifier.padding(4.dp),
                                    )
                                }
                            }
                        } else {
                            gridItemsIndexed(filteredList, key = { _, track -> audioLibraryKey(track) }, contentType = { _, _ -> "audio_track" }) { index, track ->
                                val isSelected = currentTrack?.uri == track.uri
                                AudioTrackCard(
                                    track = track,
                                    isSelected = isSelected,
                                    isPlaying = isSelected && isPlaying,
                                    onDelete = { pendingDeleteTrack = track },
                                    onClick = { musicViewModel.playTrack(track, playbackQueue) },
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .reorderableDrag(
                                            itemIndex = index,
                                            itemCount = filteredList.size,
                                            thresholdPx = reorderThresholdPx,
                                            onMove = { from, to ->
                                                audioSort = "Custom"
                                                musicViewModel.moveTrackInDisplayOrder(
                                                    filteredList[from].path.ifBlank { filteredList[from].uri.toString() },
                                                    filteredList[to].path.ifBlank { filteredList[to].uri.toString() }
                                                )
                                            }
                                        )
                                )
                            }
                        }
                    }
                } else if (groupedAudio.isNotEmpty()) {
                    LazyColumn(
                        state = trackListState,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        groupedAudio.forEach { (groupName, tracks) ->
                            item(key = "audio_group_$groupName") {
                                Text(
                                    text = groupName,
                                    color = palette.primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                                )
                            }
                            items(tracks, key = { track -> "audio_${groupName}_${audioLibraryKey(track)}" }, contentType = { "audio_track" }) { track ->
                                val isSelected = currentTrack?.uri == track.uri
                                AudioTrackCard(
                                    track = track,
                                    isSelected = isSelected,
                                    isPlaying = isSelected && isPlaying,
                                    onDelete = { pendingDeleteTrack = track },
                                    onClick = { musicViewModel.playTrack(track, playbackQueue) }
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = trackListState,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(filteredList, key = { _, track -> audioLibraryKey(track) }, contentType = { _, _ -> "audio_track" }) { index, track ->
                            val isSelected = currentTrack?.uri == track.uri
                            AudioTrackCard(
                                track = track,
                                isSelected = isSelected,
                                isPlaying = isSelected && isPlaying,
                                onDelete = { pendingDeleteTrack = track },
                                onClick = { musicViewModel.playTrack(track, playbackQueue) },
                                modifier = Modifier.reorderableDrag(
                                    itemIndex = index,
                                    itemCount = filteredList.size,
                                    thresholdPx = reorderThresholdPx,
                                    onMove = { from, to ->
                                        audioSort = "Custom"
                                        musicViewModel.moveTrackInDisplayOrder(
                                            filteredList[from].path.ifBlank { filteredList[from].uri.toString() },
                                            filteredList[to].path.ifBlank { filteredList[to].uri.toString() }
                                        )
                                    }
                                )
                            )
                        }
                    }
                }
            }
            }
            }
        }

        }
        MusicPlaybackSurface(
            musicViewModel = musicViewModel,
            onOpenEq = { showEqDialog = true },
            toggleLyrics = toggleLyrics,
        )
    }

    pendingDeleteTrack?.let { track ->
        AlertDialog(
            onDismissRequest = { pendingDeleteTrack = null },
            title = { Text("Move audio to Recycle Bin?") },
            text = { Text("This removes the track from your library. You can restore the file from Recycle Bin in Files.") },
            confirmButton = { TextButton(onClick = {
                onMoveAudioToRecycleBin(track)
                pendingDeleteTrack = null
            }) { Text("Move to Recycle Bin") } },
            dismissButton = { TextButton(onClick = { pendingDeleteTrack = null }) { Text("Cancel") } },
        )
    }

    if (showEqDialog) {
        com.example.ui.components.MusicEqualizerDialog(
            state = equalizerState,
            onEnabled = musicViewModel::setEqualizerEnabled,
            onPreset = musicViewModel::setEqualizerPreset,
            onBand = musicViewModel::setEqualizerBand,
            onBass = musicViewModel::setEqualizerBass,
            onReset = musicViewModel::resetEqualizer,
            onDismiss = { showEqDialog = false },
        )
    }

}

@Composable
fun AudioTrackCard(
    track: AudioItem,
    isSelected: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
) {
    val palette = LocalHiPalette.current
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (isSelected) Modifier.hiContainerBorder(RoundedCornerShape(12.dp), selected = true) else Modifier)
            .background(if (isSelected) palette.primary.copy(alpha = 0.08f) else palette.surfaceElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .testTag("audio_track_${track.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Audio Track Thumbnail
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isSelected) palette.primary.copy(alpha = 0.25f)
                    else palette.surfaceElevated
                ),
            contentAlignment = Alignment.Center
        ) {
            AudioTrackThumbnail(
                track = track,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(8.dp)
            )

            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Playing",
                        tint = palette.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp,
                color = if (isSelected) palette.primary else palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.artist,
                    modifier = Modifier.weight(1f),
                    fontSize = 11.5.sp,
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "•", fontSize = 10.sp, color = palette.textSecondary)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = track.album,
                    modifier = Modifier.weight(1f),
                    fontSize = 11.sp,
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (onDelete != null) Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Default.MoreVert, "Audio options", tint = palette.textSecondary)
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(text = { Text("Move to Recycle Bin") }, onClick = {
                    menuExpanded = false
                    onDelete()
                })
            }
        }

        Column(modifier = Modifier.widthIn(max = 80.dp), horizontalAlignment = Alignment.End) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp)).hiContainerBorder(RoundedCornerShape(4.dp))
                    .background(palette.primary.copy(alpha = 0.15f))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = track.audioFormat,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 9.sp,
                    color = palette.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = track.durationFormatted,
                fontSize = 11.sp,
                color = palette.textSecondary
            )
        }
    }
}

@Composable
fun WavyNowPlayingBottomSheet(
    track: AudioItem,
    isPlaying: Boolean,
    currentPos: Long,
    duration: Long,
    eqPreset: String,
    activeSubtitle: String?,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onOpenEq: () -> Unit,
    onOpenSubtitleSearch: () -> Unit,
    onLyricsTap: () -> Unit = {},
    onCancel: () -> Unit,
    onExpandFullScreen: () -> Unit = {},
    lyricsEnabled: Boolean = false,
    lyricsText: String? = null,
    activeLyricLineIndex: Int = -1,
    onLyricsToggle: () -> Unit = {},
    modifier: Modifier = Modifier,
    lyricsMessage: String? = null,
    lyricsLoading: Boolean = false,
    lyricsRetryable: Boolean = false,
    onRetryLyrics: () -> Unit = {},
) {
    val palette = LocalHiPalette.current
    val total = duration.takeIf { it > 0 } ?: track.durationMs.coerceAtLeast(0)
    Card(
        modifier = modifier.fillMaxWidth().testTag("now_playing_bottom_sheet"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surfaceElevated),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).testTag("expand_audio_body")
                .clickable(onClickLabel = "Expand music player", onClick = onExpandFullScreen),
                verticalAlignment = Alignment.CenterVertically) {
                AudioTrackThumbnail(track, Modifier.size(44.dp), shape = RoundedCornerShape(10.dp))
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = palette.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = palette.textSecondary, fontSize = 11.sp)
                }
            }
            IconButton(onClick = onTogglePlay) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (isPlaying) "Pause" else "Play", tint = palette.textPrimary)
            }
            IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, "Next Track", tint = palette.textPrimary) }
            IconButton(onClick = onExpandFullScreen, modifier = Modifier.testTag("expand_audio_sheet")) {
                Icon(Icons.Default.KeyboardArrowUp, "Expand music player", tint = palette.textPrimary)
            }
            IconButton(onClick = onCancel, modifier = Modifier.testTag("cancel_audio_sheet")) {
                Icon(Icons.Default.Close, "Cancel Playing", tint = palette.textSecondary)
            }
        }
        LinearProgressIndicator(progress = { if (total > 0) (currentPos.toFloat() / total).coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth().height(2.dp), color = palette.primary,
            trackColor = palette.surfaceBorder, drawStopIndicator = {})
    }
}

@Composable
private fun MusicPlaybackSurface(
    musicViewModel: MusicViewModel,
    onOpenEq: () -> Unit,
    toggleLyrics: () -> Unit,
) {
    val currentTrack by musicViewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by musicViewModel.isPlaying.collectAsStateWithLifecycle()
    val currentPos by musicViewModel.currentPositionMs.collectAsStateWithLifecycle()
    val duration by musicViewModel.durationMs.collectAsStateWithLifecycle()
    val equalizerPreset by musicViewModel.equalizerPreset.collectAsStateWithLifecycle()
    val activeSubtitle by musicViewModel.activeSubtitle.collectAsStateWithLifecycle()
    val lyricsText by musicViewModel.lyricsText.collectAsStateWithLifecycle()
    val lyricsMessage by musicViewModel.lyricsMessage.collectAsStateWithLifecycle()
    val lyricsLoading by musicViewModel.lyricsLoading.collectAsStateWithLifecycle()
    val lyricsRetryable by musicViewModel.lyricsRetryable.collectAsStateWithLifecycle()
    val lyricsEnabled by musicViewModel.lyricsEnabled.collectAsStateWithLifecycle()
    val currentLyricLineIndex by musicViewModel.currentLyricLineIndex.collectAsStateWithLifecycle()
    val isFullScreenPlayerOpen by musicViewModel.isFullScreenPlayerOpen.collectAsStateWithLifecycle()
        // Full Screen Audio Player or Wavy Now Playing Sheet
        if (currentTrack != null) {
            if (isFullScreenPlayerOpen) {
                FullScreenAudioPlayerScreen(
                    track = currentTrack!!,
                    isPlaying = isPlaying,
                    currentPos = currentPos,
                    duration = duration,
                    eqPreset = equalizerPreset,
                    activeSubtitle = activeSubtitle,
                    lyricsEnabled = lyricsEnabled,
                    lyricsText = lyricsText,
                    lyricsMessage = lyricsMessage,
                    lyricsLoading = lyricsLoading,
                    lyricsRetryable = lyricsRetryable,
                    onRetryLyrics = { currentTrack?.let(musicViewModel::fetchAndSyncLyrics) },
                    activeLyricLineIndex = currentLyricLineIndex,
                    onTogglePlay = { musicViewModel.togglePlayPause() },
                    onNext = { musicViewModel.playNext() },
                    onPrevious = { musicViewModel.playPrevious() },
                    onSeek = { musicViewModel.seekTo(it) },
                    onOpenEq = onOpenEq,
                    onOpenSubtitleSearch = toggleLyrics,
                    onBack = { musicViewModel.closeFullScreenPlayer() },
                    onLyricsTap = toggleLyrics,
                    onCancel = { musicViewModel.stopTrack() }
                )
            } else {
                WavyNowPlayingBottomSheet(
                    track = currentTrack!!,
                    isPlaying = isPlaying,
                    currentPos = currentPos,
                    duration = if (duration > 0) duration else currentTrack!!.durationMs,
                    eqPreset = equalizerPreset,
                    activeSubtitle = activeSubtitle,
                    onTogglePlay = { musicViewModel.togglePlayPause() },
                    onNext = { musicViewModel.playNext() },
                    onPrevious = { musicViewModel.playPrevious() },
                    onSeek = { musicViewModel.seekTo(it) },
                    onOpenEq = onOpenEq,
                    onOpenSubtitleSearch = toggleLyrics,
                    onLyricsTap = toggleLyrics,
                    onCancel = { musicViewModel.stopTrack() },
                    onExpandFullScreen = { musicViewModel.openFullScreenPlayer() },
                    lyricsEnabled = lyricsEnabled,
                    lyricsText = lyricsText,
                    lyricsMessage = lyricsMessage,
                    lyricsLoading = lyricsLoading,
                    lyricsRetryable = lyricsRetryable,
                    onRetryLyrics = { currentTrack?.let(musicViewModel::fetchAndSyncLyrics) },
                    activeLyricLineIndex = currentLyricLineIndex,
                    onLyricsToggle = toggleLyrics
                )
            }
        }
}
