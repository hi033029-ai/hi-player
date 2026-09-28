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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.VideoSize
import androidx.media3.common.CueGroup
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
import com.example.ui.AspectRatioOverlayIndicator
import com.example.ui.HoldSeekOverlayIndicator
import com.example.ui.HoldSeekSide
import com.example.ui.HdrPresetMenu
import com.example.ui.HdrColor
import com.example.ui.SeekPreviewHud
import com.example.ui.GlVideoSurface
import com.example.player.HdrColorModeManager
import com.example.player.ColorPresets
import com.example.player.ColorTargetAdjustment
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
    val equalizerBandsDb by playerViewModel.engine.equalizerBandsDb.collectAsState()
    val audioDelayMs by playerViewModel.engine.audioDelayMs.collectAsState()
    val subtitleOffsetMs by playerViewModel.engine.subtitleOffsetMs.collectAsState()
    val activeSheet by playerViewModel.activeSheet.collectAsState()
    val showStartOverPrompt by playerViewModel.showStartOverPrompt.collectAsState()
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
    var videoPixelWidthHeightRatio by remember { mutableStateOf(1f) }
    var videoRotationDegrees by remember { mutableStateOf(0) }
    var subtitleCueText by remember { mutableStateOf(emptyList<String>()) }
    var containerSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var showAspectPicker by remember { mutableStateOf(false) }
    var showAspectRatioIndicator by remember { mutableStateOf(false) }
    var isRatioTransitioning by remember { mutableStateOf(false) }
    var showSpeedOverlay by remember { mutableStateOf(false) }
    var showZoomOverlay by remember { mutableStateOf(false) }
    var holdSeekSide by remember { mutableStateOf<HoldSeekSide?>(null) }
    var showHdrPresetMenu by remember { mutableStateOf(false) }
    var showHdrCustomizer by remember { mutableStateOf(false) }
    var equalizerInteractionTick by remember { mutableStateOf(0) }
    var customHdrPreset by remember { mutableStateOf(ColorPresets.CUSTOM) }
    var selectedHdrColor by remember { mutableStateOf(HdrColor.RED) }
    var hdrColorIntensity by remember { mutableStateOf(0.5f) }
    var hdrColorIntensities by remember { mutableStateOf(emptyMap<HdrColor, Float>()) }
    var hdrInteractionTick by remember { mutableStateOf(0) }

    DisposableEffect(playerViewModel.engine.getPlayer()) {
        val player = playerViewModel.engine.getPlayer()
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                videoWidth = videoSize.width
                videoHeight = videoSize.height
                videoPixelWidthHeightRatio = videoSize.pixelWidthHeightRatio
                videoRotationDegrees = videoSize.unappliedRotationDegrees
            }

            override fun onCues(cueGroup: CueGroup) {
                subtitleCueText = cueGroup.cues
                    .mapNotNull { it.text?.toString()?.trim()?.takeIf(String::isNotEmpty) }
            }
        }
        player.addListener(listener)
        videoWidth = player.videoSize.width
        videoHeight = player.videoSize.height
        videoPixelWidthHeightRatio = player.videoSize.pixelWidthHeightRatio
        videoRotationDegrees = player.videoSize.unappliedRotationDegrees
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
    // after a surface hand-off. Keep the mask brief: a 2.5-second fallback made
    // every normal open look like an unnecessary long black loading screen.
    LaunchedEffect(currentVideo?.uri, playerViewModel.engine.getPlayer()) {
        delay(850)
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

    fun playNextVideoAutomatically() {
        val current = currentVideo ?: return
        val index = videoQueue.indexOfFirst { it.uri == current.uri }
        val next = videoQueue.getOrNull(index + 1) ?: return
        playerViewModel.playVideo(next)
    }

    DisposableEffect(playerViewModel.engine, videoQueue) {
        playerViewModel.engine.onVideoEnded = { playNextVideoAutomatically() }
        onDispose { playerViewModel.engine.onVideoEnded = null }
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

    LaunchedEffect(showStartOverPrompt) {
        if (showStartOverPrompt) {
            delay(5_000)
            playerViewModel.dismissStartOverPrompt()
        }
    }

    LaunchedEffect(aspectRatioMode) {
        // Ratio changes are user interactions; never leave the controls hidden
        // because the playback auto-hide timer fired at the same time.
        areControlsVisible = true
        isRatioTransitioning = true
        delay(360)
        isRatioTransitioning = false
    }

    LaunchedEffect(activeSheet, equalizerPreset, equalizerInteractionTick) {
        if (activeSheet == ActiveSheet.EQUALIZER) {
            delay(5_000)
            if (activeSheet == ActiveSheet.EQUALIZER) {
                playerViewModel.closeSheet()
            }
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

    LaunchedEffect(showHdrPresetMenu, showHdrCustomizer, hdrInteractionTick) {
        if (showHdrPresetMenu || showHdrCustomizer) {
            delay(3_000)
            showHdrPresetMenu = false
            showHdrCustomizer = false
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
            playerViewModel.brightnessLevel.value
                ?: activity?.window?.attributes?.screenBrightness
                ?: 0.5f
        }
        surface.currentVolumeLevel = {
            val audio = activity?.getSystemService(android.content.Context.AUDIO_SERVICE)
                as? android.media.AudioManager
            val max = audio?.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC) ?: 15
            val current = audio?.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) ?: 0
            val systemLevel = if (max > 0) current.toFloat() / max else 0f
            val boostLevel = playerViewModel.engine.volumeBoostPercent.value / 100f
            playerViewModel.volumeLevel.value
                ?: (systemLevel + boostLevel).coerceIn(0f, 2f)
        }
        surface.onBrightnessLevel = { level ->
            activity?.let { act ->
                val attributes = act.window.attributes
                attributes.screenBrightness = playerViewModel.onBrightnessGestureLevel(level)
                act.window.attributes = attributes
            }
        }
        surface.onVolumeLevel = { level -> playerViewModel.onVolumeGestureLevel(level) }
        surface.onVolumeDelta = { delta -> playerViewModel.onVolumeGesture(delta) }
        surface.onScrubStart = { playerViewModel.onScrubStart() }
        surface.onScrubMove = { delta -> playerViewModel.onScrubMove(delta) }
        surface.onScrubEnd = { playerViewModel.onScrubEnd() }
        surface.onSingleTap = { areControlsVisible = !areControlsVisible }
        surface.onDoubleTapLeft = { smoothSeekController.seekBy(-10_000L) }
        surface.onDoubleTapCenter = { playerViewModel.togglePlayPause() }
        surface.onDoubleTapRight = { smoothSeekController.seekBy(10_000L) }
        surface.onHoldSeekStart = { isForward ->
            holdSeekSide = if (isForward) HoldSeekSide.FORWARD else HoldSeekSide.REWIND
        }
        surface.onHoldSeekEnd = { holdSeekSide = null }
        surface.onLongPressVideo = { showAspectPicker = true }
        surface.onPinchZoom = { zoomFactor ->
            playerViewModel.setVideoScale(videoScale * zoomFactor)
            showZoomOverlay = true
        }
        surface.onHdrToggle = {
            showHdrPresetMenu = true
            showHdrCustomizer = false
            hdrInteractionTick++
        }
        surface.onHdrLongPress = {}
        surface.isInteractionLocked = { isScreenLocked }
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
    LaunchedEffect(areControlsVisible, isPlaying, aspectRatioMode, isRatioTransitioning) {
        if (areControlsVisible && isPlaying && !isScreenLocked && !isRatioTransitioning) {
            delay(3000)
            if (!isRatioTransitioning) areControlsVisible = false
        }
    }

    LaunchedEffect(showAspectRatioIndicator, aspectRatioMode) {
        if (showAspectRatioIndicator) {
            delay(1400)
            showAspectRatioIndicator = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clipToBounds()
            .onSizeChanged { containerSize = it },
        contentAlignment = Alignment.Center,
    ) {
        // 1. GLSL video surface layer with the selected HDR color preset.
        val ratioScale = computeVideoRatioScale(
            containerSize,
            videoWidth,
            videoHeight,
            aspectRatioMode,
            videoPixelWidthHeightRatio,
            videoRotationDegrees,
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
                videoSurface.invalidate()
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Keep the native SurfaceView at the stable container size.
                    // Resizing a SurfaceView itself can move its buffer one frame
                    // behind Compose on some devices, causing the video to jump
                    // sideways or lose its center. A centered layer transform
                    // preserves the same final geometry without relocating the
                    // native surface.
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
                showAspectRatioIndicator = true
            },
            modifier = Modifier.align(Alignment.CenterStart),
        )

        AspectRatioOverlayIndicator(
            visible = showAspectRatioIndicator,
            mode = aspectRatioMode,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 58.dp),
        )

        SpeedOverlayIndicator(
            visible = showSpeedOverlay,
            speedLabel = formatSpeedLabel(playbackSpeed),
            modifier = Modifier.align(Alignment.Center),
        )

        ZoomOverlayIndicator(
            visible = showZoomOverlay,
            scale = videoScale,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 58.dp),
        )

        HoldSeekOverlayIndicator(
            side = holdSeekSide,
            modifier = Modifier
                .align(if (holdSeekSide == HoldSeekSide.REWIND) Alignment.CenterStart else Alignment.CenterEnd)
                .padding(horizontal = 28.dp),
        )

        SeekPreviewHud(
            targetMs = scrubTimeMs,
            deltaMs = scrubDeltaMs,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 70.dp),
        )

        HdrPresetMenu(
            visible = showHdrPresetMenu || showHdrCustomizer,
            selected = hdrColorPreset,
            customPreset = customHdrPreset,
            customizing = showHdrCustomizer,
            selectedColor = selectedHdrColor,
            colorIntensity = hdrColorIntensity,
            onColorSelected = {
                selectedHdrColor = it
                val savedIntensity = hdrColorIntensities[it] ?: 0.5f
                hdrColorIntensity = savedIntensity
                hdrInteractionTick++
                val nextPreset = customHdrPreset.copy(
                    name = ColorPresets.CUSTOM.name,
                    targetHue = -1f,
                    red = 1f,
                    green = 1f,
                    blue = 1f,
                    targetAdjustments = hdrColorTargetAdjustments(hdrColorIntensities),
                )
                customHdrPreset = nextPreset
                playerViewModel.engine.setHdrColorPreset(nextPreset)
                if (!hdrEnhanceActive) playerViewModel.toggleHdrEnhance()
            },
            onColorIntensityChange = { intensity ->
                hdrInteractionTick++
                hdrColorIntensity = intensity
                hdrColorIntensities = hdrColorIntensities + (selectedHdrColor to intensity)
                val updatedIntensities = hdrColorIntensities + (selectedHdrColor to intensity)
                hdrColorIntensities = updatedIntensities
                val nextPreset = customHdrPreset.copy(
                    name = ColorPresets.CUSTOM.name,
                    targetHue = -1f,
                    red = 1f,
                    green = 1f,
                    blue = 1f,
                    targetAdjustments = hdrColorTargetAdjustments(updatedIntensities),
                )
                customHdrPreset = nextPreset
                playerViewModel.engine.setHdrColorPreset(nextPreset)
                if (!hdrEnhanceActive) playerViewModel.toggleHdrEnhance()
            },
            onCustomize = {
                showHdrCustomizer = true
                hdrInteractionTick++
            },
            onCustomPresetChange = { preset ->
                hdrInteractionTick++
                customHdrPreset = preset.copy(name = ColorPresets.CUSTOM.name)
                playerViewModel.engine.setHdrColorPreset(customHdrPreset)
                if (!hdrEnhanceActive) playerViewModel.toggleHdrEnhance()
            },
            onSelect = { preset ->
                hdrInteractionTick++
                playerViewModel.engine.setHdrColorPreset(preset)
                if (preset.name == ColorPresets.HDR_DISABLED.name) {
                    playerViewModel.disableHdrEnhance()
                } else if (!hdrEnhanceActive) {
                    playerViewModel.toggleHdrEnhance()
                }
                showHdrPresetMenu = false
                showHdrCustomizer = false
            },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 28.dp, bottom = 150.dp),
        )

        if (subtitleCueText.isNotEmpty() && !isScreenLocked) {
            Text(
                text = subtitleCueText.joinToString("\n"),
                color = Color.White,
                fontSize = subtitleStyle.fontSizeSp.coerceIn(12, 48).sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 112.dp)
                    .background(Color.Black.copy(alpha = 0.58f), RoundedCornerShape(5.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }

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
            onToggleLock = {
                val locking = !isScreenLocked
                playerViewModel.engine.setScreenLocked(locking)
                areControlsVisible = true
                if (locking) {
                    playerViewModel.closeSheet()
                    showAspectPicker = false
                    showHdrPresetMenu = false
                    showHdrCustomizer = false
                }
            },
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
            // A single tap opens the HDR choices. HDR is changed only after the
            // user selects a preset, including the explicit Off option.
            onToggleHdrEnhance = {
                showHdrPresetMenu = true
                showHdrCustomizer = false
                hdrInteractionTick++
            },
            onLongPressHdrEnhance = {},
            onToggleSubtitles = {
                // CC now opens the caption picker on a normal tap. Cycling tracks
                // was undiscoverable and prevented users from seeing the embedded
                // languages in a movie unless they knew to long-press the icon.
                playerViewModel.refreshCaptionTracks()
                playerViewModel.openSheet(ActiveSheet.SUBTITLE_SETTINGS)
            },
            onOpenAudioSettings = { playerViewModel.openSheet(ActiveSheet.AUDIO_SETTINGS) },
            onOpenEqualizer = {
                equalizerInteractionTick++
                playerViewModel.openSheet(ActiveSheet.EQUALIZER)
            },
            onOpenVideoSettings = { playerViewModel.openSheet(ActiveSheet.VIDEO_SETTINGS) },
            onOpenTelemetry = { playerViewModel.openSheet(ActiveSheet.DECODER_TELEMETRY) },
            onCycleAspectRatio = {
                playerViewModel.cycleAspectRatio()
                showAspectRatioIndicator = true
            },
            onLongPressAspectRatio = { showAspectPicker = true },
            onAspectRatioSelected = {
                playerViewModel.setAspectRatio(it)
                showAspectRatioIndicator = true
            },
            onCycleSpeed = {
                val nextSpeed = nextPlaybackSpeed(playbackSpeed)
                playerViewModel.setPlaybackSpeed(nextSpeed)
                showSpeedOverlay = true
            }
        )

        if (showStartOverPrompt && !isScreenLocked) {
            Button(
                onClick = { playerViewModel.playCurrentVideoFromStart() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 62.dp, bottom = 174.dp)
                    .height(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.92f),
                    contentColor = Color.Black,
                ),
            ) {
                Text("Start over", fontSize = 11.sp)
            }
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
            ActiveSheet.EQUALIZER -> {
                EqualizerBottomSheet(
                    selectedPreset = equalizerPreset,
                    customBandsDb = equalizerBandsDb,
                    onSelectPreset = {
                        playerViewModel.engine.setEqualizerPreset(it)
                        equalizerInteractionTick++
                        if (it != "Custom") {
                            playerViewModel.closeSheet()
                        }
                    },
                    onCustomBandChange = { index, gainDb ->
                        playerViewModel.engine.setCustomEqualizerBand(index, gainDb)
                        equalizerInteractionTick++
                    },
                    onDismiss = { playerViewModel.closeSheet() },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 128.dp),
                )
            }
            ActiveSheet.VIDEO_SETTINGS -> {
                VideoSettingsBottomSheet(
                    currentAspectRatio = aspectRatioMode,
                    onAspectRatioSelected = {
                        playerViewModel.setAspectRatio(it)
                        showAspectRatioIndicator = true
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
    mode: AspectRatioMode,
    pixelWidthHeightRatio: Float = 1f,
    unappliedRotationDegrees: Int = 0,
): Pair<Float, Float> {
    if (container.width == 0 || container.height == 0 || videoWidth == 0 || videoHeight == 0) {
        return 1f to 1f
    }
    val containerRatio = container.width.toFloat() / container.height.toFloat()
    val safePixelRatio = pixelWidthHeightRatio.takeIf { it.isFinite() && it > 0f } ?: 1f
    val encodedRatio = videoWidth.toFloat() * safePixelRatio / videoHeight.toFloat()
    val normalizedRotation = ((unappliedRotationDegrees % 360) + 360) % 360
    val videoRatio = if (normalizedRotation == 90 || normalizedRotation == 270) {
        1f / encodedRatio
    } else {
        encodedRatio
    }
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

private fun hdrColorTargetAdjustments(
    intensities: Map<HdrColor, Float>
): List<ColorTargetAdjustment> {
    return intensities.map { (color, intensity) ->
        ColorTargetAdjustment(
            hue = hdrColorHue(color.color),
            red = (1f + (color.color.red - 0.5f) * intensity).coerceIn(0.5f, 1.5f),
            green = (1f + (color.color.green - 0.5f) * intensity).coerceIn(0.5f, 1.5f),
            blue = (1f + (color.color.blue - 0.5f) * intensity).coerceIn(0.5f, 1.5f),
        )
    }
}

private fun hdrColorHue(color: Color): Float {
    val max = maxOf(color.red, color.green, color.blue)
    val min = minOf(color.red, color.green, color.blue)
    val chroma = max - min
    if (chroma < 0.001f) return 0f
    val raw = when (max) {
        color.red -> ((color.green - color.blue) / chroma) % 6f
        color.green -> ((color.blue - color.red) / chroma) + 2f
        else -> ((color.red - color.green) / chroma) + 4f
    }
    return ((raw / 6f) + 1f) % 1f
}
