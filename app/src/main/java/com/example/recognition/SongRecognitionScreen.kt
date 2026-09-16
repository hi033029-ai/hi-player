package com.example.recognition

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Composable
fun SongRecognitionScreen(viewModel: SongRecognitionViewModel, sourceUri: Uri? = null) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var selectedModeName by rememberSaveable { mutableStateOf(SongRecognitionMode.PLAYING.name) }
    val selectedMode = remember(selectedModeName) {
        SongRecognitionMode.valueOf(selectedModeName)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
        ) { granted -> if (granted) viewModel.startListening(selectedMode, sourceUri) }

    fun requestAndStart() {
        // A local video is decoded directly; microphone permission is only needed
        // when there is no local source and recognition must listen externally.
        if (sourceUri != null && sourceUri != Uri.EMPTY) {
            viewModel.startListening(selectedMode, sourceUri)
            return
        }
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            viewModel.startListening(selectedMode, sourceUri)
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val s = state) {
                is RecognitionState.Idle -> {
                    MicButton(pulsing = false, onClick = ::requestAndStart)
                    Spacer(Modifier.height(14.dp))
                    Text("Tap to identify a song")
                    Spacer(Modifier.height(18.dp))
                    RecognitionModeRow(
                        selected = selectedMode,
                        onSelected = { selectedModeName = it.name },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        selectedMode.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                    )
                    if (selectedMode == SongRecognitionMode.HUMMING) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Humming and singing need melody matching; AcoustID is optimized for recorded songs.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray,
                        )
                    }
                }
                is RecognitionState.Listening -> {
                    MicButton(pulsing = true, onClick = {})
                    Spacer(Modifier.height(16.dp))
                    Text(
                        if (sourceUri != null && sourceUri != Uri.EMPTY)
                            "Extracting audio and trying 10-second windows…"
                        else
                            "Listening for 10 seconds…",
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(selectedMode.title, color = Color.Gray)
                }
                is RecognitionState.Recognizing -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Recognising song in the background…")
                }
                is RecognitionState.Found -> {
                    Text(s.song.title, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(s.song.artist, color = Color.Gray)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${(s.song.score * 100).toInt()}% match",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                    )
                    Spacer(Modifier.height(16.dp))

                    var musicBrainzSong by remember(s.song) { mutableStateOf<MusicBrainzSong?>(null) }
                    var musicBrainzFinished by remember(s.song) { mutableStateOf(false) }
                    var videoId by remember(s.song) { mutableStateOf<String?>(null) }
                    var searchFailed by remember(s.song) { mutableStateOf(false) }
                    LaunchedEffect(s.song) {
                        // MusicBrainz supplies canonical title/artist metadata when
                        // available; AcoustID remains the audio-identification source.
                        val metadataResult = MusicBrainzApi.findRecording(s.song)
                        musicBrainzSong = metadataResult.getOrNull()
                        musicBrainzFinished = true
                        val metadata = musicBrainzSong
                        val artist = metadata?.artist ?: s.song.artist
                        val title = metadata?.title ?: s.song.title
                        YouTubeSearchApi.findVideoId(artist, title)
                            .onSuccess { videoId = it; searchFailed = (it == null) }
                            .onFailure { searchFailed = true }
                    }

                    if (musicBrainzSong != null) {
                        val metadata = musicBrainzSong!!
                        Text(
                            "MusicBrainz: ${metadata.title} — ${metadata.artist}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                        )
                        metadata.releaseTitle?.let { release ->
                            Text(
                                "Release: $release${metadata.releaseYear?.let { " ($it)" } ?: ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    when {
                        videoId != null -> YouTubeEmbedPlayer(videoId = videoId!!)
                        searchFailed -> Text("Couldn't find a video for this track", color = Color.Gray)
                        !musicBrainzFinished -> Text("Loading music metadata…", color = Color.Gray)
                        else -> CircularProgressIndicator()
                    }

                    Spacer(Modifier.height(24.dp))
                    TextButton(onClick = viewModel::reset) { Text("Search again") }
                }
                is RecognitionState.NoMatch -> {
                    Text("No match found")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "AcoustID's free database doesn't cover every track — especially newer or less common releases.",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(24.dp))
                    TextButton(onClick = viewModel::reset) { Text("Try again") }
                }
                is RecognitionState.Error -> {
                    Text("Something went wrong", color = Color.Red)
                    Spacer(Modifier.height(4.dp))
                    Text(s.message, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(24.dp))
                    TextButton(onClick = viewModel::reset) { Text("Try again") }
                }
            }
        }
    }
}

@Composable
private fun RecognitionModeRow(
    selected: SongRecognitionMode,
    onSelected: (SongRecognitionMode) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SongRecognitionMode.entries.forEachIndexed { index, mode ->
            FilterChip(
                selected = selected == mode,
                onClick = { onSelected(mode) },
                label = { Text(mode.title) },
            )
            if (index < SongRecognitionMode.entries.lastIndex) Spacer(Modifier.width(6.dp))
        }
    }
}

@Composable
private fun MicButton(pulsing: Boolean, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "micPulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (pulsing) 1.15f else 1f,
        animationSpec = infiniteRepeatable(
            tween(600, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "micScale",
    )

    Box(
        modifier = Modifier
            .size(80.dp)
            .scale(if (pulsing) scale else 1f)
            .clip(CircleShape)
            .background(if (pulsing) Color(0xFFE53935) else Color(0xFF2196F3))
            .clickable(enabled = !pulsing, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Mic,
            contentDescription = "Start song recognition",
            tint = Color.White,
            modifier = Modifier.size(36.dp),
        )
    }
}
