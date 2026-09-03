package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.model.AspectRatioMode
import com.example.model.VideoItem
import com.example.ui.components.AudioSettingsBottomSheet
import com.example.ui.components.ControlsOverlay
import com.example.ui.components.FetchSubtitleUrlDialog
import com.example.ui.components.GestureOverlay
import com.example.ui.components.MoreOptionsBottomSheet
import com.example.ui.components.TmdbKeyDialog

import com.example.ui.components.SubtitleCustomizationBottomSheet
import com.example.ui.components.SubtitleSettingsBottomSheet
import com.example.ui.components.VideoInfoDialog
import com.example.ui.components.VideoSettingsBottomSheet
import com.example.viewmodel.ActiveSheet
import com.example.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.util.RecognizedSong
import com.example.util.recognizeVideoSong

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onEnterPip: () -> Unit,
    currentThemeMode: com.example.data.AppThemeMode = com.example.data.AppThemeMode.CYAN_NEON_DARK,
    onThemeSelected: (com.example.data.AppThemeMode) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // Immersive playback: hide the status bar (battery/network/notification
    // icons) and nav bar while the player is on screen so nothing but the
    // video and its own controls are visible, restoring system bars when the
    // user leaves this screen. Swiping from an edge still reveals them
    // briefly (BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE) so back gestures work.
    DisposableEffect(activity) {
        val window = activity?.window
        val insetsController = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, it.decorView) }
        insetsController?.let { controller ->
            controller.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            insetsController?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }

    val currentVideo by playerViewModel.currentVideo.collectAsState()
    val isPlaying by playerViewModel.engine.isPlaying.collectAsState()
    val isBuffering by playerViewModel.engine.isBuffering.collectAsState()
    val currentPositionMs by playerViewModel.engine.currentPositionMs.collectAsState()
    val durationMs by playerViewModel.engine.durationMs.collectAsState()
    val bufferedPositionMs by playerViewModel.engine.bufferedPositionMs.collectAsState()
    val aspectRatioMode by playerViewModel.engine.aspectRatioMode.collectAsState()
    val playbackSpeed by playerViewModel.engine.playbackSpeed.collectAsState()
    val isScreenLocked by playerViewModel.engine.isScreenLocked.collectAsState()
    val isBgPlayActive by playerViewModel.engine.isBackgroundPlayActive.collectAsState()
    val availableAudioTracks by playerViewModel.engine.availableAudioTracks.collectAsState()
    val availableSubtitleTracks by playerViewModel.engine.availableSubtitleTracks.collectAsState()
    val telemetry by playerViewModel.engine.telemetry.collectAsState()
    val volumeBoostPercent by playerViewModel.engine.volumeBoostPercent.collectAsState()
    val audioDelayMs by playerViewModel.engine.audioDelayMs.collectAsState()
    val subtitleOffsetMs by playerViewModel.engine.subtitleOffsetMs.collectAsState()
    val abRepeatA by playerViewModel.engine.abRepeatA.collectAsState()
    val abRepeatB by playerViewModel.engine.abRepeatB.collectAsState()
    val activeSheet by playerViewModel.activeSheet.collectAsState()
    val currentRating by playerViewModel.currentRating.collectAsState()
    val sleepTimerMinutes by playerViewModel.sleepTimerMinutesLeft.collectAsState()
    val playerError by playerViewModel.engine.playerError.collectAsState()

    // New features state
    val videoScale by playerViewModel.videoScale.collectAsState()
    val subtitleStyle by playerViewModel.subtitleStyle.collectAsState()
    val hdrEnhanceActive by playerViewModel.hdrEnhanceActive.collectAsState()
    val isHdrContent by playerViewModel.engine.isHdrContent.collectAsState()
    val wideColorGamutEnabled by playerViewModel.wideColorGamutEnabled.collectAsState()
    val screenOrientation by playerViewModel.screenOrientation.collectAsState()

    // HUD States
    val brightnessLevel by playerViewModel.brightnessLevel.collectAsState()
    val volumeLevel by playerViewModel.volumeLevel.collectAsState()
    val scrubTimeMs by playerViewModel.scrubTimeMs.collectAsState()
    val scrubDeltaMs by playerViewModel.scrubDeltaMs.collectAsState()

    var areControlsVisible by remember { mutableStateOf(true) }
    var recognitionDialogVisible by remember { mutableStateOf(false) }
    // AudD documents the public "test" token for its no-key demo endpoint.
    // Use it by default so recognition starts automatically without blocking
    // playback with a token setup dialog.
    var recognitionToken by remember {
        mutableStateOf(context.getSharedPreferences("hi_player_recognition", 0)
            .getString("audd_token", "test") ?: "test")
    }
    var recognizedSong by remember { mutableStateOf<RecognizedSong?>(null) }
    var recognitionError by remember { mutableStateOf<String?>(null) }
    var isRecognizing by remember { mutableStateOf(false) }
    val recognitionScope = rememberCoroutineScope()

    fun startRecognition(token: String, showProgress: Boolean = false) {
        currentVideo?.let { video ->
            isRecognizing = true
            recognizedSong = null
            recognitionError = null
            if (showProgress) recognitionDialogVisible = true
            recognitionScope.launch {
                recognizeVideoSong(context, video, token, currentPositionMs)
                    .onSuccess { match ->
                        recognizedSong = match
                        if (match == null) {
                            recognitionError = "No matching song was found in this video."
                        } else {
                            recognitionDialogVisible = true
                        }
                    }
                    .onFailure { error ->
                        recognitionError = error.message ?: "Song recognition failed."
                        if (showProgress) recognitionDialogVisible = true
                    }
                isRecognizing = false
            }
        }
    }

    fun openVideoTitleSearch() {
        currentVideo?.let { video ->
            val cleanTitle = video.title.substringBeforeLast('.', video.title)
                .replace(Regex("[_-]+"), " ").trim()
            val query = Uri.encode("$cleanTitle song")
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$query")))
        }
    }

    val searchSongFromVideo: () -> Unit = {
        if (recognitionToken.isBlank()) {
            openVideoTitleSearch()
        } else if (recognizedSong != null) {
            recognitionDialogVisible = true
        } else {
            startRecognition(recognitionToken.trim(), showProgress = true)
        }
    }

    fun openSongSearch(title: String, artist: String) {
        val query = Uri.encode("$artist $title song")
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$query")))
    }

    // Recognition starts automatically whenever a different video begins playing.
    LaunchedEffect(currentVideo?.uri) {
        currentVideo?.let {
            recognizedSong = null
            recognitionError = null
            val savedToken = recognitionToken.trim()
            if (savedToken.isNotBlank()) startRecognition(savedToken)
        }
    }

    // File pickers for open file and external subtitles
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val videoItem = VideoItem(
                uri = it,
                title = it.lastPathSegment ?: "Loaded Video",
                mimeType = "video/*"
            )
            playerViewModel.playVideo(videoItem)
        }
    }

    val subtitlePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { playerViewModel.loadExternalSubtitle(it) }
    }

    // Handle orientation changes
    LaunchedEffect(screenOrientation) {
        activity?.requestedOrientation = screenOrientation
    }

    // Keep screen on and restore the display state the activity had before
    // playback. Color mode updates themselves live in the LaunchedEffect below
    // so the original mode is not accidentally recaptured on every track or
    // settings update.
    DisposableEffect(activity) {
        val window = activity?.window
        val originalBrightness = window?.attributes?.screenBrightness
        val originalColorMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window?.colorMode
        } else {
            null
        }

        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window?.let { w ->
            val lp = w.attributes
            lp.screenBrightness = 1.0f
            w.attributes = lp
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                window?.colorMode = originalColorMode ?: ActivityInfo.COLOR_MODE_DEFAULT
            }
            window?.let { w ->
                val lp = w.attributes
                lp.screenBrightness = originalBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                w.attributes = lp
            }
        }
    }

    // Wide gamut alone does not enable HDR output. On Android O+ an HDR stream
    // needs COLOR_MODE_HDR, while SDR material can use wide gamut only when the
    // user enabled that preference. Devices without an HDR-capable display
    // safely fall back to their supported output mode.
    LaunchedEffect(activity, isHdrContent, wideColorGamutEnabled) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity?.window?.colorMode = when {
                isHdrContent -> ActivityInfo.COLOR_MODE_HDR
                wideColorGamutEnabled -> ActivityInfo.COLOR_MODE_WIDE_COLOR_GAMUT
                else -> ActivityInfo.COLOR_MODE_DEFAULT
            }
        }
    }

    // Auto-hide controls after 3 seconds of inactivity during playback
    LaunchedEffect(areControlsVisible, isPlaying) {
        if (areControlsVisible && isPlaying && !isScreenLocked) {
            delay(3000)
            areControlsVisible = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Video Surface Layer (PlayerView with scaling factor)
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    player = playerViewModel.engine.getPlayer()
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                playerView.player = playerViewModel.engine.getPlayer()
                playerView.resizeMode = when (aspectRatioMode) {
                    AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    AspectRatioMode.FILL_CROP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    AspectRatioMode.CINEMA_21_9 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    AspectRatioMode.ORIGINAL -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    AspectRatioMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                }

                // Apply subtitle customization (size / color / background opacity).
                // Previously SubtitleCustomizationBottomSheet updated this state but
                // nothing ever fed it into the actual rendered subtitle view, so the
                // sliders visibly had no effect.
                playerView.subtitleView?.let { subtitleView ->
                    subtitleView.setFixedTextSize(
                        android.util.TypedValue.COMPLEX_UNIT_SP,
                        subtitleStyle.fontSizeSp.toFloat()
                    )
                    val bgAlpha = (subtitleStyle.bgOpacity.coerceIn(0f, 1f) * 255).toInt()
                    val bgColor = (bgAlpha shl 24)
                    subtitleView.setStyle(
                        androidx.media3.ui.CaptionStyleCompat(
                            subtitleStyle.textColorHex.toInt(),
                            bgColor,
                            android.graphics.Color.TRANSPARENT,
                            androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                            android.graphics.Color.BLACK,
                            null
                        )
                    )
                    subtitleView.setBottomPaddingFraction(
                        (subtitleStyle.verticalOffsetDp / 400f).coerceIn(0f, 0.3f)
                    )
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = videoScale, scaleY = videoScale)
        )

        // 2. Gesture Handling Layer
        GestureOverlay(
            isLocked = isScreenLocked,
            onSingleTap = {
                areControlsVisible = !areControlsVisible
            },
            onDoubleTapLeft = {
                playerViewModel.seekRelative(-10_000L)
            },
            onDoubleTapRight = {
                playerViewModel.seekRelative(10_000L)
            },
            onBrightnessDelta = { delta ->
                activity?.let { act ->
                    val cur = act.window.attributes.screenBrightness
                    val newBri = playerViewModel.onBrightnessGesture(delta, cur)
                    val lp = act.window.attributes
                    lp.screenBrightness = newBri
                    act.window.attributes = lp
                }
            },
            onVolumeDelta = { delta ->
                playerViewModel.onVolumeGesture(delta)
            },
            onScrubStart = {
                playerViewModel.onScrubStart()
            },
            onScrubMove = { deltaMs ->
                playerViewModel.onScrubMove(deltaMs)
            },
            onScrubEnd = {
                playerViewModel.onScrubEnd()
            },
            brightnessLevel = brightnessLevel,
            volumeLevel = volumeLevel,
            scrubTimeMs = scrubTimeMs,
            scrubDeltaMs = scrubDeltaMs
        )

        // 3. UI Controls Overlay Layer
        ControlsOverlay(
            isVisible = areControlsVisible,
            isLocked = isScreenLocked,
            isPlaying = isPlaying,
            isBuffering = isBuffering,
            currentPositionMs = currentPositionMs,
            durationMs = durationMs,
            bufferedPositionMs = bufferedPositionMs,
            video = currentVideo,
            aspectRatioMode = aspectRatioMode,
            playbackSpeed = playbackSpeed,
            isBgPlayActive = isBgPlayActive,
            abRepeatA = abRepeatA,
            abRepeatB = abRepeatB,
            videoScale = videoScale,
            rating = currentRating,
            onTogglePlayPause = { playerViewModel.togglePlayPause() },
            onSeekTo = { playerViewModel.seekTo(it) },
            onRewind10 = { playerViewModel.seekRelative(-10_000L) },
            onFastForward10 = { playerViewModel.seekRelative(10_000L) },
            onBack = onBack,
            onToggleLock = { playerViewModel.engine.setScreenLocked(!isScreenLocked) },
            onRotateScreen = { playerViewModel.cycleScreenOrientation() },
            onZoomIn = { playerViewModel.zoomIn() },
            onZoomOut = { playerViewModel.zoomOut() },
            onEnterPip = onEnterPip,
            onToggleBgPlay = { playerViewModel.engine.setBackgroundPlay(!isBgPlayActive) },
            onOpenFile = { videoPickerLauncher.launch("video/*") },
            onSearchSong = searchSongFromVideo,
            isHdrEnhanceActive = hdrEnhanceActive,
            onToggleHdrEnhance = {
                playerViewModel.toggleHdrEnhance()
                areControlsVisible = false
            },
            onOpenPlaylist = { playerViewModel.openSheet(ActiveSheet.PLAYLIST_CHOOSER) },
            onToggleSubtitles = {
                // CC now opens the caption picker on a normal tap. Cycling tracks
                // was undiscoverable and prevented users from seeing the embedded
                // languages in a movie unless they knew to long-press the icon.
                playerViewModel.refreshCaptionTracks()
                playerViewModel.openSheet(ActiveSheet.SUBTITLE_SETTINGS)
            },
            onOpenAudioSettings = { playerViewModel.openSheet(ActiveSheet.AUDIO_SETTINGS) },
            onOpenVideoSettings = { playerViewModel.openSheet(ActiveSheet.VIDEO_SETTINGS) },
            onOpenMoreOptions = { playerViewModel.openSheet(ActiveSheet.PLAYLIST_CHOOSER) },
            onOpenTelemetry = { playerViewModel.openSheet(ActiveSheet.DECODER_TELEMETRY) },
            onCycleAspectRatio = { playerViewModel.cycleAspectRatio() },
            onSetAbRepeat = {
                if (abRepeatA == null) {
                    playerViewModel.engine.setAbRepeatA()
                } else if (abRepeatB == null) {
                    playerViewModel.engine.setAbRepeatB()
                } else {
                    playerViewModel.engine.clearAbRepeat()
                }
            },
            onCycleSpeed = {
                val nextSpeed = when (playbackSpeed) {
                    0.5f -> 0.75f
                    0.75f -> 1.0f
                    1.0f -> 1.25f
                    1.25f -> 1.5f
                    1.5f -> 2.0f
                    2.0f -> 3.0f
                    else -> 0.5f
                }
                playerViewModel.setPlaybackSpeed(nextSpeed)
            }
        )

        if (recognitionDialogVisible) {
            AlertDialog(
                onDismissRequest = { if (!isRecognizing) recognitionDialogVisible = false },
                title = { Text("Song Recognition") },
                text = {
                    Column {
                        if (isRecognizing) {
                            Text("Listening to the current video audio…")
                        } else if (recognizedSong != null) {
                            Text("${recognizedSong!!.title}\n${recognizedSong!!.artist}", fontWeight = FontWeight.Bold)
                            recognizedSong!!.album?.let { album -> Text("Album: $album") }
                        } else {
                            Text(recognitionError ?: "No result.")
                        }
                    }
                },
                confirmButton = {
                    if (recognizedSong != null) {
                        Button(onClick = {
                            openSongSearch(recognizedSong!!.title, recognizedSong!!.artist)
                            recognitionDialogVisible = false
                        }) { Text("Get Video on YouTube") }
                    } else if (!isRecognizing) {
                        Button(onClick = { recognitionDialogVisible = false }) { Text("Close") }
                    }
                },
                dismissButton = {
                    if (!isRecognizing) {
                        OutlinedButton(onClick = { recognitionDialogVisible = false }) { Text("Cancel") }
                    }
                }
            )
        }

        // Error Card Overlay
        playerError?.let { err ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xEE000000)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Error",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Playback Failed",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = err,
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { currentVideo?.let { playerViewModel.playVideo(it) } },
                                colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.HiPrimaryCyan, contentColor = Color.Black)
                            ) {
                                Text("Retry")
                            }
                            OutlinedButton(
                                onClick = { videoPickerLauncher.launch("video/*") }
                            ) {
                                Text("Open Local Video", color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // 4. Modal Bottom Sheets & Dialogs
        when (activeSheet) {
            ActiveSheet.AUDIO_SETTINGS -> {
                AudioSettingsBottomSheet(
                    audioTracks = availableAudioTracks,
                    volumeBoostPercent = volumeBoostPercent,
                    audioDelayMs = audioDelayMs,
                    onSelectTrack = { playerViewModel.selectAudioTrack(it) },
                    onVolumeBoostChange = { playerViewModel.setVolumeBoost(it) },
                    onAudioDelayChange = { playerViewModel.setAudioDelay(it) },
                    onDismiss = { playerViewModel.closeSheet() }
                )
            }
            ActiveSheet.VIDEO_SETTINGS -> {
                VideoSettingsBottomSheet(
                    currentAspectRatio = aspectRatioMode,
                    onAspectRatioSelected = {
                        playerViewModel.setAspectRatio(it)
                        playerViewModel.closeSheet()
                    },
                    sleepTimerMinutes = sleepTimerMinutes,
                    onSetSleepTimer = { playerViewModel.setSleepTimer(it) },
                    currentThemeMode = currentThemeMode,
                    onThemeSelected = onThemeSelected,
                    onDismiss = { playerViewModel.closeSheet() }
                )
            }
            ActiveSheet.SUBTITLE_SETTINGS -> {
                SubtitleSettingsBottomSheet(
                    subtitleTracks = availableSubtitleTracks,
                    subtitleOffsetMs = subtitleOffsetMs,
                    onSelectTrack = { playerViewModel.selectSubtitleTrack(it) },
                    onSubtitleOffsetChange = { playerViewModel.setSubtitleOffset(it) },
                    onLoadExternalSubtitle = { playerViewModel.loadExternalSubtitle(it) },
                    onOpenCustomizeAppearance = { playerViewModel.openSheet(ActiveSheet.SUBTITLE_CUSTOMIZATION) },
                    onDismiss = { playerViewModel.closeSheet() }
                )
            }
            ActiveSheet.DECODER_TELEMETRY -> {
                VideoInfoDialog(
                    video = currentVideo,
                    telemetry = telemetry,
                    onDismiss = { playerViewModel.closeSheet() }
                )
            }
            ActiveSheet.PLAYLIST_CHOOSER -> {
                MoreOptionsBottomSheet(
                    onLoadSubtitleFile = { subtitlePickerLauncher.launch("*/*") },
                    onFetchSubtitleUrl = { playerViewModel.openSheet(ActiveSheet.FETCH_SUBTITLE_URL_DIALOG) },
                    onDownloadCurrentSubtitle = { playerViewModel.downloadCurrentSubtitle(context) },
                    onOpenSubtitleCustomization = { playerViewModel.openSheet(ActiveSheet.SUBTITLE_CUSTOMIZATION) },
                    onOpenAudioSettings = { playerViewModel.openSheet(ActiveSheet.AUDIO_SETTINGS) },
                    onOpenVideoSettings = { playerViewModel.openSheet(ActiveSheet.VIDEO_SETTINGS) },
                    onOpenTelemetry = { playerViewModel.openSheet(ActiveSheet.DECODER_TELEMETRY) },
                    onOpenRatingKey = { playerViewModel.openSheet(ActiveSheet.TMDB_KEY_DIALOG) },
                    onDismiss = { playerViewModel.closeSheet() }
                )
            }
            ActiveSheet.SUBTITLE_CUSTOMIZATION -> {
                SubtitleCustomizationBottomSheet(
                    config = subtitleStyle,
                    onUpdateStyle = { playerViewModel.updateSubtitleStyle(it) },
                    onDismiss = { playerViewModel.closeSheet() }
                )
            }
            ActiveSheet.FETCH_SUBTITLE_URL_DIALOG -> {
                FetchSubtitleUrlDialog(
                    onFetchUrl = { playerViewModel.fetchSubtitleFromUrl(it) },
                    onDismiss = { playerViewModel.closeSheet() }
                )
            }
            ActiveSheet.TMDB_KEY_DIALOG -> {
                val savedKey by playerViewModel.tmdbApiKey.collectAsState()
                TmdbKeyDialog(
                    currentKey = savedKey,
                    onSaveKey = { playerViewModel.setTmdbApiKey(it) },
                    onDismiss = { playerViewModel.closeSheet() }
                )
            }
            else -> {}
        }
    }
}
