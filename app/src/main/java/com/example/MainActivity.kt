package com.example

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.model.VideoItem
import com.example.ui.components.HiBottomNavigationBar
import com.example.ui.components.HiNavigationBottomSheet
import com.example.ui.components.NavTab
import com.example.ui.screens.FileManagerScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MusicScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.HiPlayerTheme
import com.example.ui.theme.LocalHiPalette
import com.example.viewmodel.FileManagerViewModel
import com.example.viewmodel.LibraryViewModel
import com.example.viewmodel.MusicViewModel
import com.example.viewmodel.PlayerViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

sealed class AppScreen {
    object Main : AppScreen()
    object Player : AppScreen()
}

class MainActivity : ComponentActivity() {

    private val libraryViewModel: LibraryViewModel by viewModels()
    private val playerViewModel: PlayerViewModel by viewModels()
    private val musicViewModel: MusicViewModel by viewModels()
    private val fileManagerViewModel: FileManagerViewModel by viewModels()

    private val _isInPipMode = mutableStateOf(false)

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIntent(intent)

        setContent {
            val settings by libraryViewModel.playerSettings.collectAsState()
            val settingsLoaded by libraryViewModel.isSettingsLoaded.collectAsState()
            var isShowingSplash by remember { mutableStateOf(true) }

            HiPlayerTheme(
                themeMode = settings.themeMode,
                uiTextScale = (settings.uiTextSizeSp / 14f).coerceIn(0.75f, 1.5f)
            ) {
                val palette = LocalHiPalette.current
                var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Main) }
                var currentTab by remember { mutableStateOf(NavTab.VIDEOS) }
                var showHubBottomSheet by remember { mutableStateOf(false) }

                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                val scope = rememberCoroutineScope()
                val isPip = _isInPipMode.value

                val videos by libraryViewModel.allVideos.collectAsState()
                val audios by musicViewModel.audioList.collectAsState()
                val currentAudioTrack by musicViewModel.currentTrack.collectAsState()
                val isAudioPlaying by musicViewModel.isPlaying.collectAsState()
                val currentAudioPos by musicViewModel.currentPositionMs.collectAsState()
                val audioDuration by musicViewModel.durationMs.collectAsState()
                val activeSubtitle by musicViewModel.activeSubtitle.collectAsState()
                val isAudioFullScreenOpen by musicViewModel.isFullScreenPlayerOpen.collectAsState()
                val audioEqPreset by musicViewModel.equalizerPreset.collectAsState()

                if (!settingsLoaded) {
                    // Hold on the bare logo/background until the real persisted
                    // isFirstLaunch value has loaded from disk, so we never briefly
                    // show onboarding on a normal (non-first) launch.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFFFFFFF))
                    )
                } else if (isShowingSplash) {
                    SplashScreen(
                        isFirstLaunch = settings.isFirstLaunch,
                        onFinish = { selectedTheme ->
                            if (settings.isFirstLaunch) {
                                libraryViewModel.setThemeMode(selectedTheme)
                                libraryViewModel.setFirstLaunchCompleted()
                            }
                            isShowingSplash = false
                            libraryViewModel.refreshVideos()
                            musicViewModel.loadAudioTracks()
                        }
                    )
                } else {
                    // Dynamic Permission Request on Launch (Only prompt dialog on first launch)
                    RequestPermissionsOnLaunch(
                        isFirstLaunch = settings.isFirstLaunch,
                        onGranted = {
                            libraryViewModel.refreshVideos()
                            musicViewModel.loadAudioTracks()
                        }
                    )

                    // Handle system back press
                    androidx.activity.compose.BackHandler(enabled = currentScreen != AppScreen.Main && !isPip) {
                        if (currentScreen == AppScreen.Player) {
                            playerViewModel.saveCurrentProgress()
                            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            currentScreen = AppScreen.Main
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(palette.background)
                    ) {
                        when {
                            isPip || currentScreen == AppScreen.Player -> {
                                PlayerScreen(
                                    playerViewModel = playerViewModel,
                                    currentThemeMode = settings.themeMode,
                                    onThemeSelected = { mode ->
                                        libraryViewModel.setThemeMode(mode)
                                    },
                                    onBack = {
                                        playerViewModel.saveCurrentProgress()
                                        val settings = libraryViewModel.playerSettings.value
                                        // In-app "back" (returning to the library) previously
                                        // just switched screens and left ExoPlayer running with
                                        // no visible Surface underneath - same silent
                                        // audio-only bug as the system back/home case, just
                                        // reachable without ever leaving the app. Stop playback
                                        // here unless the user opted into PiP or background audio.
                                        when {
                                            settings.autoPipEnabled && playerViewModel.engine.isPlaying.value -> {
                                                enterPipMode()
                                            }
                                            settings.backgroundPlayEnabled -> {
                                                // Intentionally left playing (audio-only is the point).
                                            }
                                            else -> {
                                                playerViewModel.engine.pause()
                                            }
                                        }
                                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                        currentScreen = AppScreen.Main
                                    },
                                    onEnterPip = {
                                        enterPipMode()
                                    }
                                )
                            }
                            else -> {
                                Scaffold(
                                    modifier = Modifier.fillMaxSize(),
                                    containerColor = palette.background,
                                    bottomBar = {
                                        Column {
                                            if (currentTab != com.example.ui.components.NavTab.MUSIC && currentAudioTrack != null && !isAudioFullScreenOpen) {
                                                com.example.ui.screens.WavyNowPlayingBottomSheet(
                                                    track = currentAudioTrack!!,
                                                    isPlaying = isAudioPlaying,
                                                    currentPos = currentAudioPos,
                                                    duration = if (audioDuration > 0) audioDuration else currentAudioTrack!!.durationMs,
                                                    eqPreset = audioEqPreset,
                                                    activeSubtitle = activeSubtitle,
                                                    onTogglePlay = { musicViewModel.togglePlayPause() },
                                                    onNext = { musicViewModel.playNext() },
                                                    onPrevious = { musicViewModel.playPrevious() },
                                                    onSeek = { musicViewModel.seekTo(it) },
                                                    onOpenEq = { currentTab = com.example.ui.components.NavTab.MUSIC },
                                                    onOpenSubtitleSearch = { currentTab = com.example.ui.components.NavTab.MUSIC },
                                                    onOpenVideoSearch = { currentTab = com.example.ui.components.NavTab.MUSIC },
                                                    onCancel = { musicViewModel.stopTrack() },
                                                     onExpandFullScreen = { musicViewModel.openFullScreenPlayer() }
                                                )
                                            }
                                            HiBottomNavigationBar(
                                                currentTab = currentTab,
                                                onTabSelected = { tab ->
                                                    currentTab = tab
                                                },
                                                onOpenHubSheet = {
                                                    showHubBottomSheet = true
                                                }
                                            )
                                        }
                                    }
                                ) { paddingValues ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(paddingValues)
                                    ) {
                                        when (currentTab) {
                                            NavTab.VIDEOS -> {
                                                HomeScreen(
                                                    libraryViewModel = libraryViewModel,
                                                    onVideoSelected = { video ->
                                                        playerViewModel.playVideo(video)
                                                        currentScreen = AppScreen.Player
                                                    },
                                                    onOpenSettings = {
                                                        currentTab = NavTab.SETTINGS
                                                    }
                                                )
                                            }
                                            NavTab.MUSIC -> {
                                                MusicScreen(
                                                    musicViewModel = musicViewModel,
                                                    onPlayVideo = { uri ->
                                                        libraryViewModel.viewModelScope.launch {
                                                            val video = com.example.data.MediaScanner.createVideoItemFromUri(
                                                                libraryViewModel.getApplication(), uri
                                                            )
                                                            playerViewModel.playVideo(video)
                                                            currentScreen = AppScreen.Player
                                                        }
                                                    }
                                                )
                                            }
                                            NavTab.FILE_MANAGER -> {
                                                FileManagerScreen(
                                                    fileManagerViewModel = fileManagerViewModel
                                                )
                                            }
                                            NavTab.SETTINGS -> {
                                                SettingsScreen(
                                                    libraryViewModel = libraryViewModel,
                                                    onBack = { currentTab = NavTab.VIDEOS }
                                                )
                                            }
                                        }
                                    }
                                }

                                if (currentTab != NavTab.MUSIC && currentAudioTrack != null && isAudioFullScreenOpen) {
                                     com.example.ui.screens.FullScreenAudioPlayerScreen(
                                         track = currentAudioTrack!!,
                                         isPlaying = isAudioPlaying,
                                         currentPos = currentAudioPos,
                                         duration = if (audioDuration > 0) audioDuration else currentAudioTrack!!.durationMs,
                                         eqPreset = audioEqPreset,
                                         activeSubtitle = activeSubtitle,
                                         onTogglePlay = { musicViewModel.togglePlayPause() },
                                         onNext = { musicViewModel.playNext() },
                                         onPrevious = { musicViewModel.playPrevious() },
                                         onSeek = { musicViewModel.seekTo(it) },
                                         onOpenEq = { currentTab = NavTab.MUSIC },
                                         onOpenSubtitleSearch = { currentTab = NavTab.MUSIC },
                                         onOpenVideoSearch = { currentTab = NavTab.MUSIC },
                                         onBack = { musicViewModel.closeFullScreenPlayer() },
                                         onCancel = { musicViewModel.stopTrack() }
                                     )
                                 }

                                 // Modal Navigation Hub Bottom Sheet
                                if (showHubBottomSheet) {
                                    HiNavigationBottomSheet(
                                        sheetState = sheetState,
                                        currentTab = currentTab,
                                        videoCount = videos.size,
                                        audioCount = audios.size,
                                        onTabSelected = { tab ->
                                            currentTab = tab
                                            showHubBottomSheet = false
                                        },
                                        onDismiss = {
                                            showHubBottomSheet = false
                                        },
                                        onRefreshStorage = {
                                            libraryViewModel.refreshVideos()
                                            musicViewModel.loadAudioTracks()
                                            showHubBottomSheet = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val action = intent?.action
        val data: Uri? = intent?.data

        if (action == Intent.ACTION_VIEW && data != null) {
            libraryViewModel.addExternalVideo(data) { videoItem ->
                playerViewModel.playVideo(videoItem)
            }
        }
    }

    fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val aspectRatio = Rational(16, 9)
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)
                .build()
            enterPictureInPictureMode(params)
        }
    }

    // onUserLeaveHint previously entered PiP any time video was playing,
    // regardless of the user's "Auto Picture-in-Picture" toggle. It now
    // respects the persisted setting: PiP only auto-triggers when the user
    // has actually turned it on.
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val settings = libraryViewModel.playerSettings.value
        if (playerViewModel.engine.isPlaying.value && settings.autoPipEnabled) {
            enterPipMode()
        }
    }

    // Neither Auto-PiP nor Background Play were previously wired to any
    // lifecycle callback, so leaving the app while a video was playing (and
    // PiP not entering, e.g. dismissed via the PiP window's own close/cancel
    // control) left ExoPlayer running with no visible Surface: audio kept
    // playing with nothing on screen. Pause playback on backgrounding unless
    // the user has explicitly enabled PiP or background audio playback.
    override fun onStop() {
        super.onStop()
        if (isInPictureInPictureMode) return
        val settings = libraryViewModel.playerSettings.value
        if (!settings.autoPipEnabled && !settings.backgroundPlayEnabled) {
            if (playerViewModel.engine.isPlaying.value) {
                playerViewModel.engine.pause()
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        _isInPipMode.value = isInPictureInPictureMode
        // Tapping the PiP window's own close/"cancel" control finishes the
        // activity, which triggers onStop() above and pauses playback there
        // if the user hasn't opted into background audio. Nothing further
        // needed here beyond keeping the mode flag in sync.
    }
}

@Composable
fun RequestPermissionsOnLaunch(
    isFirstLaunch: Boolean = false,
    onGranted: () -> Unit
) {
    val context = LocalContext.current

    val permissionsToRequest = remember {
        val list = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.READ_MEDIA_VIDEO)
            list.add(Manifest.permission.READ_MEDIA_AUDIO)
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            list.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        list.toTypedArray()
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val anyGranted = permissions.values.any { it }
        if (anyGranted) {
            onGranted()
        } else {
            onGranted() // Proceed smoothly to main UI even if permissions rejected
        }
    }

    LaunchedEffect(Unit) {
        val hasPermissions = permissionsToRequest.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (!hasPermissions && isFirstLaunch) {
            launcher.launch(permissionsToRequest)
        } else {
            onGranted()
        }
    }
}
