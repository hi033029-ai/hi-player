package com.example.recognition

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Composable
fun SongRecognitionScreen(viewModel: SongRecognitionViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.startListening() }

    fun requestAndStart() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            viewModel.startListening()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when (val s = state) {
                is RecognitionState.Idle -> {
                    MicButton(pulsing = false, onClick = ::requestAndStart)
                    Spacer(Modifier.height(16.dp))
                    Text("Tap to identify a song")
                }
                is RecognitionState.Listening -> {
                    MicButton(pulsing = true, onClick = {})
                    Spacer(Modifier.height(16.dp))
                    Text("Listening…")
                }
                is RecognitionState.Recognizing -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Identifying…")
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

                    var videoId by remember(s.song) { mutableStateOf<String?>(null) }
                    var searchFailed by remember(s.song) { mutableStateOf(false) }
                    LaunchedEffect(s.song) {
                        YouTubeSearchApi.findVideoId(s.song.artist, s.song.title)
                            .onSuccess { videoId = it; searchFailed = (it == null) }
                            .onFailure { searchFailed = true }
                    }

                    when {
                        videoId != null -> YouTubeEmbedPlayer(videoId = videoId!!)
                        searchFailed -> Text("Couldn't find a video for this track", color = Color.Gray)
                        else -> CircularProgressIndicator()
                    }

                    Spacer(Modifier.height(24.dp))
                    TextButton(onClick = viewModel::reset) { Text("Search again") }
                }
                is RecognitionState.NoMatch -> {
                    Text("No match found")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "AcoustID's free database doesn't cover every track — " +
                            "especially newer or less common releases.",
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
private fun MicButton(pulsing: Boolean, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "micPulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (pulsing) 1.15f else 1f,
        animationSpec = infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
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
        Icon(Icons.Filled.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
    }
}
