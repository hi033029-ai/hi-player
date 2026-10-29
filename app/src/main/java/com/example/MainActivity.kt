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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.model.PendingDownloadFormat
import com.example.model.PublicWebVideoResolver
import com.example.model.StreamUrlClassifier
import com.example.model.StreamUrlResult
import com.example.model.VideoItem
import com.example.player.DownloadingFilesRegistry
import com.example.ui.components.HiBottomNavigationBar
import com.example.ui.components.HiNavigationBottomSheet
import com.example.ui.components.NavTab
import com.example.ui.components.HiPlayerHeader
import com.example.ui.screens.DiscoverScreen
import com.example.ui.screens.FileManagerScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MusicScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.screens.HiBrowserScreen
import com.example.ui.screens.HI_BROWSER_ACTION_OPEN_URL
import com.example.ui.screens.HI_BROWSER_EXTRA_URL
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.HiPlayerTheme
import com.example.ui.theme.LocalHiPalette
import com.example.ui.theme.getCustomPalette
import com.example.viewmodel.FileManagerViewModel
import com.example.viewmodel.DiscoverViewModel
import com.example.viewmodel.LibraryViewModel
import com.example.viewmodel.MusicViewModel
import com.example.viewmodel.PlayerViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        val SUPPORTED_EXTERNAL_VIDEO_EXTENSIONS = setOf(
            "mp4", "m4v", "mkv", "webm", "mov", "avi", "ts", "mts",
            "m2ts", "3gp", "flv", "wmv", "vob"
        )
    }

    private val libraryViewModel: LibraryViewModel by viewModels()
    private val playerViewModel: PlayerViewModel by viewModels()
    private val musicViewModel: MusicViewModel by viewModels()
    private val fileManagerViewModel: FileManagerViewModel by viewModels()
    private val discoverViewModel: DiscoverViewModel by viewModels()

    private val _isInPipMode = mutableStateOf(false)
    // Android VIEW/SEND intents can arrive before Compose is created or while
    // the library is visible. Keep the URI in Activity-owned state so Compose
    // can switch to the Player only after its screen state exists.
    private val pendingExternalVideoUri = mutableStateOf<Uri?>(null)
    private var pipTransitionRequested = false
    // True while Hi Browser (not the video player) owns the PiP window.
    private val _browserPipActive = mutableStateOf(false)
    // Web page opened from a home-screen shortcut created by Hi Browser.
    private val pendingBrowserUrl = mutableStateOf<String?>(null)
    private val pendingBrowserDownloads = mutableStateOf(false)

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.example.ui.components.HiPlayerLogo.preload(this)

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
                var returnTabFromDiscover by remember { mutableStateOf(NavTab.VIDEOS) }
                // Where Back from browser mode returns to.
                var tabBeforeBrowser by remember { mutableStateOf(NavTab.VIDEOS) }
                androidx.compose.runtime.LaunchedEffect(currentTab) {
                    if (currentTab != NavTab.BROWSER && currentTab != NavTab.DISCOVER) tabBeforeBrowser = currentTab
                }
                var showHubBottomSheet by remember { mutableStateOf(false) }
                var showGlobalSearchDialog by remember { mutableStateOf(false) }
                var showStreamUrlDialog by remember { mutableStateOf(false) }
                var recycleBinOpenRequest by remember { mutableStateOf(0) }
                var globalSearchQuery by remember { mutableStateOf("") }
                var streamUrl by remember { mutableStateOf("") }
                var streamUrlError by remember { mutableStateOf<String?>(null) }
                var isResolvingStreamUrl by remember { mutableStateOf(false) }
                var completeFirstLaunchAfterPermission by remember { mutableStateOf(false) }

                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                val scope = rememberCoroutineScope()
                val mainTabStateHolder = rememberSaveableStateHolder()
                val isPip = _isInPipMode.value
                val externalVideoUri = pendingExternalVideoUri.value
                val browserPip = _browserPipActive.value
                DisposableEffect(Unit) {
                    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                            libraryViewModel.refreshVideos(silent = true)
                            musicViewModel.loadAudioTracks()
                            com.example.data.MediaLibraryScanService.schedule(this@MainActivity)
                        }
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }
                val externalBrowserUrl = pendingBrowserUrl.value
                val openBrowserDownloads = pendingBrowserDownloads.value
                val openResolvedStream: (StreamUrlResult) -> Unit = { result ->
                    val candidate = checkNotNull(result.normalizedUrl)
                    val parsed = Uri.parse(candidate)
                    playerViewModel.playVideo(
                        VideoItem(
                            uri = parsed,
                            title = result.displayTitle
                                ?: parsed.lastPathSegment
                                ?: "Stream",
                            path = candidate,
                            mimeType = result.mimeType ?: "video/*",
                            folderName = "Online streams"
                        )
                    )
                    currentScreen = AppScreen.Player
                    streamUrlError = null
                    isResolvingStreamUrl = false
                    showStreamUrlDialog = false
                }

                // File managers, browsers, and share sheets route here through
                // ACTION_VIEW/ACTION_SEND. Consume the URI only once the app has
                // finished its startup gate, then navigate directly into Player.
                LaunchedEffect(externalVideoUri, settingsLoaded, isShowingSplash) {
                    val uri = externalVideoUri ?: return@LaunchedEffect
                    if (!settingsLoaded || isShowingSplash) return@LaunchedEffect
                    pendingExternalVideoUri.value = null
                    libraryViewModel.addExternalVideo(uri) { video ->
                        libraryViewModel.markVideoOpened(video)
                        playerViewModel.playVideo(video)
                        currentTab = NavTab.VIDEOS
                        currentScreen = AppScreen.Player
                    }
                }

                LaunchedEffect(externalBrowserUrl, openBrowserDownloads, settingsLoaded, isShowingSplash) {
                    if ((externalBrowserUrl == null && !openBrowserDownloads) || !settingsLoaded || isShowingSplash) return@LaunchedEffect
                    if (currentScreen == AppScreen.Player) {
                        playerViewModel.saveCurrentProgress()
                        playerViewModel.stopForNavigation()
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        currentScreen = AppScreen.Main
                    }
                    musicViewModel.closeFullScreenPlayer()
                    currentTab = NavTab.BROWSER
                }

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
                                else -> playerViewModel.stopForNavigation()
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
                            (isPip && !browserPip) || currentScreen == AppScreen.Player -> {
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
                                                playerViewModel.stopForNavigation()
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
                                        if (currentTab != NavTab.BROWSER) {
                                            HiPlayerHeader(
                                                onAccountClick = { currentTab = NavTab.SETTINGS },
                                                showSearch = false,
                                                onSearchClick = null,
                                                onRefreshClick = {
                                                    libraryViewModel.refreshVideos()
                                                    musicViewModel.loadAudioTracks()
                                                    fileManagerViewModel.refreshAll()
                                                },
                                                onStreamClick = if (currentTab == NavTab.FILE_MANAGER) null else ({ showStreamUrlDialog = true }),
                                                extraActions = {
                                                    if (currentTab != NavTab.FILE_MANAGER && currentTab != NavTab.DISCOVER) {
                                                        IconButton(onClick = {
                                                            returnTabFromDiscover = currentTab
                                                            currentTab = NavTab.DISCOVER
                                                        }) {
                                                            Icon(Icons.Default.Explore, contentDescription = "Discover", tint = palette.textPrimary)
                                                        }
                                                    }
                                                    if (currentTab == NavTab.FILE_MANAGER) {
                                                        IconButton(onClick = { recycleBinOpenRequest++ }) {
                                                            Icon(Icons.Default.DeleteSweep, contentDescription = "Recycle Bin", tint = palette.textPrimary)
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    },
                                    bottomBar = {
                                        if (!browserPip) Column {
                                            if (currentTab != com.example.ui.components.NavTab.MUSIC && currentTab != NavTab.BROWSER && currentAudioTrack != null && !isAudioFullScreenOpen) {
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
                                            if (currentTab != NavTab.BROWSER) HiBottomNavigationBar(
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
                                            // Browser mode draws its own bottom bar above the keyboard / system bar.
                                            .padding(
                                                top = paddingValues.calculateTopPadding(),
                                                bottom = if (currentTab == NavTab.BROWSER) 0.dp else paddingValues.calculateBottomPadding(),
                                            )
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
                                                        // Library overflow deletion must not inherit a stale
                                                        // Files multi-select toolbar while the recycle-bin move runs.
                                                        fileManagerViewModel.clearSelection()
                                                        fileManagerViewModel.moveVideosToRecycleBin(selectedVideos) { movedPaths ->
                                                            val moved = selectedVideos.filter { it.path in movedPaths }
                                                            libraryViewModel.removeVideoRecords(moved)
                                                        }
                                                    },
                                                    includeHeader = false
                                                )
                                            }
                                            NavTab.DISCOVER -> {
                                                DiscoverScreen(
                                                    discoverViewModel = discoverViewModel,
                                                    onPlayRemote = { match ->
                                                        playerViewModel.playVideo(match.toVideoItem())
                                                        currentScreen = AppScreen.Player
                                                    },
                                                    onOpenSettings = { currentTab = NavTab.SETTINGS },
                                                    onBack = { currentTab = returnTabFromDiscover }
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
                                            NavTab.BROWSER -> {
                                                mainTabStateHolder.SaveableStateProvider("hi_browser") {
                                                    HiBrowserScreen(
                                                        modifier = Modifier.fillMaxSize(),
                                                        isInPictureInPicture = isPip && browserPip,
                                                        onEnterPictureInPicture = { enterBrowserPipMode() },
                                                        externalUrl = externalBrowserUrl,
                                                        openDownloads = openBrowserDownloads,
                                                        onDownloadsConsumed = { pendingBrowserDownloads.value = false },
                                                        onExternalUrlConsumed = { pendingBrowserUrl.value = null },
                                                        onBack = { currentTab = tabBeforeBrowser },
                                                        appNavigation = {
                                                            HiBottomNavigationBar(
                                                                currentTab = NavTab.BROWSER,
                                                                onTabSelected = { tab ->
                                                                    if (tab != NavTab.MUSIC) musicViewModel.closeFullScreenPlayer()
                                                                    currentTab = tab
                                                                },
                                                                onOpenHubSheet = { showHubBottomSheet = true },
                                                            )
                                                        },
                                                        onPlayBrowserVideo = { url, mimeType, title ->
                                                            // The page already proved this is media: play it directly.
                                                            val result = StreamUrlClassifier.classifyResolvedMediaUrl(
                                                                url,
                                                                suppliedMimeType = mimeType,
                                                                displayTitle = title,
                                                                trustAsDirectSource = true,
                                                            )
                                                            if (result.isPlayableInHiPlayer && result.normalizedUrl != null) {
                                                                openResolvedStream(result)
                                                            } else {
                                                                android.widget.Toast.makeText(
                                                                    this@MainActivity,
                                                                    result.errorMessage ?: "This video can't be played in Hi Player.",
                                                                    android.widget.Toast.LENGTH_SHORT,
                                                                ).show()
                                                            }
                                                        },
                                                        onPlayDirectMedia = { url ->
                                                            val uri = Uri.parse(url)
                                                            if (uri.scheme == "content" || uri.scheme == "file") {
                                                                val localDownload = com.example.downloads.BrowserDownloads.playbackItem(this@MainActivity, uri)
                                                                val mimeType = localDownload?.mimeType ?: runCatching { contentResolver.getType(uri) }.getOrNull() ?: "video/*"
                                                                playerViewModel.playVideo(
                                                                    localDownload ?: VideoItem(
                                                                        uri = uri,
                                                                        title = uri.lastPathSegment ?: "Downloaded video",
                                                                        path = url,
                                                                        mimeType = mimeType,
                                                                        folderName = "Downloads",
                                                                    )
                                                                )
                                                                currentScreen = AppScreen.Player
                                                            } else {
                                                                val result = StreamUrlClassifier.classify(url)
                                                                when {
                                                                    result.isPlayableInHiPlayer && !result.requiresWebResolution -> openResolvedStream(result)
                                                                    result.isPlayableInHiPlayer && result.requiresWebResolution -> {
                                                                        android.widget.Toast.makeText(
                                                                            this@MainActivity,
                                                                            "Checking this page for a public video stream…",
                                                                            android.widget.Toast.LENGTH_SHORT,
                                                                        ).show()
                                                                        scope.launch {
                                                                            val resolution = withContext(Dispatchers.IO) {
                                                                                PublicWebVideoResolver.resolve(url)
                                                                            }
                                                                            val stream = resolution.stream
                                                                            if (stream != null) {
                                                                                openResolvedStream(stream)
                                                                            } else {
                                                                                android.widget.Toast.makeText(
                                                                                    this@MainActivity,
                                                                                    resolution.errorMessage ?: "No public video stream was found on this page.",
                                                                                    android.widget.Toast.LENGTH_LONG,
                                                                                ).show()
                                                                            }
                                                                        }
                                                                    }
                                                                    else -> android.widget.Toast.makeText(
                                                                        this@MainActivity,
                                                                        result.errorMessage ?: "This page does not expose a video stream that Hi Player can play.",
                                                                        android.widget.Toast.LENGTH_LONG,
                                                                    ).show()
                                                                }
                                                            }
                                                        },
                                                    )
                                                }
                                            }
                                            NavTab.FILE_MANAGER -> {
                                                FileManagerScreen(
                                                    fileManagerViewModel = fileManagerViewModel,
                                                    includeHeader = false,
                                                    onOpenVideo = { file ->
                                                        val isPartial = PendingDownloadFormat.isPending(file.name)
                                                        if (isPartial) {
                                                            DownloadingFilesRegistry.markDownloading(file.path, file.sizeBytes)
                                                        }
                                                        val title = PendingDownloadFormat.originalName(file.name)
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
                                                                durationMs = 0L,
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
                                                    discoverViewModel = discoverViewModel,
                                                    onBack = { currentTab = NavTab.VIDEOS },
                                                    onPlayDriveMedia = { video ->
                                                        playerViewModel.playVideo(video)
                                                        currentScreen = AppScreen.Player
                                                    },
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
                                        onDismissRequest = {
                                            if (isResolvingStreamUrl) return@AlertDialog
                                            streamUrlError = null
                                            showStreamUrlDialog = false
                                        },
                                        shape = RoundedCornerShape(24.dp),
                                        containerColor = palette.surfaceElevated,
                                        titleContentColor = palette.textPrimary,
                                        textContentColor = palette.textSecondary,
                                        title = {
                                            Card(
                                                shape = RoundedCornerShape(14.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = palette.primary.copy(alpha = if (palette.isDark) 0.14f else 0.12f)
                                                )
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.PlayCircle,
                                                        contentDescription = null,
                                                        tint = palette.primary
                                                    )
                                                    Spacer(Modifier.width(10.dp))
                                                    Column {
                                                        Text("Play stream", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                                        Text(
                                                            "Open a direct video or public video page",
                                                            color = palette.textSecondary,
                                                            fontSize = 12.sp
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        text = {
                                            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                                                Card(
                                                    shape = RoundedCornerShape(14.dp),
                                                    colors = CardDefaults.cardColors(containerColor = palette.surface),
                                                ) {
                                                    OutlinedTextField(
                                                        value = streamUrl,
                                                        onValueChange = {
                                                            streamUrl = it
                                                            streamUrlError = null
                                                        },
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(6.dp),
                                                        singleLine = true,
                                                        leadingIcon = {
                                                            Icon(
                                                                imageVector = Icons.Default.Link,
                                                                contentDescription = null,
                                                                tint = palette.primary
                                                            )
                                                        },
                                                        label = { Text("Video or stream link") },
                                                        placeholder = { Text("https://example.com/video.m3u8") },
                                                        isError = streamUrlError != null,
                                                        supportingText = {
                                                            Text(
                                                                streamUrlError ?: if (isResolvingStreamUrl) {
                                                                    "Looking for a public direct video source…"
                                                                } else {
                                                                    "Direct MP4, MKV, HLS, DASH, or a public webpage"
                                                                }
                                                            )
                                                        },
                                                        colors = OutlinedTextFieldDefaults.colors(
                                                            focusedBorderColor = palette.primary,
                                                            unfocusedBorderColor = palette.surfaceBorder,
                                                            focusedTextColor = palette.textPrimary,
                                                            unfocusedTextColor = palette.textPrimary,
                                                            focusedLabelColor = palette.primary,
                                                            unfocusedLabelColor = palette.textSecondary,
                                                            cursorColor = palette.primary,
                                                        )
                                                    )
                                                }
                                                Spacer(Modifier.height(12.dp))
                                                Card(
                                                    shape = RoundedCornerShape(12.dp),
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = palette.primary.copy(alpha = if (palette.isDark) 0.09f else 0.08f)
                                                    )
                                                ) {
                                                    Text(
                                                        "Hi Player plays direct media with its own controls. For an ordinary public webpage, it checks standard public video metadata without using logins, cookies, or protected streams.",
                                                        color = palette.textSecondary,
                                                        fontSize = 12.sp,
                                                        modifier = Modifier.padding(12.dp)
                                                    )
                                                }
                                            }
                                        },
                                        confirmButton = {
                                            Button(
                                                onClick = {
                                                val result = StreamUrlClassifier.classify(streamUrl)
                                                if (!result.isPlayableInHiPlayer) {
                                                    streamUrlError = result.errorMessage
                                                } else if (!result.requiresWebResolution) {
                                                    openResolvedStream(result)
                                                } else {
                                                    isResolvingStreamUrl = true
                                                    streamUrlError = null
                                                    scope.launch {
                                                        val resolution = withContext(Dispatchers.IO) {
                                                            PublicWebVideoResolver.resolve(streamUrl)
                                                        }
                                                        resolution.stream?.let(openResolvedStream)
                                                            ?: run {
                                                                isResolvingStreamUrl = false
                                                                streamUrlError = resolution.errorMessage
                                                            }
                                                    }
                                                }
                                                },
                                                enabled = !isResolvingStreamUrl,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = palette.primary,
                                                    contentColor = if (palette.isDark) Color.Black else Color.White
                                                )
                                            ) {
                                                if (isResolvingStreamUrl) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.height(18.dp).width(18.dp),
                                                        color = if (palette.isDark) Color.Black else Color.White,
                                                        strokeWidth = 2.dp
                                                    )
                                                    Spacer(Modifier.width(8.dp))
                                                    Text("Checking")
                                                } else {
                                                    Icon(Icons.Default.PlayCircle, contentDescription = null)
                                                    Spacer(Modifier.width(6.dp))
                                                    Text("Play in Hi Player")
                                                }
                                            }
                                        },
                                        dismissButton = {
                                            TextButton(
                                                enabled = !isResolvingStreamUrl,
                                                onClick = {
                                                    streamUrlError = null
                                                    showStreamUrlDialog = false
                                                }
                                            ) { Text("Cancel", color = palette.textPrimary) }
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
        val incoming = intent ?: return
        if (incoming.action == "com.example.OPEN_BROWSER_DOWNLOADS") {
            pendingBrowserDownloads.value = true
            return
        }
        if (incoming.action == HI_BROWSER_ACTION_OPEN_URL) {
            incoming.getStringExtra(HI_BROWSER_EXTRA_URL)
                ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                ?.let { pendingBrowserUrl.value = it }
            return
        }
        val uri = when (incoming.action) {
            Intent.ACTION_VIEW -> incoming.data
            Intent.ACTION_SEND -> incoming.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                ?: incoming.clipData?.getItemAt(0)?.uri
            else -> null
        } ?: return

        if (isSupportedExternalVideo(incoming, uri)) {
            pendingExternalVideoUri.value = uri
        }
    }

    private fun isSupportedExternalVideo(intent: Intent, uri: Uri): Boolean {
        val mimeType = intent.type ?: contentResolver.getType(uri)
        if (mimeType?.startsWith("video/", ignoreCase = true) == true) return true
        if (mimeType.equals("application/octet-stream", ignoreCase = true)) return true

        val extension = uri.lastPathSegment
            ?.substringBefore('?')
            ?.substringAfterLast('.', "")
            ?.lowercase()
            .orEmpty()
        return extension in SUPPORTED_EXTERNAL_VIDEO_EXTENSIONS
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

    /** Shows the current Hi Browser page in a picture-in-picture window. */
    fun enterBrowserPipMode(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return false
        _browserPipActive.value = true
        pipTransitionRequested = true
        val entered = runCatching {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
            )
        }.getOrDefault(false)
        if (!entered) {
            _browserPipActive.value = false
            pipTransitionRequested = false
        }
        return entered
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
        // Persist normal WebView cookies without changing website expiry rules.
        runCatching { android.webkit.CookieManager.getInstance().flush() }
        super.onStop()
        if (isInPictureInPictureMode || pipTransitionRequested) return
        // Persist the current resume position and aspect mode before the app is
        // backgrounded. Do not reset requestedOrientation here: changing it
        // while entering Recents can recreate the activity and leave the GL
        // surface with a mismatched aspect transform when the app returns.
        playerViewModel.saveCurrentProgress()
        val settings = libraryViewModel.playerSettings.value
        if (!(settings.backgroundPlayEnabled && playerViewModel.engine.isBackgroundPlayActive.value)) {
            playerViewModel.pauseForAppBackground()
        }
    }

    override fun onStart() {
        super.onStart()
        if (!isInPictureInPictureMode && !pipTransitionRequested) {
            playerViewModel.resumeAfterAppForeground()
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
        } else if (_browserPipActive.value) {
            _browserPipActive.value = false
            pipTransitionRequested = false
        } else if (!(libraryViewModel.playerSettings.value.backgroundPlayEnabled && playerViewModel.engine.isBackgroundPlayActive.value)) {
            pipTransitionRequested = false
            playerViewModel.stopForNavigation()
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
            if (Build.VERSION.SDK_INT >= 34) list.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
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
