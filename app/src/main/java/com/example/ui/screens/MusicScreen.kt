package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OndemandVideo
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.model.AudioItem
import com.example.ui.components.AudioTrackThumbnail
import com.example.ui.components.HiPlayerHeader
import com.example.ui.theme.LocalHiPalette
import com.example.viewmodel.MusicViewModel

/**
 * "Find Video" now searches the internet (YouTube) for this track's music
 * video instead of only checking the device's local library - opens the
 * YouTube app directly if installed, otherwise falls back to the browser.
 */
private fun openYoutubeSearchForTrack(context: android.content.Context, track: AudioItem) {
    val query = "${track.artist} ${track.title} official video".trim()
    val encodedQuery = Uri.encode(query)
    try {
        val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:search?q=$encodedQuery"))
        appIntent.setPackage("com.google.android.youtube")
        context.startActivity(appIntent)
    } catch (e: Exception) {
        val webIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.youtube.com/results?search_query=$encodedQuery")
        )
        context.startActivity(webIntent)
    }
}

private fun openLyricsFallbackIfAny(context: android.content.Context, url: String?) {
    if (url.isNullOrBlank()) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen(
    musicViewModel: MusicViewModel,
    onPlayVideo: (android.net.Uri) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalHiPalette.current
    val audioList by musicViewModel.audioList.collectAsState()
    val currentTrack by musicViewModel.currentTrack.collectAsState()
    val isPlaying by musicViewModel.isPlaying.collectAsState()
    val currentPos by musicViewModel.currentPositionMs.collectAsState()
    val duration by musicViewModel.durationMs.collectAsState()
    val searchQuery by musicViewModel.searchQuery.collectAsState()
    val selectedFilter by musicViewModel.selectedFilter.collectAsState()
    val equalizerPreset by musicViewModel.equalizerPreset.collectAsState()
    val activeSubtitle by musicViewModel.activeSubtitle.collectAsState()
    val lyricsFallbackUrl by musicViewModel.lyricsFallbackUrl.collectAsState()
    val isFullScreenPlayerOpen by musicViewModel.isFullScreenPlayerOpen.collectAsState()
    val matchingVideoState by musicViewModel.matchingVideoState.collectAsState()

    var showEqDialog by remember { mutableStateOf(false) }

    val filteredList = remember(audioList, searchQuery, selectedFilter) {
        audioList.filter { track ->
            val matchesQuery = searchQuery.isBlank() ||
                    track.title.contains(searchQuery, ignoreCase = true) ||
                    track.artist.contains(searchQuery, ignoreCase = true) ||
                    track.album.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (selectedFilter) {
                "Lossless FLAC" -> track.path.endsWith(".flac", ignoreCase = true)
                "Hi-Res WAV" -> track.path.endsWith(".wav", ignoreCase = true)
                "MP3 Audio" -> track.path.endsWith(".mp3", ignoreCase = true)
                else -> true
            }

            matchesQuery && matchesFilter
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(palette.background)
    ) {
        // One shared compact header used across the player.
        HiPlayerHeader(
            testTag = "music_header",
            onSearchClick = { musicViewModel.setSearchQuery("") },
            onRefreshClick = { musicViewModel.loadAudioTracks() },
            onStreamClick = { /* Stream URL dialog is handled by the host screen. */ }
        )

        // Clean Sheet Plan Filter chips bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.surface)
        ) {
            val filters = listOf("All", "MP3 Audio")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filters) { filter ->
                    val isSelected = selectedFilter == filter
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) palette.primary.copy(alpha = 0.18f) else palette.surfaceElevated
                            )
                            .clickable { musicViewModel.setFilter(filter) }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = filter,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) palette.primary else palette.textPrimary
                        )
                    }
                }
            }
        }

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
                            text = "No audio tracks found",
                            color = palette.textSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredList, key = { it.id }) { track ->
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
        }

        // Full Screen Audio Player or Wavy Now Playing Sheet
        if (currentTrack != null) {
            if (isFullScreenPlayerOpen) {
                FullScreenAudioPlayerScreen(
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
                    onOpenSubtitleSearch = { musicViewModel.fetchAndSyncLyrics(currentTrack!!) },
                    onOpenVideoSearch = { openYoutubeSearchForTrack(context, currentTrack!!) },
                    onBack = { musicViewModel.closeFullScreenPlayer() },
                    onLyricsTap = { openLyricsFallbackIfAny(context, lyricsFallbackUrl) },
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
                    onOpenSubtitleSearch = { musicViewModel.fetchAndSyncLyrics(currentTrack!!) },
                    onOpenVideoSearch = { openYoutubeSearchForTrack(context, currentTrack!!) },
                    onLyricsTap = { openLyricsFallbackIfAny(context, lyricsFallbackUrl) },
                    onCancel = { musicViewModel.stopTrack() },
                    onExpandFullScreen = { musicViewModel.openFullScreenPlayer() }
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

    // Matching-video search result - replaces the old fake dialog that
    // listed made-up video titles and whose "play" action did nothing.
    // This is a real MediaStore lookup against the device's actual videos.
    when (val state = matchingVideoState) {
        is MusicViewModel.MatchingVideoState.Searching -> {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Searching your library…", color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
                text = { Text("Looking for a matching video for this track.", color = palette.textSecondary, fontSize = 12.sp) },
                confirmButton = {}
            )
        }
        is MusicViewModel.MatchingVideoState.Found -> {
            AlertDialog(
                onDismissRequest = { musicViewModel.resetMatchingVideoState() },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.OndemandVideo, contentDescription = null, tint = palette.secondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Video Found", color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Text(
                        text = "Found a matching video in your library: ${state.title}",
                        color = palette.textSecondary,
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        musicViewModel.resetMatchingVideoState()
                        onPlayVideo(state.uri)
                    }) {
                        Text("Play", color = palette.secondary, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { musicViewModel.resetMatchingVideoState() }) {
                        Text("Cancel", color = palette.textSecondary)
                    }
                }
            )
        }
        is MusicViewModel.MatchingVideoState.NotFound -> {
            AlertDialog(
                onDismissRequest = { musicViewModel.resetMatchingVideoState() },
                title = { Text("No Matching Video", color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
                text = { Text("No video matching this track was found in your library.", color = palette.textSecondary, fontSize = 12.sp) },
                confirmButton = {
                    TextButton(onClick = { musicViewModel.resetMatchingVideoState() }) {
                        Text("OK", color = palette.primary, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
        is MusicViewModel.MatchingVideoState.Idle -> {}
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
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .testTag("audio_track_${track.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Audio Track Thumbnail
        Box(
            modifier = Modifier
                .size(42.dp)
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
                    .clip(RoundedCornerShape(4.dp))
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
    onOpenVideoSearch: () -> Unit,
    onLyricsTap: () -> Unit = {},
    onCancel: () -> Unit,
    onExpandFullScreen: () -> Unit = {},
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
        border = BorderStroke(1.dp, palette.surfaceBorder.copy(alpha = 0.5f)),
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
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.primary.copy(alpha = 0.15f))
                            .clickable(onClick = onOpenSubtitleSearch)
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
                                text = if (activeSubtitle != null) "Lyrics On" else "Lyrics",
                                fontSize = 11.sp,
                                color = palette.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Internet Video Search Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.secondary.copy(alpha = 0.15f))
                            .clickable(onClick = onOpenVideoSearch)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.OndemandVideo,
                                contentDescription = "Find Video",
                                tint = palette.secondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Find Video",
                                fontSize = 11.sp,
                                color = palette.secondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

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

            // Attached Active Subtitle Banner if loaded
            if (activeSubtitle != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(palette.primary.copy(alpha = 0.12f))
                        .clickable(onClick = onLyricsTap)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = activeSubtitle,
                        fontSize = 11.sp,
                        color = palette.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

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
                    // PNG Thumbnail of local audio track
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.surface)
                            .border(1.dp, palette.primary.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        AudioTrackThumbnail(
                            track = track,
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(10.dp),
                            contentScale = ContentScale.Crop
                        )
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
                            .clip(RoundedCornerShape(8.dp))
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
