package com.example.browser.gecko

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.auth.GoogleAccountViewModel
import com.example.downloads.BrowserDownloads
import com.example.model.StreamUrlClassifier
import com.example.ui.components.GoogleAccountDialog
import com.example.ui.screens.BrowserDownloadsPanel
import com.example.ui.screens.BrowserNavigationHost
import com.example.ui.screens.BrowserSearchProvider
import com.example.ui.screens.BrowserTabBackPolicy
import com.example.ui.screens.GeckoBrowserArguments
import com.example.ui.screens.OrbitBrowserStore
import com.example.ui.screens.normalizeBrowserInput

/** A detached approved transfer must outlive its browser UI while its foreground lease runs. */
private data class RetainedGeckoBrowser(val services: GeckoBrowserServices, val store: OrbitBrowserStore)
private object RetainedGeckoBrowsers {
    private val transfers = GeckoDetachedTransfers<RetainedGeckoBrowser>()
    fun retain(value: RetainedGeckoBrowser) = transfers.retain(value)
    fun take(): RetainedGeckoBrowser? = transfers.take()
    fun finish(services: GeckoBrowserServices) {
        transfers.removeFirst { it.services === services }?.services?.close()
    }
}

/** Application-only owner; Activities and result registries are rebound, never retained. */
internal class GeckoBrowserOwner(private val application: Application) : ViewModel() {
    var store = OrbitBrowserStore(application)
        private set
    private var owned: GeckoBrowserServices? = null
    var failed by mutableStateOf(false)
        private set
    fun acquire(): GeckoBrowserServices? {
        if (failed) return null
        owned?.let { return it }
        RetainedGeckoBrowsers.take()?.let { retained ->
            store = retained.store
            owned = retained.services
            return retained.services
        }
        return try {
            lateinit var services: GeckoBrowserServices
            services = GeckoBrowserServices(application, null, store, onCloseRequested = { id -> services.coordinator.closeTab(id) })
            val priorStateChanged = services.downloads.onStateChanged
            services.downloads.onStateChanged = { state ->
                priorStateChanged?.invoke(state)
                if (state is GeckoDownloadState.Finished || state is GeckoDownloadState.Idle) {
                    // Close only if it remains detached; a reopened browser may have reclaimed it.
                    Handler(Looper.getMainLooper()).post { RetainedGeckoBrowsers.finish(services) }
                }
            }
            owned = services
            services
        } catch (_: Exception) { failed = true; null }
          catch (_: LinkageError) { failed = true; null }
    }
    fun release() {
        val services = owned
        owned = null
        failed = false
        if (services == null) return
        if (services.downloads.state is GeckoDownloadState.Active) {
            // Do not retain a UI callback or reopen the player after the user exits.
            services.downloads.clearPendingPlayback()
            RetainedGeckoBrowsers.retain(RetainedGeckoBrowser(services, store))
        } else services.close()
    }
    override fun onCleared() {
        if (Looper.myLooper() == Looper.getMainLooper()) release()
        else Handler(Looper.getMainLooper()).post { release() }
    }
}

private fun Context.browserActivity(): ComponentActivity? {
    var current = this
    val seen = java.util.IdentityHashMap<Context, Boolean>()
    while (seen.put(current, true) == null) {
        if (current is ComponentActivity) return current
        current = (current as? ContextWrapper)?.baseContext ?: return null
    }
    return null
}

@Composable
internal fun GeckoBrowserEntry(arguments: GeckoBrowserArguments, onUseWebView: () -> Unit) {
    val context = LocalContext.current
    val activity = context.browserActivity()
    if (activity == null) { BrowserUnavailable(onUseWebView, arguments.onBack); return }
    val application = context.applicationContext as Application
    val factory = remember(application) { object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = GeckoBrowserOwner(application) as T
    } }
    val owner: GeckoBrowserOwner = viewModel(viewModelStoreOwner = activity, factory = factory)
    val services = owner.acquire()
    if (services == null) { BrowserUnavailable(onUseWebView, arguments.onBack); return }
    val coordinator = services.coordinator
    val latest by rememberUpdatedState(arguments)
    val downloads by BrowserDownloads.downloads.collectAsStateWithLifecycle()
    var provider by remember(owner) { mutableStateOf(BrowserSearchProvider.entries.firstOrNull { it.name == owner.store.searchProviderName } ?: BrowserSearchProvider.GOOGLE) }
    var showSettings by remember { mutableStateOf(false) }
    var voiceUnavailable by remember { mutableStateOf(false) }
    val accountModel: GoogleAccountViewModel = viewModel(viewModelStoreOwner = activity)
    var showAccount by remember { mutableStateOf(false) }
    var confirmDetectedMedia by remember { mutableStateOf(false) }
    val accountProfile by accountModel.profile.collectAsStateWithLifecycle()
    val accountHooks = remember(accountProfile) {
        GeckoAccountHooks(email = { accountProfile?.email }, signedIn = { accountProfile != null },
            name = { accountProfile?.name }, photoUrl = { accountProfile?.photoUrl }, openAccount = { showAccount = true })
    }
    val fullscreen = coordinator.activeSnapshot.fullscreen
    fun navigate(raw: String) {
        val url = normalizeBrowserInput(raw, provider) ?: return
        val media = StreamUrlClassifier.classify(url)
        if (!com.example.ui.screens.isOrbitYoutube(url) && media.isPlayableInHiPlayer && !media.requiresWebResolution) latest.onPlayDirectMedia(url)
        else { services.secureRouting.leaveTab(coordinator.activeId); coordinator.load(url) }
    }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(::navigate)
    }
    DisposableEffect(services, activity) {
        val registry = activity.activityResultRegistry
        services.bind(activity, registry)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> coordinator.controller.setHostActive(true)
                Lifecycle.Event.ON_STOP -> coordinator.controller.setHostActive(latest.isInPictureInPicture)
                else -> Unit
            }
        }
        activity.lifecycle.addObserver(observer)
        coordinator.controller.setHostActive(activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) || latest.isInPictureInPicture)
        onDispose { activity.lifecycle.removeObserver(observer); coordinator.controller.setHostActive(false); services.unbind(registry) }
    }
    LaunchedEffect(arguments.isInPictureInPicture) { coordinator.controller.setHostActive(activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) || arguments.isInPictureInPicture) }
    DisposableEffect(fullscreen, activity) {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        if (fullscreen) controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { if (fullscreen) controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
    LaunchedEffect(arguments.externalUrl) { arguments.externalUrl?.let { navigate(it); latest.onExternalUrlConsumed() } }
    BackHandler {
        val activeId = coordinator.activeId
        val snapshot = coordinator.activeSnapshot
        when (BrowserTabBackPolicy.backAction(
            showingHome = snapshot.url.isBlank() && services.secureRouting.route(activeId) == null,
            canGoBack = snapshot.canGoBack,
        )) {
            BrowserTabBackPolicy.BackAction.PAGE_HISTORY -> coordinator.back()
            BrowserTabBackPolicy.BackAction.HOME -> {
                services.secureRouting.leaveTab(activeId)
                coordinator.home()
            }
            BrowserTabBackPolicy.BackAction.EXIT_BROWSER -> latest.onBack()
        }
    }
    BrowserNavigationHost(modifier = arguments.modifier.fillMaxSize(), enabled = !fullscreen && !arguments.isInPictureInPicture, navigation = arguments.appNavigation) {
        Box(Modifier.fillMaxSize().then(if (fullscreen || arguments.isInPictureInPicture) Modifier else Modifier.windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)))) {
            GeckoBrowserChrome(
                coordinator = coordinator, store = owner.store,
                providerNames = BrowserSearchProvider.entries.map { it.displayName }, selectedProvider = provider.displayName,
                onProviderSelect = { index -> BrowserSearchProvider.entries.getOrNull(index)?.let { provider = it; owner.store.searchProviderName = it.name } },
                normalizeAddress = { normalizeBrowserInput(it, provider) },
                onVoiceSearch = {
                    try { voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)) }
                    catch (_: Exception) { voiceUnavailable = true }
                },
                onSettings = { showSettings = true }, onExit = { owner.release(); latest.onBack() },
                downloadsContent = { query -> BrowserDownloadsPanel(downloads, query,
                    onPlay = { id -> BrowserDownloads.playInPlayer(context, id, latest.onPlayDirectMedia) },
                    onCancelGecko = services::cancelDownload) },
                appNavigation = arguments.appNavigation, services = services,
                account = accountHooks,
                onPlayDetected = { confirmDetectedMedia = true },
                openDownloads = arguments.openDownloads, onDownloadsConsumed = arguments.onDownloadsConsumed,
            )
        }
    }
    if (confirmDetectedMedia) AlertDialog(
        onDismissRequest = { confirmDetectedMedia = false },
        title = { Text("Open media in Hi Player?") },
        text = { Text("Only the media address, type and title are handed off. Website cookies and DRM keys stay in Gecko.") },
        confirmButton = { TextButton(onClick = {
            confirmDetectedMedia = false
            val media = coordinator.activeSnapshot
            media.mediaUrl?.let { url ->
                val playBrowserVideo = latest.onPlayBrowserVideo
                if (playBrowserVideo != null) playBrowserVideo(url, media.mediaMime, media.mediaTitle)
                else latest.onPlayDirectMedia(url)
            }
        }) { Text("Play") } },
        dismissButton = { TextButton(onClick = { confirmDetectedMedia = false }) { Text("Stay in browser") } },
    )
    if (showSettings) GeckoAppearanceDialog(
        services.settings,
        canReloadCurrentPage = coordinator.activeSnapshot.url.isNotBlank(),
        onReloadCurrentPage = { coordinator.reload() },
    ) { showSettings = false }
    if (showAccount) GoogleAccountDialog(
        onDismiss = { showAccount = false },
        onPlayDriveMedia = { video -> showAccount = false; latest.onPlayAccountMedia(video) },
        model = accountModel,
    )
    if (voiceUnavailable) AlertDialog(onDismissRequest = { voiceUnavailable = false }, title = { Text("Voice search unavailable") },
        text = { Text("Type your search in the address bar instead.") }, confirmButton = { TextButton(onClick = { voiceUnavailable = false }) { Text("OK") } })
}

@Composable
private fun BrowserUnavailable(onUseWebView: () -> Unit, onBack: () -> Unit) {
    AlertDialog(onDismissRequest = onBack, title = { Text("Gecko browser could not start") },
        text = { Text("Your bookmarks and regular tabs are unchanged. You can use the original browser instead.") },
        confirmButton = { TextButton(onClick = onUseWebView) { Text("Use original browser") } },
        dismissButton = { TextButton(onClick = onBack) { Text("Back") } })
}
