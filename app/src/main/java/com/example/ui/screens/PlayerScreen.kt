package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.VideoSize
import com.example.model.AspectRatioMode
import com.example.model.VideoItem
import com.example.ui.components.AudioSettingsBottomSheet
import com.example.ui.components.ControlsOverlay
import com.example.ui.components.FetchSubtitleUrlDialog
import com.example.ui.components.TmdbKeyDialog

import com.example.ui.components.SubtitleCustomizationBottomSheet
import com.example.ui.components.SubtitleSettingsBottomSheet
import com.example.ui.components.VideoInfoDialog
import com.example.ui.components.EqualizerBottomSheet
import com.example.ui.components.VideoSettingsBottomSheet
import com.example.ui.components.AspectRatioPickerOverlay
import com.example.ui.components.SpeedOverlayIndicator
import com.example.ui.ZoomOverlayIndicator
import com.example.ui.HdrPresetMenu
import com.example.ui.SeekPreviewHud
import com.example.ui.GlVideoSurface
import com.example.player.HdrColorModeManager
import com.example.player.ColorPresets
import com.example.player.formatSpeedLabel
import com.example.player.nextPlaybackSpeed
import com.example.player.rememberSmoothSeekController
import com.example.viewmodel.ActiveSheet
import com.example.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    playerViewModel: PlayerViewModel,
    videoQueue: List<VideoItem> = emptyList(),
    onBack: () -> Unit,
    onEnterPip: () -> Unit,
    currentThemeMode: com.example.data.AppThemeMode = com.example.data.AppThemeMode.CYAN_NEON_DARK,
    onThemeSelected: (com.example.data.AppThemeMode) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val hdrColorModeManager = remember(activity, playerViewModel.engine.getPlayer()) {
        activity?.let { HdrColorModeManager(it) }
    }
    val smoothSeekController = rememberSmoothSeekController(playerViewModel.engine.getPlayer())

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
    val equalizerPreset by playerViewModel.engine.equalizerPreset.collectAsState()
    val audioDelayMs by playerViewModel.engine.audioDelayMs.collectAsState()
    val subtitleOffsetMs by playerViewModel.engine.subtitleOffsetMs.collectAsState()
    val activeSheet by playerViewModel.activeSheet.collectAsState()
    val currentRating by playerViewModel.currentRating.collectAsState()
    val sleepTimerMinutes by playerViewModel.sleepTimerMinutesLeft.collectAsState()
    val playerError by playerViewModel.engine.playerError.collectAsState()
    // New features state
    val videoScale by playerViewModel.videoScale.collectAsState()
    val subtitleStyle by playerViewModel.subtitleStyle.collectAsState()
    val hdrEnhanceActive by playerViewModel.hdrEnhanceActive.collectAsState()
    val hdrColorPreset by playerViewModel.engine.hdrColorPreset.collectAsState()
    val isHdrContent by playerViewModel.engine.isHdrContent.collectAsState()
    val is4kContent by playerViewModel.engine.is4kContent.collectAsState()
    val wideColorGamutEnabled by playerViewModel.wideColorGamutEnabled.collectAsState()
    val screenOrientation by playerViewModel.screenOrientation.collectAsState()
    // The window manager reports display HDR capability; the icon must reflect
    // the user-controlled grading effect state so tapping it visibly changes
    // exposure and saturation.
    val managedHdrActive = hdrEnhanceActive
    val managedHdrSwitching = hdrColorModeManager?.isSwitching ?: false

    // Attach before the first selected HDR track reaches the surface so the
    // window is already in HDR output mode when MediaCodec renders frame one.
    DisposableEffect(hdrColorModeManager, playerViewModel.engine.getPlayer()) {
        hdrColorModeManager?.attach(playerViewModel.engine.getPlayer())
        onDispose { hdrColorModeManager?.release() }
    }

    LaunchedEffect(hdrColorModeManager, hdrEnhanceActive) {
        hdrColorModeManager?.setUserEnabled(hdrEnhanceActive)
    }

    // HUD States
    val brightnessLevel by playerViewModel.brightnessLevel.collectAsState()
    val volumeLevel by playerViewModel.volumeLevel.collectAsState()
    val scrubTimeMs by playerViewModel.scrubTimeMs.collectAsState()
    val scrubDeltaMs by playerViewModel.scrubDeltaMs.collectAsState()

    var areControlsVisible by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var firstFrameRendered by remember { mutableStateOf(false) }
    var videoWidth by remember { mutableStateOf(0) }
    var videoHeight by remember { mutableStateOf(0) }
    var surfaceSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var showAspectPicker by remember { mutableStateOf(false) }
    var showSpeedOverlay by remember { mutableStateOf(false) }
    var showZoomOverlay by remember { mutableStateOf(false) }
    var showHdrPresetMenu by remember { mutableStateOf(false) }

    DisposableEffect(playerViewModel.engine.getPlayer()) {
        val player = playerViewModel.engine.getPlayer()
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                videoWidth = videoSize.width
                videoHeight = videoSize.height
            }
        }
        player.addListener(listener)
        videoWidth = player.videoSize.width
        videoHeight = player.videoSize.height
        onDispose { player.removeListener(listener) }
    }

    // Keep the native surface path, but mask the initial surface/HDR negotiation
    // until Media3 confirms that the first decoded frame is on screen.
    DisposableEffect(playerViewModel.engine.getPlayer(), currentVideo?.uri) {
        firstFrameRendered = false
        val player = playerViewModel.engine.getPlayer()
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onRenderedFirstFrame() {
                firstFrameRendered = true
            }
        }
        player.addListener(listener)
        // The listener can be registered after Media3 has already rendered the
        // first frame when the screen is restored. Do not leave the video hidden
        // in that case.
        firstFrameRendered = player.videoSize.width > 0 &&
            (player.playbackState == Player.STATE_READY || player.currentPosition > 0)
        onDispose { player.removeListener(listener) }
    }

    // Safety valve for devices/codecs that do not dispatch onRenderedFirstFrame
    // after a surface hand-off. Audio may already be playing, so a permanent
    // black mask is worse than revealing the surface while it finishes settling.
    LaunchedEffect(currentVideo?.uri, playerViewModel.engine.getPlayer()) {
        delay(2500)
        if (!firstFrameRendered) firstFrameRendered = true
    }

    fun playAdjacentVideo(step: Int) {
        val current = currentVideo ?: return
        if (videoQueue.size < 2) return
        val index = videoQueue.indexOfFirst { it.uri == current.uri }
        if (index < 0) return
        val nextIndex = (index + step + videoQueue.size) % videoQueue.size
        playerViewModel.playVideo(videoQueue[nextIndex])
    }

    // File picker for opening a local video.
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

    // Handle orientation changes
    LaunchedEffect(screenOrientation) {
        activity?.requestedOrientation = screenOrientation
    }

    LaunchedEffect(showAspectPicker) {
        if (showAspectPicker) {
            delay(4000)
            showAspectPicker = false
        }
    }

    LaunchedEffect(showSpeedOverlay, playbackSpeed) {
        if (showSpeedOverlay) {
            delay(900)
            showSpeedOverlay = false
        }
    }

    LaunchedEffect(showZoomOverlay, videoScale) {
        if (showZoomOverlay) {
            delay(900)
            showZoomOverlay = false
        }
    }

    fun configureGlSurface(surface: GlVideoSurface) {
        surface.onBrightnessDelta = { delta ->
            activity?.let { act ->
                val attributes = act.window.attributes
                attributes.screenBrightness = playerViewModel.onBrightnessGesture(
                    delta,
                    attributes.screenBrightness,
                )
                act.window.attributes = attributes
            }
        }
        surface.currentBrightnessLevel = {
            val value = activity?.window?.attributes?.screenBrightness ?: -1f
            if (value >= 0f) value else 0.5f
        }
        surface.currentVolumeLevel = {
            val audio = activity?.getSystemService(android.content.Context.AUDIO_SERVICE)
                as? android.media.AudioManager
            val max = audio?.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC) ?: 15
            val current = audio?.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) ?: 0
            val systemLevel = if (max > 0) current.toFloat() / max else 0f
            val boostLevel = playerViewModel.engine.volumeBoostPercent.value / 100f
            (systemLevel + boostLevel).coerceIn(0f, 2f)
        }
        surface.onVolumeDelta = { delta -> playerViewModel.onVolumeGesture(delta) }
        surface.onScrubStart = { playerViewModel.onScrubStart() }
        surface.onScrubMove = { delta -> playerViewModel.onScrubMove(delta) }
        surface.onScrubEnd = { playerViewModel.onScrubEnd() }
        surface.onSingleTap = { areControlsVisible = !areControlsVisible }
        surface.onDoubleTapLeft = { smoothSeekController.seekBy(-10_000L) }
        surface.onDoubleTapCenter = { playerViewModel.togglePlayPause() }
        surface.onDoubleTapRight = { smoothSeekController.seekBy(10_000L) }
        surface.onLongPressVideo = { showAspectPicker = true }
        surface.onPinchZoom = { zoomFactor ->
            playerViewModel.setVideoScale(videoScale * zoomFactor)
            showZoomOverlay = true
        }
        surface.onHdrToggle = {
            val enabling = !hdrEnhanceActive
            playerViewModel.toggleHdrEnhance()
            if (enabling) surface.showPresetPopup(hdrColorPreset.name)
        }
        surface.onPresetSelected = { preset ->
            playerViewModel.engine.setHdrColorPreset(preset)
            if (preset.name == ColorPresets.HDR_DISABLED.name) {
                playerViewModel.disableHdrEnhance()
            } else if (!hdrEnhanceActive) {
                playerViewModel.toggleHdrEnhance()
            }
        }
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
    LaunchedEffect(activity, isHdrContent, is4kContent, wideColorGamutEnabled) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // HdrColorModeManager owns HDR transitions. This effect only applies
            // the user's wide-gamut preference while the selected stream is SDR.
            if (!isHdrContent) {
                activity?.window?.colorMode = if (wideColorGamutEnabled) {
                    ActivityInfo.COLOR_MODE_WIDE_COLOR_GAMUT
                } else {
                    ActivityInfo.COLOR_MODE_DEFAULT
                }
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
        // 1. GLSL video surface layer with the selected HDR color preset.
        val ratioScale = computeVideoRatioScale(
            surfaceSize,
            videoWidth,
            videoHeight,
            aspectRatioMode
        )

        AndroidView(
            factory = { ctx ->
                GlVideoSurface(ctx).apply {
                    configureGlSurface(this)
                    setPlayer(playerViewModel.engine.getPlayer())
                    setColorPreset(if (hdrEnhanceActive) hdrColorPreset else ColorPresets.NEUTRAL)
                }
            },
            update = { videoSurface ->
                configureGlSurface(videoSurface)
                videoSurface.setPlayer(playerViewModel.engine.getPlayer())
                videoSurface.setColorPreset(if (hdrEnhanceActive) hdrColorPreset else ColorPresets.NEUTRAL)
                videoSurface.pivotX = videoSurface.width / 2f
                videoSurface.pivotY = videoSurface.height / 2f
                videoSurface.requestLayout()
                videoSurface.invalidate()
            },
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { surfaceSize = it }
                .graphicsLayer {
                    scaleX = ratioScale.first * videoScale
                    scaleY = ratioScale.second * videoScale
                }
        )

        val surfaceMaskAlpha by animateFloatAsState(
            targetValue = if (firstFrameRendered) 0f else 1f,
            animationSpec = tween(150),
            label = "firstFrameMaskAlpha"
        )
        if (surfaceMaskAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(surfaceMaskAlpha)
                    .background(Color.Black)
            )
        }

        AspectRatioPickerOverlay(
            visible = showAspectPicker,
            current = aspectRatioMode,
            onSelect = {
                playerViewModel.setAspectRatio(it)
                showAspectPicker = false
            },
            modifier = Modifier.align(Alignment.CenterStart),
        )

        SpeedOverlayIndicator(
            visible = showSpeedOverlay,
            speedLabel = formatSpeedLabel(playbackSpeed),
            modifier = Modifier.align(Alignment.Center),
        )

        ZoomOverlayIndicator(
            visible = showZoomOverlay,
            scale = videoScale,
            modifier = Modifier.align(Alignment.Center),
        )

        SeekPreviewHud(
            targetMs = scrubTimeMs,
            deltaMs = scrubDeltaMs,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 70.dp),
        )

        HdrPresetMenu(
            visible = showHdrPresetMenu,
            selected = hdrColorPreset,
            onSelect = { preset ->
                playerViewModel.engine.setHdrColorPreset(preset)
                if (preset.name == ColorPresets.HDR_DISABLED.name) {
                    playerViewModel.disableHdrEnhance()
                } else if (!hdrEnhanceActive) {
                    playerViewModel.toggleHdrEnhance()
                }
                showHdrPresetMenu = false
            },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 112.dp, bottom = 142.dp),
        )

        // 2. UI Controls Overlay Layer
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
            videoScale = videoScale,
            rating = currentRating,
            onTogglePlayPause = { playerViewModel.togglePlayPause() },
            onSeekTo = { playerViewModel.seekTo(it) },
            onRewind10 = { playerViewModel.seekRelative(-10_000L) },
            onFastForward10 = { playerViewModel.seekRelative(10_000L) },
            onPrevious = { playAdjacentVideo(-1) },
            onNext = { playAdjacentVideo(1) },
            onBack = onBack,
            onToggleLock = { playerViewModel.engine.setScreenLocked(!isScreenLocked) },
            onRotateScreen = { playerViewModel.cycleScreenOrientation() },
            onZoomIn = { playerViewModel.zoomIn() },
            onZoomOut = { playerViewModel.zoomOut() },
            onEnterPip = onEnterPip,
            onToggleBgPlay = { playerViewModel.engine.setBackgroundPlay(!isBgPlayActive) },
            isMuted = isMuted,
            onToggleMute = {
                isMuted = !isMuted
                playerViewModel.engine.getPlayer().volume = if (isMuted) 0f else 1f
            },
            onOpenFile = { videoPickerLauncher.launch("video/*") },
            isHdrEnhanceActive = managedHdrActive,
            isHdrSwitching = managedHdrSwitching,
            onToggleHdrEnhance = {
                val enabling = !hdrEnhanceActive
                playerViewModel.toggleHdrEnhance()
                showHdrPresetMenu = enabling
            },
            onToggleSubtitles = {
                // CC now opens the caption picker on a normal tap. Cycling tracks
                // was undiscoverable and prevented users from seeing the embedded
                // languages in a movie unless they knew to long-press the icon.
                playerViewModel.refreshCaptionTracks()
                playerViewModel.openSheet(ActiveSheet.SUBTITLE_SETTINGS)
            },
            onOpenAudioSettings = { playerViewModel.openSheet(ActiveSheet.AUDIO_SETTINGS) },
            onOpenEqualizer = { playerViewModel.openSheet(ActiveSheet.EQUALIZER) },
            onOpenVideoSettings = { playerViewModel.openSheet(ActiveSheet.VIDEO_SETTINGS) },
            onOpenTelemetry = { playerViewModel.openSheet(ActiveSheet.DECODER_TELEMETRY) },
            onCycleAspectRatio = { playerViewModel.cycleAspectRatio() },
            onLongPressAspectRatio = { showAspectPicker = true },
            onAspectRatioSelected = { playerViewModel.setAspectRatio(it) },
            onCycleSpeed = {
                val nextSpeed = nextPlaybackSpeed(playbackSpeed)
                playerViewModel.setPlaybackSpeed(nextSpeed)
                showSpeedOverlay = true
            }
        )

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
            ActiveSheet.EQUALIZER -> {
                EqualizerBottomSheet(
                    selectedPreset = equalizerPreset,
                    onSelectPreset = { playerViewModel.engine.setEqualizerPreset(it) },
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

private fun computeVideoRatioScale(
    container: androidx.compose.ui.unit.IntSize,
    videoWidth: Int,
    videoHeight: Int,
    mode: AspectRatioMode
): Pair<Float, Float> {
    if (container.width == 0 || container.height == 0 || videoWidth == 0 || videoHeight == 0) {
        return 1f to 1f
    }
    val containerRatio = container.width.toFloat() / container.height.toFloat()
    val videoRatio = videoWidth.toFloat() / videoHeight.toFloat()
    return when (mode) {
        AspectRatioMode.STRETCH -> 1f to 1f
        AspectRatioMode.FIT,
        AspectRatioMode.ORIGINAL -> if (videoRatio > containerRatio) {
            1f to (containerRatio / videoRatio)
        } else {
            (videoRatio / containerRatio) to 1f
        }
        AspectRatioMode.FILL_CROP -> if (videoRatio > containerRatio) {
            (videoRatio / containerRatio) to 1f
        } else {
            1f to (containerRatio / videoRatio)
        }
        AspectRatioMode.IMAX_DIGITAL,
        AspectRatioMode.IMAX_ORIGINAL,
        AspectRatioMode.CINEMA_21_9 -> {
            val targetRatio = mode.targetRatio ?: videoRatio
            if (targetRatio > containerRatio) {
                (targetRatio / containerRatio) to 1f
            } else {
                1f to (containerRatio / targetRatio)
            }
        }
    }
}
