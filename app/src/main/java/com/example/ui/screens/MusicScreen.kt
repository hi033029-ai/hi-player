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
    val mediaAccess = com.example.ui.components.rememberLibraryMediaAccess(audio = true) { musicViewModel.loadAudioTracks() }
    val audioList = if (mediaAccess.granted) availableAudio else emptyList()
    val swipeTip = com.example.ui.components.rememberFirstUseTip("music_library_swipe_v1")
    val currentTrack by musicViewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by musicViewModel.isPlaying.collectAsStateWithLifecycle()
    val searchQuery by musicViewModel.searchQuery.collectAsStateWithLifecycle()
    val continueWatchingVideos by (libraryViewModel?.continueWatchingVideos
        ?: flowOf(emptyList<LibraryViewModel.ContinueWatchingItem>()))
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val equalizerPreset by musicViewModel.equalizerPreset.collectAsStateWithLifecycle()
    val equalizerBands by musicViewModel.equalizerBands.collectAsStateWithLifecycle()
    val equalizerStatus by musicViewModel.equalizerStatus.collectAsStateWithLifecycle()
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
                onCategory = { category -> scope.launch { pager.animateScrollToPage(modes.indexOf(category)) } },
                grid = compactAudioView,
                onGridChange = {
                    compactAudioView = it
                    musicPreferences.edit().putBoolean("horizontal_view", it).apply()
                },
                sort = audioSort,
                onSort = { audioSort = it },
            )

        }

        // Keep the toolbar outside the list so it remains available when a
        // library mode has no matching tracks (for example an empty playlist).
        audioToolbar()
        if (mediaAccess.granted) {
                com.example.ui.components.LibraryAccessExplanation(audio = true)
            com.example.ui.components.FirstUseTip(swipeTip, "Swipe to explore your music",
                "Swipe left or right across the library to move between Songs, Folders, Playlists, Albums and Artists. Tap a tab to jump straight there.",
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp))
            if (audioList.isNotEmpty() || scanStatus != com.example.ui.components.LibraryScanStatus.SCANNING)
                com.example.ui.components.LibraryScanFeedback(scanStatus, audioList.size, audio = true)
        }

        // Track List
        androidx.compose.foundation.pager.HorizontalPager(
            state = pager, key = { modes[it] }, beyondViewportPageCount = 1,
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
            } else if (scanStatus == com.example.ui.components.LibraryScanStatus.SCANNING && audioList.isEmpty()) {
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
                            musicViewModel.loadAudioTracks()
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

    // Equalizer Preset Dialog
    if (showEqDialog) {
        EqualizerPresetDialog(
            currentPreset = equalizerPreset,
            bands = equalizerBands,
            status = equalizerStatus,
            onBandChange = musicViewModel::setEqualizerBand,
            onPresetSelected = {
                musicViewModel.setEqualizerPreset(it)
            },
            onDismiss = { showEqDialog = false }
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
    val context = LocalContext.current
    val progress = if (duration > 0) (currentPos.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    val formatTime: (Long) -> String = { ms ->
        val totalSeconds = ms / 1000
        val min = totalSeconds / 60
        val sec = totalSeconds % 60
        String.format(java.util.Locale.US, "%02d:%02d", min, sec)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("now_playing_bottom_sheet"),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surfaceElevated),
        border = BorderStroke(1.dp, palette.surfaceBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            // Top Quick Options Bar (Subtitles Search, Video Search, Equalizer, Cancel X)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Subtitle Search Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp)).hiContainerBorder(RoundedCornerShape(8.dp))
                            .background(palette.primary.copy(alpha = 0.15f))
                            .clickable(onClick = onLyricsToggle)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Subtitles,
                                contentDescription = "Lyrics",
                                tint = palette.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (lyricsEnabled) "Lyrics Off" else "Lyrics",
                                fontSize = 11.sp,
                                color = palette.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Spacer(modifier = Modifier.width(6.dp))

                    // EQ Preset Chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.surface)
                            .border(0.5.dp, palette.surfaceBorder, RoundedCornerShape(8.dp))
                            .clickable(onClick = onOpenEq)
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = eqPreset,
                            fontSize = 10.sp,
                            color = palette.textSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // CANCEL Playing Sheet Button (Close X)
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("cancel_audio_sheet")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel Playing",
                        tint = palette.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Wavy Audio Soundwave Canvas
            WavyAudioWaveform(
                isPlaying = isPlaying,
                waveColor = palette.primary,
                height = 20.dp
            )

            // Slider Scrub Bar
            Slider(
                value = progress,
                onValueChange = { frac ->
                    onSeek((frac * duration).toLong())
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp),
                colors = SliderDefaults.colors(
                    thumbColor = palette.primary,
                    activeTrackColor = palette.primary,
                    inactiveTrackColor = palette.surfaceBorder
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = formatTime(currentPos), fontSize = 10.sp, color = palette.textSecondary)
                Text(text = formatTime(duration), fontSize = 10.sp, color = palette.textSecondary)
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Track Info & Controls Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onExpandFullScreen),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // The thumbnail becomes a compact live lyric preview when enabled.
                    Box(
                        modifier = Modifier
                            .size(width = 210.dp, height = 54.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.surface)
                            .border(1.dp, palette.primary.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (lyricsEnabled) {
                            AudioLyricsPanel(
                                text = lyricsText, activeIndex = activeLyricLineIndex,
                                message = lyricsMessage, loading = lyricsLoading,
                                retryable = lyricsRetryable, onRetry = onRetryLyrics, compact = true,
                            )
                        } else {
                            AudioTrackThumbnail(
                                track = track,
                                modifier = Modifier.fillMaxSize(),
                                shape = RoundedCornerShape(10.dp),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = track.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = palette.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.artist,
                            fontSize = 11.5.sp,
                            color = palette.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPrevious, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = palette.textPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(palette.primary)
                            .clickable(onClick = onTogglePlay)
                            .testTag("audio_toggle_play_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = if (palette.isDark) Color.Black else Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    IconButton(onClick = onNext, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = palette.textPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EqualizerPresetDialog(
    currentPreset: String,
    bands: List<Float>,
    status: String,
    onBandChange: (Int, Float) -> Unit,
    onPresetSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalHiPalette.current
    val presets = com.example.audio.MusicEqualizer.presets.keys.toList()

    AlertDialog(

        modifier = androidx.compose.ui.Modifier.hiContainerBorder(androidx.compose.foundation.shape.RoundedCornerShape(28.dp)),
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Equalizer, contentDescription = null, tint = palette.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Audio Equalizer & DSP", color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                bands.forEachIndexed { index, gain ->
                    Text("${listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")[index]} · ${"%.1f".format(gain)} dB")
                    androidx.compose.material3.Slider(value = gain, valueRange = -12f..12f,
                        onValueChange = { onBandChange(index, it) })
                }
                Text(
                    text = status,
                    color = palette.textSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                presets.forEach { preset ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp)).hiContainerBorder(RoundedCornerShape(8.dp))
                            .clickable { onPresetSelected(preset) }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentPreset == preset,
                            onClick = { onPresetSelected(preset) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = palette.primary,
                                unselectedColor = palette.textSecondary
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = preset,
                            color = if (currentPreset == preset) palette.primary else palette.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = if (currentPreset == preset) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = palette.primary)
            }
        },
        containerColor = palette.surfaceElevated
    )
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
