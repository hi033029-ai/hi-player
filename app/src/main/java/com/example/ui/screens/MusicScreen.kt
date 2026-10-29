package com.example.ui.screens

import com.example.ui.theme.hiContainerBorder

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen(
    musicViewModel: MusicViewModel,
    onPlayVideo: (android.net.Uri) -> Unit = {},
    modifier: Modifier = Modifier,
    includeHeader: Boolean = true,
    onSearchRequested: (() -> Unit)? = null,
    libraryViewModel: LibraryViewModel? = null
) {
    val context = LocalContext.current
    val palette = LocalHiPalette.current
    val audioList by musicViewModel.audioList.collectAsState()
    val currentTrack by musicViewModel.currentTrack.collectAsState()
    val isPlaying by musicViewModel.isPlaying.collectAsState()
    val currentPos by musicViewModel.currentPositionMs.collectAsState()
    val duration by musicViewModel.durationMs.collectAsState()
    val searchQuery by musicViewModel.searchQuery.collectAsState()
    val continueWatchingVideos by (libraryViewModel?.continueWatchingVideos
        ?: flowOf(emptyList<LibraryViewModel.ContinueWatchingItem>()))
        .collectAsState(initial = emptyList())
    val equalizerPreset by musicViewModel.equalizerPreset.collectAsState()
    val activeSubtitle by musicViewModel.activeSubtitle.collectAsState()
    val lyricsText by musicViewModel.lyricsText.collectAsState()
    val lyricsEnabled by musicViewModel.lyricsEnabled.collectAsState()
    val currentLyricLineIndex by musicViewModel.currentLyricLineIndex.collectAsState()
    val isFullScreenPlayerOpen by musicViewModel.isFullScreenPlayerOpen.collectAsState()

    var showEqDialog by remember { mutableStateOf(false) }
    val toggleLyrics: () -> Unit = {
        val enabled = !lyricsEnabled
        musicViewModel.setLyricsEnabled(enabled)
        if (enabled) {
            currentTrack?.let { musicViewModel.fetchAndSyncLyrics(it) }
        }
    }
    var libraryMode by remember { mutableStateOf("All Music") }
    val musicPreferences = remember {
        context.getSharedPreferences("music_preferences", android.content.Context.MODE_PRIVATE)
    }
    var compactAudioView by remember {
        mutableStateOf(musicPreferences.getBoolean("horizontal_view", false))
    }
    var audioGridMinSize by remember {
        mutableStateOf(musicPreferences.getInt("grid_min_size_dp", 160).coerceIn(120, 240))
    }
    var audioSort by remember { mutableStateOf("Name") }
    var selectedCollection by remember { mutableStateOf<String?>(null) }
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

    val filteredList = remember(audioList, searchQuery, audioSort, libraryMode, selectedCollection) {
        audioList.filter { track ->
            val matchesQuery = searchQuery.isBlank() ||
                    track.title.contains(searchQuery, ignoreCase = true) ||
                    track.artist.contains(searchQuery, ignoreCase = true) ||
                    track.album.contains(searchQuery, ignoreCase = true)

            val matchesLibrary = when (libraryMode) {
                "Playlists" -> track.isFavorite
                else -> true
            }
            val matchesCollection = selectedCollection?.let { collection ->
                when (libraryMode) {
                    "Folders" -> track.path.substringBeforeLast('/', "").substringAfterLast('/', "Music") == collection
                    "Albums" -> track.album.ifBlank { "Unknown album" } == collection
                    "Artists" -> track.artist.ifBlank { "Unknown artist" } == collection
                    else -> true
                }
            } ?: true
            matchesQuery && matchesLibrary && matchesCollection
        }.let { list ->
            when (audioSort) {
                "Custom" -> list
                "Artist" -> list.sortedBy { it.artist.lowercase() }
                "Duration" -> list.sortedByDescending { it.durationMs }
                "Newest" -> list.sortedByDescending { it.dateAdded }
                else -> when (libraryMode) {
                    "Folders" -> list.sortedWith(compareBy({ it.path.substringBeforeLast('/', "").lowercase() }, { it.title.lowercase() }))
                    "Albums" -> list.sortedWith(compareBy({ it.album.lowercase() }, { it.title.lowercase() }))
                    "Artists" -> list.sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))
                    else -> list.sortedBy { it.title.lowercase() }
                }
            }
        }
    }

    // Folders, albums, and artists are genuine grouped views rather than a
    // flat track list merely sorted by that field. The selected sort still
    // determines ordering inside each group.
    val groupedAudio = remember(filteredList, libraryMode) {
        when (libraryMode) {
            "Folders" -> filteredList.groupBy { it.path.substringBeforeLast('/', "").substringAfterLast('/', "Music") }
            "Albums" -> filteredList.groupBy { it.album.ifBlank { "Unknown album" } }
            "Artists" -> filteredList.groupBy { it.artist.ifBlank { "Unknown artist" } }
            else -> emptyMap()
        }.toSortedMap(String.CASE_INSENSITIVE_ORDER)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        val audioToolbar: @Composable () -> Unit = {
            MusicLibraryToolbar(
                selected = libraryMode,
                onCategory = { libraryMode = it; selectedCollection = null },
                grid = compactAudioView,
                onGridChange = {
                    compactAudioView = it
                    musicPreferences.edit().putBoolean("horizontal_view", it).apply()
                },
                sort = audioSort,
                onSort = { audioSort = it },
            )
            if (selectedCollection != null) {
                TextButton(onClick = { selectedCollection = null }) {
                    Text("‹ All $libraryMode", color = palette.textPrimary)
                }
            }
        }

        // Keep the toolbar outside the list so it remains available when a
        // library mode has no matching tracks (for example an empty playlist).
        MusicLibraryAccess(onRefresh = { musicViewModel.loadAudioTracks() })
        audioToolbar()

        // Track List
        Box(modifier = Modifier.weight(1f)) {
            if (filteredList.isEmpty()) {
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
                            text = when (libraryMode) {
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
                            libraryMode = "All Music"
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
                                    imageVector = when (libraryMode) {
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
                                gridItems(tracks, key = { track -> "audio_grid_${groupName}_${track.id}" }) { track ->
                                    val isSelected = currentTrack?.id == track.id
                                    AudioTrackCard(
                                        track = track,
                                        isSelected = isSelected,
                                        isPlaying = isSelected && isPlaying,
                                        onClick = { musicViewModel.playTrack(track) },
                                        modifier = Modifier.padding(4.dp),
                                    )
                                }
                            }
                        } else {
                            gridItems(filteredList, key = { it.id }) { track ->
                                val isSelected = currentTrack?.id == track.id
                                AudioTrackCard(
                                    track = track,
                                    isSelected = isSelected,
                                    isPlaying = isSelected && isPlaying,
                                    onClick = { musicViewModel.playTrack(track) },
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .reorderableDrag(
                                            itemIndex = filteredList.indexOf(track),
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
                            items(tracks, key = { track -> "audio_${groupName}_${track.id}" }) { track ->
                                val isSelected = currentTrack?.id == track.id
                                AudioTrackCard(
                                    track = track,
                                    isSelected = isSelected,
                                    isPlaying = isSelected && isPlaying,
                                    onClick = { musicViewModel.playTrack(track) }
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(filteredList, key = { _, it -> it.id }) { index, track ->
                            val isSelected = currentTrack?.id == track.id
                            AudioTrackCard(
                                track = track,
                                isSelected = isSelected,
                                isPlaying = isSelected && isPlaying,
                                onClick = { musicViewModel.playTrack(track) },
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
                    activeLyricLineIndex = currentLyricLineIndex,
                    onTogglePlay = { musicViewModel.togglePlayPause() },
                    onNext = { musicViewModel.playNext() },
                    onPrevious = { musicViewModel.playPrevious() },
                    onSeek = { musicViewModel.seekTo(it) },
                    onOpenEq = { showEqDialog = true },
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
                    onOpenEq = { showEqDialog = true },
                    onOpenSubtitleSearch = toggleLyrics,
                    onLyricsTap = toggleLyrics,
                    onCancel = { musicViewModel.stopTrack() },
                    onExpandFullScreen = { musicViewModel.openFullScreenPlayer() },
                    lyricsEnabled = lyricsEnabled,
                    lyricsText = lyricsText,
                    activeLyricLineIndex = currentLyricLineIndex,
                    onLyricsToggle = toggleLyrics
                )
            }
        }
    }

    // Equalizer Preset Dialog
    if (showEqDialog) {
        EqualizerPresetDialog(
            currentPreset = equalizerPreset,
            onPresetSelected = {
                musicViewModel.setEqualizerPreset(it)
                showEqDialog = false
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
    modifier: Modifier = Modifier
) {
    val palette = LocalHiPalette.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .hiContainerBorder(RoundedCornerShape(12.dp), selected = isSelected)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .testTag("audio_track_${track.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Audio Track Thumbnail
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(8.dp)).hiContainerBorder(RoundedCornerShape(8.dp))
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
                    fontSize = 11.sp,
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.End) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp)).hiContainerBorder(RoundedCornerShape(4.dp))
                    .background(palette.primary.copy(alpha = 0.15f))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = track.audioFormat,
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
    modifier: Modifier = Modifier
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
                                contentDescription = "Subtitles",
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
                        if (lyricsEnabled && !lyricsText.isNullOrBlank()) {
                            val previewLines = lyricsText.lines().mapNotNull { line ->
                                Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})](.*)""").find(line)?.groupValues?.getOrNull(4)?.trim()
                            }
                            val previewStart = if (activeLyricLineIndex >= 0) activeLyricLineIndex else 0
                            val preview = previewLines.drop(previewStart).take(2)
                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                preview.forEachIndexed { index, line ->
                                    Text(
                                        text = line.ifBlank { "♪" },
                                        color = if (index == 0) palette.primary else palette.textSecondary,
                                        fontSize = if (index == 0) 11.sp else 9.sp,
                                        fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                                        style = LocalTextStyle.current.copy(textDirection = TextDirection.ContentOrRtl),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
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
    onPresetSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalHiPalette.current
    val presets = listOf("Cinema 3D", "Bass Boost 200%", "Vocal Clarity", "Rock & Metal", "Electronic / EDM", "Audiophile Flat")

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
            Column {
                Text(
                    text = "Select hardware-tuned acoustic profile:",
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
