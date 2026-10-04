package com.example

import android.Manifest
import android.app.PictureInPictureParams
import android.app.PendingIntent
import android.app.RemoteAction
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.graphics.drawable.Icon
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.example.player.DownloadingFilesRegistry
import com.example.ui.components.HiBottomNavigationBar
import com.example.ui.components.HiNavigationBottomSheet
import com.example.ui.components.NavTab
import com.example.ui.components.HiPlayerHeader
import com.example.ui.screens.FileManagerScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MusicScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.HiPlayerTheme
import com.example.ui.theme.LocalHiPalette
import com.example.ui.theme.getCustomPalette
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

    private companion object {
        const val PIP_ACTION_PLAY_PAUSE = "com.example.hiplayer.PIP_PLAY_PAUSE"
        const val PIP_ACTION_PREVIOUS = "com.example.hiplayer.PIP_PREVIOUS"
        const val PIP_ACTION_NEXT = "com.example.hiplayer.PIP_NEXT"
        const val PIP_ACTION_CANCEL = "com.example.hiplayer.PIP_CANCEL"
    }

    private val libraryViewModel: LibraryViewModel by viewModels()
    private val playerViewModel: PlayerViewModel by viewModels()
    private val musicViewModel: MusicViewModel by viewModels()
    private val fileManagerViewModel: FileManagerViewModel by viewModels()

    private val _isInPipMode = mutableStateOf(false)
    private var pipTransitionRequested = false

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
                uiTextScale = (settings.uiTextSizeSp / 14f).coerceIn(0.75f, 1.5f),
                customPalette = getCustomPalette(settings),
            ) {
                val palette = LocalHiPalette.current
                var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Main) }
                var currentTab by remember { mutableStateOf(NavTab.VIDEOS) }
                var showHubBottomSheet by remember { mutableStateOf(false) }
                var showGlobalSearchDialog by remember { mutableStateOf(false) }
                var showStreamUrlDialog by remember { mutableStateOf(false) }
                var recycleBinOpenRequest by remember { mutableStateOf(0) }
                var globalSearchQuery by remember { mutableStateOf("") }
                var streamUrl by remember { mutableStateOf("") }
                var completeFirstLaunchAfterPermission by remember { mutableStateOf(false) }

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
                val lyricsText by musicViewModel.lyricsText.collectAsState()
                val currentLyricLineIndex by musicViewModel.currentLyricLineIndex.collectAsState()
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
                        onFinish = { selectedTheme, textSizeSp ->
                            if (settings.isFirstLaunch) {
                                libraryViewModel.setThemeMode(selectedTheme)
                                libraryViewModel.setUiTextSize(textSizeSp)
                                libraryViewModel.setFirstLaunchCompleted()
                            }
                            isShowingSplash = false
                            libraryViewModel.refreshVideos()
                            musicViewModel.loadAudioTracks()
                        },
                        onSkip = {
                            libraryViewModel.setThemeMode(com.example.data.AppThemeMode.CYAN_NEON_DARK)
                            libraryViewModel.setUiTextSize(14)
                            completeFirstLaunchAfterPermission = true
                            isShowingSplash = false
                            libraryViewModel.refreshVideos()
                            musicViewModel.loadAudioTracks()
                        }
                    )
                } else {
                    // Dynamic Permission Request on Launch (Only prompt dialog on first launch)
                    RequestPermissionsOnLaunch(
                        isFirstLaunch = settings.isFirstLaunch || completeFirstLaunchAfterPermission,
                        onGranted = {
                            if (completeFirstLaunchAfterPermission) {
                                libraryViewModel.setFirstLaunchCompleted()
                                completeFirstLaunchAfterPermission = false
                            }
                            libraryViewModel.refreshVideos()
                            musicViewModel.loadAudioTracks()
                        }
                    )

                    // Handle system back press
                    androidx.activity.compose.BackHandler(enabled = currentScreen != AppScreen.Main && !isPip) {
                        if (currentScreen == AppScreen.Player) {
                            playerViewModel.saveCurrentProgress()
                            when {
                                settings.autoPipEnabled && playerViewModel.engine.isPlaying.value -> {
                                    enterPipMode()
                                }
                                settings.backgroundPlayEnabled && playerViewModel.engine.isBackgroundPlayActive.value -> {
                                    // The player utility action explicitly opted into audio-only playback.
                                }
                                else -> playerViewModel.engine.stopForNavigation()
                            }
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
                                    videoQueue = videos,
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
                                            settings.backgroundPlayEnabled && playerViewModel.engine.isBackgroundPlayActive.value -> {
                                                // Intentionally left playing (audio-only is the point).
                                            }
                                            else -> {
                                                playerViewModel.engine.stopForNavigation()
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
                                    topBar = {
                                        HiPlayerHeader(
                                            showSearch = false,
                                            onSearchClick = null,
                                            onRefreshClick = {
                                                libraryViewModel.refreshVideos()
                                                musicViewModel.loadAudioTracks()
                                                fileManagerViewModel.refreshAll()
                                            },
                                            onStreamClick = if (currentTab == NavTab.FILE_MANAGER) null else ({ showStreamUrlDialog = true }),
                                            extraActions = if (currentTab == NavTab.FILE_MANAGER) {
                                                {
                                                    IconButton(onClick = { recycleBinOpenRequest++ }) {
                                                        Icon(Icons.Default.DeleteSweep, contentDescription = "Recycle Bin", tint = palette.textPrimary)
                                                    }
                                                }
                                            } else null
                                        )
                                    },
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
                                                    onCancel = { musicViewModel.stopTrack() },
                                                     onExpandFullScreen = { musicViewModel.openFullScreenPlayer() }
                                                )
                                            }
                                            HiBottomNavigationBar(
                                                currentTab = currentTab,
                                                onTabSelected = { tab ->
                                                    if (tab != NavTab.MUSIC) musicViewModel.closeFullScreenPlayer()
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
                                                        libraryViewModel.markVideoOpened(video)
                                                        playerViewModel.playVideo(video)
                                                        currentScreen = AppScreen.Player
                                                    },
                                                    onPlayFromStart = { video ->
                                                        libraryViewModel.markVideoOpened(video)
                                                        playerViewModel.playVideo(video, startPositionOverride = 0L)
                                                        currentScreen = AppScreen.Player
                                                    },
                                                    onOpenSettings = {
                                                        currentTab = NavTab.SETTINGS
                                                    },
                                                    onMoveVideosToRecycleBin = { selectedVideos ->
                                                        fileManagerViewModel.moveVideosToRecycleBin(selectedVideos) { movedPaths ->
                                                            val moved = selectedVideos.filter { it.path in movedPaths }
                                                            libraryViewModel.removeVideoRecords(moved)
                                                        }
                                                    },
                                                    includeHeader = false
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
                                                            libraryViewModel.markVideoOpened(video)
                                                            playerViewModel.playVideo(video)
                                                            currentScreen = AppScreen.Player
                                                        }
                                                    },
                                                    includeHeader = false,
                                                    libraryViewModel = libraryViewModel
                                                )
                                            }
                                            NavTab.FILE_MANAGER -> {
                                                FileManagerScreen(
                                                    fileManagerViewModel = fileManagerViewModel,
                                                    includeHeader = false,
                                                    onOpenVideo = { file ->
                                                        val lowerName = file.name.lowercase()
                                                        val isPartial = listOf(
                                                            ".crdownload", ".download", ".part", ".partial", ".tmp"
                                                        ).any(lowerName::endsWith)
                                                        if (isPartial) {
                                                            DownloadingFilesRegistry.markDownloading(file.path, file.sizeBytes)
                                                        }
                                                        val title = file.name
                                                            .removeSuffix(".crdownload")
                                                            .removeSuffix(".download")
                                                            .removeSuffix(".part")
                                                            .removeSuffix(".tmp")
                                                        val extension = title.substringAfterLast('.', "mp4").lowercase()
                                                        val mimeType = when (extension) {
                                                            "mkv" -> "video/x-matroska"
                                                            "webm" -> "video/webm"
                                                            "mov" -> "video/quicktime"
                                                            "avi" -> "video/x-msvideo"
                                                            "ts" -> "video/mp2t"
                                                            else -> "video/mp4"
                                                        }
                                                        val video = VideoItem(
                                                                id = file.path.hashCode().toLong(),
                                                                uri = Uri.fromFile(file.file),
                                                                title = title,
                                                                durationMs = if (isPartial) -1L else 0L,
                                                                sizeBytes = file.sizeBytes,
                                                                width = if (isPartial) 0 else 1920,
                                                                height = if (isPartial) 0 else 1080,
                                                                mimeType = mimeType,
                                                                codec = if (isPartial) "Downloading" else "HEVC/H.264",
                                                                path = file.path,
                                                                folderName = file.file.parentFile?.name ?: "Files",
                                                                isPartialDownload = isPartial
                                                            )
                                                        libraryViewModel.markVideoOpened(video)
                                                        playerViewModel.playVideo(video)
                                                        currentScreen = AppScreen.Player
                                                    },
                                                    onFilesChanged = { libraryViewModel.refreshVideos() },
                                                    recycleBinOpenRequest = recycleBinOpenRequest,
                                                    onRecycleBinRequestConsumed = { recycleBinOpenRequest = 0 }
                                                )
                                            }
                                            NavTab.SETTINGS -> {
                                                SettingsScreen(
                                                    libraryViewModel = libraryViewModel,
                                                    onBack = { currentTab = NavTab.VIDEOS },
                                                    includeHeader = false
                                                )
                                            }
                                        }
                                    }
                                }

                                if (showGlobalSearchDialog) {
                                    AlertDialog(
                                        onDismissRequest = { showGlobalSearchDialog = false },
                                        title = { Text("Search Hi Player") },
                                        text = {
                                            OutlinedTextField(
                                                value = globalSearchQuery,
                                                onValueChange = { globalSearchQuery = it },
                                                singleLine = true,
                                                label = { Text("Search videos, music, and files") }
                                            )
                                        },
                                        confirmButton = {
                                            Button(onClick = {
                                                libraryViewModel.setSearchQuery(globalSearchQuery)
                                                musicViewModel.setSearchQuery(globalSearchQuery)
                                                fileManagerViewModel.setSearchQuery(globalSearchQuery)
                                                showGlobalSearchDialog = false
                                            }) { Text("Search") }
                                        },
                                        dismissButton = {
                                            Button(onClick = { showGlobalSearchDialog = false }) { Text("Cancel") }
                                        }
                                    )
                                }

                                if (showStreamUrlDialog) {
                                    AlertDialog(
                                        onDismissRequest = { showStreamUrlDialog = false },
                                        title = { Text("Play Stream URL") },
                                        text = {
                                            OutlinedTextField(
                                                value = streamUrl,
                                                onValueChange = { streamUrl = it },
                                                singleLine = true,
                                                label = { Text("HTTP(S) video URL") },
                                                placeholder = { Text("https://example.com/video.mp4") }
                                            )
                                        },
                                        confirmButton = {
                                            Button(onClick = {
                                                val candidate = streamUrl.trim()
                                                val parsed = runCatching { Uri.parse(candidate) }.getOrNull()
                                                if (parsed != null && (parsed.scheme == "http" || parsed.scheme == "https") && parsed.host != null) {
                                                    playerViewModel.playVideo(
                                                        VideoItem(
                                                            uri = parsed,
                                                            title = parsed.lastPathSegment ?: "Stream",
                                                            path = candidate,
                                                            mimeType = "video/*"
                                                        )
                                                    )
                                                    currentScreen = AppScreen.Player
                                                    showStreamUrlDialog = false
                                                }
                                            }) { Text("Play") }
                                        },
                                        dismissButton = {
                                            Button(onClick = { showStreamUrlDialog = false }) { Text("Cancel") }
                                        }
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
                                            if (tab != NavTab.MUSIC) musicViewModel.closeFullScreenPlayer()
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
        setIntent(intent)
        when (intent.action) {
            PIP_ACTION_PLAY_PAUSE -> playerViewModel.togglePlayPause()
            PIP_ACTION_PREVIOUS -> playerViewModel.playAdjacentVideo(libraryViewModel.allVideos.value, -1)
            PIP_ACTION_NEXT -> playerViewModel.playAdjacentVideo(libraryViewModel.allVideos.value, 1)
            PIP_ACTION_CANCEL -> finishAndRemoveTask()
            else -> handleIntent(intent)
        }
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
            pipTransitionRequested = true
            val aspectRatio = Rational(16, 9)
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)
                .setActions(buildPipActions())
                .build()
            enterPictureInPictureMode(params)
        }
    }

    private fun buildPipActions(): List<RemoteAction> {
        fun action(icon: Int, title: String, command: String, requestCode: Int) = RemoteAction(
            Icon.createWithResource(this, icon), title, title,
            PendingIntent.getActivity(
                this, requestCode,
                Intent(this, MainActivity::class.java).setAction(command),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        val playing = playerViewModel.engine.isPlaying.value
        return listOf(
            action(android.R.drawable.ic_media_previous, "Previous", PIP_ACTION_PREVIOUS, 21),
            action(if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, if (playing) "Pause" else "Play", PIP_ACTION_PLAY_PAUSE, 22),
            action(android.R.drawable.ic_media_next, "Next", PIP_ACTION_NEXT, 23),
            action(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", PIP_ACTION_CANCEL, 24),
        )
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
        if (isInPictureInPictureMode || pipTransitionRequested) return
        // Persist the current resume position and aspect mode before the app is
        // backgrounded. Do not reset requestedOrientation here: changing it
        // while entering Recents can recreate the activity and leave the GL
        // surface with a mismatched aspect transform when the app returns.
        playerViewModel.saveCurrentProgress()
        val settings = libraryViewModel.playerSettings.value
        if (!(settings.backgroundPlayEnabled && playerViewModel.engine.isBackgroundPlayActive.value)) {
            playerViewModel.engine.stopForNavigation()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        _isInPipMode.value = isInPictureInPictureMode
        if (isInPictureInPictureMode) {
            pipTransitionRequested = false
        } else if (!(libraryViewModel.playerSettings.value.backgroundPlayEnabled && playerViewModel.engine.isBackgroundPlayActive.value)) {
            pipTransitionRequested = false
            playerViewModel.engine.stopForNavigation()
        }
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
