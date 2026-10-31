package com.example.ui.screens

import com.example.ui.theme.hiContainerBorder

import com.example.downloads.BrowserDownload
import com.example.downloads.BrowserDownloadRequest
import com.example.downloads.DownloadFileResolver
import com.example.downloads.BrowserDownloadStatus
import com.example.downloads.BrowserDownloads
import androidx.compose.foundation.layout.FlowRow

import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.SwitchDefaults
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Checkbox
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.DesktopWindows
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewGroup
import com.example.auth.SecureWebSessions
import com.example.auth.SecureWebsitePolicy
import androidx.compose.ui.graphics.toArgb
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.webkit.ScriptHandler
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import org.json.JSONArray
import org.json.JSONTokener
import com.example.auth.GoogleAccountViewModel
import com.example.ui.components.GoogleAccountCard
import com.example.ui.components.GoogleAccountDialog
import com.example.ui.components.GoogleAccountStatusRow
import com.example.ui.components.BrowserPageSkeleton
import com.example.data.AppThemeMode
import com.example.ui.theme.HiPlayerTheme
import com.example.ui.theme.getPaletteForTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.example.ui.theme.LocalHiPalette
import com.example.model.StreamUrlClassifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.WeakHashMap
import android.app.Activity
import android.content.ActivityNotFoundException
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Slider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.Locale
import kotlin.math.roundToInt
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.ContextThemeWrapper
import android.view.View
import android.webkit.ValueCallback
import android.widget.FrameLayout
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.player.RemoteStreamHeadersRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.material.icons.filled.WifiOff
import java.util.concurrent.CopyOnWriteArrayList

private data class OrbitBrowserTab(
    val id: Int,
    val privateTab: Boolean,
    val webView: WebView? = null,
    val url: String = "",
    val title: String = "New tab",
    val desktop: Boolean = false,
    val openerTabId: Int? = null,
    val historyState: android.os.Bundle? = null,
)

private enum class OrbitEntryList { BOOKMARKS, HISTORY }

/**
 * Browser state that outlives the browser screen. Leaving browser mode (for the
 * player, library or settings) and coming back keeps every tab, and the active
 * tab's page stays loaded instead of reloading from scratch.
 */
private object HiBrowserSession {
    var tabs: List<OrbitBrowserTab>? = null
    var activeTabId: Int = 1
    var nextTabId: Int = 2
    var recentTabIds: List<Int> = listOf(1)
    val mediaSniffer = HiBrowserMediaSniffer()
    private var owner: java.lang.ref.WeakReference<Activity>? = null

    /** Set while the browser is on screen; called from WebView's JavaBridge thread. */
    @Volatile var autoPlayHandler: ((tabId: Int, directUrl: String?) -> Boolean)? = null

    /** WebViews made for an older (recreated) activity cannot be reattached; keep their tabs only. */
    fun adopt(activity: Activity?) {
        val previous = owner?.get()
        if (previous != null && previous !== activity) releaseWebViews()
        owner = activity?.let { java.lang.ref.WeakReference(it) }
    }

    fun releaseWebViews() {
        tabs = tabs?.map { tab ->
            val view = tab.webView ?: return@map tab
            val history = if (tab.privateTab) null else captureOrbitHistory(view)
            disposeOrbitWebView(view)
            tab.copy(webView = null, historyState = history ?: tab.historyState)
        }
    }
}

private class HiBrowserInitialState(
    val tabs: List<OrbitBrowserTab>,
    val activeTabId: Int,
    val nextTabId: Int,
    val recentTabIds: List<Int>,
)

/** Live tabs from this app session, else the tabs saved last time, else one new tab. */
private fun hiBrowserInitialState(store: OrbitBrowserStore): HiBrowserInitialState {
    HiBrowserSession.tabs?.takeIf { it.isNotEmpty() }?.map { tab ->
        if (tab.webView != null && !isOrbitWebViewUsable(tab.webView)) tab.copy(webView = null) else tab
    }?.let { live ->
        val ids = live.map { it.id }
        val active = HiBrowserSession.activeTabId.takeIf { it in ids } ?: live.last().id
        val next = maxOf(HiBrowserSession.nextTabId, (ids.maxOrNull() ?: 0) + 1)
        val recent = HiBrowserSession.recentTabIds.filter { it in ids }.ifEmpty { listOf(active) }
        return HiBrowserInitialState(live, active, next, recent)
    }
    val saved = store.savedTabs()
    if (saved.isNotEmpty()) {
        val ids = saved.map { it.id }.toSet()
        val tabs = saved.map { tab ->
            OrbitBrowserTab(
                id = tab.id,
                privateTab = false,
                url = tab.url,
                title = tab.title,
                desktop = tab.desktop,
                openerTabId = tab.openerTabId?.takeIf { it in ids },
            )
        }
        val active = store.savedActiveTabId.takeIf { it in ids } ?: tabs.last().id
        return HiBrowserInitialState(tabs, active, (ids.maxOrNull() ?: 0) + 1, listOf(active))
    }
    return HiBrowserInitialState(listOf(OrbitBrowserTab(id = 1, privateTab = false)), 1, 2, listOf(1))
}

/** A page that could not load because of the network (shown instead of WebView's error page). */
private data class HiBrowserPageError(val tabId: Int, val url: String, val offline: Boolean)

private data class HiBrowserSecureRequest(val url: String, val fromPrivateTab: Boolean, val forSignIn: Boolean, val tabId: Int)

/** History writes parse and rewrite a JSON list, so they run off the main thread, in order. */
private val hiBrowserHistoryExecutor = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, "hi-browser-history").apply {
        isDaemon = true
        priority = Thread.MIN_PRIORITY
    }
}

private object HiBrowserDocumentScripts {
    private val handlers = WeakHashMap<WebView, MutableList<ScriptHandler>>()

    fun isSupported(): Boolean = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)

    fun apply(webView: WebView, context: Context, store: OrbitBrowserStore, desktop: Boolean) {
        remove(webView)
        if (!isSupported()) return
        val registered = mutableListOf<ScriptHandler>()
        if (desktop) readAsset(context, "orbit_desktop_viewport.js")?.let {
            registered += WebViewCompat.addDocumentStartJavaScript(webView, it, setOf("*"))
        }
        if (store.adBlock) {
            readAsset(context, "orbit_adblock.js")?.let {
                registered += WebViewCompat.addDocumentStartJavaScript(webView, it, setOf("*"))
            }
        }
        if (store.pageSkipper) {
            readAsset(context, "orbit_page_skipper.js")?.let {
                registered += WebViewCompat.addDocumentStartJavaScript(webView, it, setOf("*"))
            }
        }
        if (store.youtubeAdSkip) {
            readAsset(context, "orbit_youtube.js")?.let {
                registered += WebViewCompat.addDocumentStartJavaScript(
                    webView, it, setOf(
                        "https://*.youtube.com", "https://youtube.com",
                        "https://*.youtube-nocookie.com", "https://youtube-nocookie.com",
                    ),
                )
            }
        }
        registered += WebViewCompat.addDocumentStartJavaScript(
            webView,
            HI_BROWSER_VIDEO_HOOK_SCRIPT,
            setOf("*"),
        )
        if (registered.isNotEmpty()) handlers[webView] = registered
    }

    fun remove(webView: WebView) {
        handlers.remove(webView)?.forEach { handler -> runCatching { handler.remove() } }
    }

    private fun readAsset(context: Context, name: String): String? =
        runCatching { context.assets.open(name).bufferedReader().use { it.readText().take(100_000) } }.getOrNull()
}

private fun captureOrbitHistory(view: WebView): android.os.Bundle? = runCatching {
    val state = android.os.Bundle()
    view.saveState(state)
    val parcel = android.os.Parcel.obtain()
    try { parcel.writeBundle(state); state.takeIf { parcel.dataSize() <= 48_000 } } finally { parcel.recycle() }
}.getOrNull()

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun HiBrowserScreen(
    onBack: () -> Unit,
    appNavigation: @Composable () -> Unit = {},
    onPlayDirectMedia: (String) -> Unit,
    modifier: Modifier = Modifier,
    appTextSizeSp: Int = 14,
    onAppTextSizeChange: (Int) -> Unit = {},
    onPlayAccountMedia: (com.example.model.VideoItem) -> Unit = {},
    isInPictureInPicture: Boolean = false,
    onEnterPictureInPicture: (() -> Boolean)? = null,
    externalUrl: String? = null,
    openDownloads: Boolean = false,
    onDownloadsConsumed: () -> Unit = {},
    onExternalUrlConsumed: () -> Unit = {},
    /** Plays a video found on a web page (direct file, HLS or DASH) in Hi Player. */
    onPlayBrowserVideo: ((url: String, mimeType: String?, title: String?) -> Unit)? = null,
) {
    val context = LocalContext.current
    val store = remember(context) { OrbitBrowserStore(context) }
    val downloads by BrowserDownloads.downloads.collectAsStateWithLifecycle()
    var pendingDownload by remember { mutableStateOf<BrowserDownloadRequest?>(null) }
    var darkModeEnabled by remember { mutableStateOf(store.darkMode) }
    val browserTheme = if (darkModeEnabled) AppThemeMode.CYAN_NEON_DARK else AppThemeMode.WARM_SUNSET_LIGHT
    val palette = getPaletteForTheme(browserTheme)
    val accountModel: GoogleAccountViewModel = viewModel()
    val accountProfile by accountModel.profile.collectAsStateWithLifecycle()
    var showAccount by remember { mutableStateOf(false) }
    var adBlockEnabled by remember { mutableStateOf(store.adBlock) }
    var pageSkipperEnabled by remember { mutableStateOf(store.pageSkipper) }
    var youtubeAdSkipEnabled by remember { mutableStateOf(store.youtubeAdSkip) }
    var pendingSecureSite by remember { mutableStateOf<HiBrowserSecureRequest?>(null) }
    var secureRoutingVersion by remember { mutableIntStateOf(0) }
    // Private-site choices live only for this browser composition; never write them to disk.
    var privateSecureOrigins by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingVideoNavigation by remember { mutableStateOf<String?>(null) }
    var videoHandoffEnabled by remember { mutableStateOf(store.videoHandoff) }
    // Bumped to rebuild every tab's WebView (pages pick light/dark at creation).
    var webViewGeneration by remember { mutableIntStateOf(0) }
    // Before main-frame content paints, a neutral skeleton covers the WebView
    // rather than flashing a blank page. It never claims to be website content.
    var pendingFirstPaint by remember { mutableStateOf(false) }
    // Reserve room for the floating address bar only on pages that cannot scroll.
    var reserveBarSpace by remember { mutableStateOf(false) }
    var barHeightPx by remember { mutableIntStateOf(0) }
    val mediaSniffer = HiBrowserSession.mediaSniffer
    val hostActivity = remember(context) { context.findHiActivity() }
    remember(hostActivity) {
        HiBrowserSession.adopt(hostActivity)
        true
    }
    val initialState = remember { hiBrowserInitialState(store) }
    val adBlockFlag = remember { HiBrowserFlag(store.adBlock) }
    val fullscreen = remember { HiBrowserFullscreen() }
    val latestOnPlayBrowserVideo by rememberUpdatedState(onPlayBrowserVideo)
    var tabs by remember { mutableStateOf(initialState.tabs) }
    val latestTabs by rememberUpdatedState(tabs)
    val latestOnPlayDirectMedia by rememberUpdatedState(onPlayDirectMedia)
    var activeTabId by remember { mutableIntStateOf(initialState.activeTabId) }
    var nextTabId by remember { mutableIntStateOf(initialState.nextTabId) }
    var recentTabIds by remember { mutableStateOf(initialState.recentTabIds) }
    val activeTab = tabs.firstOrNull { it.id == activeTabId }
    val webView = activeTab?.webView
    androidx.compose.runtime.SideEffect {
        HiBrowserSession.tabs = tabs
        HiBrowserSession.activeTabId = activeTabId
        HiBrowserSession.nextTabId = nextTabId
        HiBrowserSession.recentTabIds = recentTabIds
    }
    // Regular tabs are saved (debounced) so they come back after the app is closed.
    val persistedTabs = tabs.filterNot { it.privateTab }.map { OrbitSavedTab(it.id, it.url, it.title, it.desktop, it.openerTabId) }
    LaunchedEffect(persistedTabs, activeTabId) {
        delay(700L)
        val ids = persistedTabs.map { it.id }.toSet()
        val cleaned = persistedTabs.map { tab -> if (tab.openerTabId?.let { it in ids } == true) tab else tab.copy(openerTabId = null) }
        withContext(Dispatchers.IO) { runCatching { store.saveTabs(cleaned, activeTabId) } }
    }
    var pageError by remember { mutableStateOf<HiBrowserPageError?>(null) }
    var slowLoad by remember { mutableStateOf(false) }
    var networkOnline by remember { mutableStateOf(true) }
    var autoPlayEnabled by remember { mutableStateOf(store.autoPlayInPlayer) }
    val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    var address by remember { mutableStateOf("") }
    var currentUrl by remember { mutableStateOf("") }
    var showStartPage by remember { mutableStateOf(true) }
    var searchProvider by rememberSaveable { mutableStateOf(BrowserSearchProvider.entries.firstOrNull { it.name == store.searchProviderName } ?: BrowserSearchProvider.GOOGLE) }
    var pageTitle by remember { mutableStateOf("Hi Browser") }
    var loadingProgress by remember { mutableIntStateOf(0) }
    var rendererGone by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showBrowserSettings by remember { mutableStateOf(false) }
    var showTabsDialog by remember { mutableStateOf(false) }
    var showDownloadsDialog by remember { mutableStateOf(false) }
    var entryList by remember { mutableStateOf<OrbitEntryList?>(null) }
    var entriesVersion by remember { mutableIntStateOf(0) }
    var addressFocused by remember { mutableStateOf(false) }
    val browserFocusManager = androidx.compose.ui.platform.LocalFocusManager.current
    // History suggestions are only needed while the address bar is focused.
    val recentEntries = remember(entriesVersion, addressFocused) { if (addressFocused) store.history() else emptyList() }
    val redirectSources = remember { mutableMapOf<Int, String>() }
    var textZoomPercent by remember { mutableIntStateOf(store.textZoom) }
    var popupBlockEnabled by remember { mutableStateOf(store.popupBlock) }
    var findBarVisible by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var findMatchLabel by remember { mutableStateOf("") }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var librarySection by remember { mutableStateOf<HiLibrarySection?>(null) }
    LaunchedEffect(openDownloads) {
        if (openDownloads) {
            librarySection = HiLibrarySection.DOWNLOADS
            onDownloadsConsumed()
        }
    }
    val thumbnails = remember { mutableStateMapOf<Int, ImageBitmap>() }
    var shortcutsVersion by remember { mutableIntStateOf(0) }
    val shortcuts = remember(shortcutsVersion) { store.shortcuts().map { it.title to it.url } }
    var defaultBrowserCheck by remember { mutableIntStateOf(0) }
    val isDefaultBrowser = remember(defaultBrowserCheck) {
        runCatching {
            context.packageManager.resolveActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")),
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY,
            )?.activityInfo?.packageName == context.packageName
        }.getOrDefault(false)
    }
    // pipSession: a browser picture-in-picture window is (or was just) open.
    // pipPinned: the page's video is pinned to fill the page for that window.
    val pipSession = remember { mutableStateOf(false) }
    val pipPinned = remember { mutableStateOf(false) }
    val pausedForPip = remember { mutableStateOf(false) }
    // The bottom address bar slides away while the page scrolls down and returns on scroll up.
    var browserBarHidden by remember { mutableStateOf(false) }

    androidx.compose.runtime.SideEffect { mediaSniffer.activeTabId = activeTabId }

    LaunchedEffect(activeTabId) {
        val restoredTab = tabs.firstOrNull { it.id == activeTabId }
        address = restoredTab?.url.orEmpty()
        currentUrl = restoredTab?.url.orEmpty()
        pageTitle = restoredTab?.title ?: "Hi Browser"
        showStartPage = restoredTab?.url.isNullOrBlank()
        findBarVisible = false
        findQuery = ""
        findMatchLabel = ""
        browserBarHidden = false
        reserveBarSpace = false
        if (pageError?.tabId != activeTabId) pageError = null
        // A restored tab (app reopened, or its page was released) shows the loader until it paints.
        if (restoredTab != null && restoredTab.url.isNotBlank() && !isOrbitWebViewUsable(restoredTab.webView)) pendingFirstPaint = true
        // Only the visible tab keeps playing media and running animations.
        tabs.forEach { tab ->
            val view = tab.webView ?: return@forEach
            if (tab.id == activeTabId) {
                view.onResume()
            } else {
                view.evaluateJavascript(HI_BROWSER_PAUSE_MEDIA_SCRIPT, null)
                view.onPause()
            }
        }
    }

    fun updateTab(id: Int, transform: (OrbitBrowserTab) -> OrbitBrowserTab) {
        tabs = tabs.map { if (it.id == id) transform(it) else it }
    }

    fun prefersSecureWebsite(url: String, privateTab: Boolean): Boolean =
        if (privateTab) SecureWebsitePolicy.originKey(url) in privateSecureOrigins
        else SecureWebSessions.prefersBrowser(context, url)

    fun openSecureWebsite(url: String, privateTab: Boolean, tabId: Int = activeTabId): Boolean {
        browserFocusManager.clearFocus()
        addressFocused = false
        findBarVisible = false
        val result = SecureWebSessions.open(context, url, palette.surface.toArgb())
        if (result.isFailure) {
            Toast.makeText(context, "Couldn't open the website browser. Install or enable a browser with Custom Tabs, then retry.", Toast.LENGTH_LONG).show()
            return false
        }
        // Successful launch is NOT proof of website authentication. Keep that
        // session in its owning browser and remember only this origin's route.
        if (privateTab) SecureWebsitePolicy.originKey(url)?.let { privateSecureOrigins = privateSecureOrigins + it }
        else SecureWebSessions.rememberBrowser(context, url)
        secureRoutingVersion++
        val tab = tabs.firstOrNull { it.id == tabId }
        val history = tab?.webView?.let(::captureOrbitHistory) ?: tab?.historyState
        tab?.webView?.let(::disposeOrbitWebView)
        updateTab(tabId) { it.copy(url = url, title = Uri.parse(url).host.orEmpty(), webView = null, historyState = history) }
        if (activeTabId == tabId) {
            currentUrl = url
            address = url
            pageTitle = Uri.parse(url).host.orEmpty()
            showStartPage = false
            pendingFirstPaint = false
            loadingProgress = 100
            pageError = null
            rendererGone = false
        }
        return true
    }

    fun openNewTab(privateTab: Boolean, initialUrl: String = "", openerId: Int? = activeTabId) {
        if (tabs.size >= 12) {
            Toast.makeText(context, "Close a tab before opening another (12-tab limit)", Toast.LENGTH_SHORT).show()
            return
        }
        val id = nextTabId++
        tabs = tabs + OrbitBrowserTab(id = id, privateTab = privateTab, url = initialUrl, openerTabId = openerId)
        recentTabIds = (recentTabIds - activeTabId - id) + activeTabId + id
        activeTabId = id
        address = initialUrl
        currentUrl = initialUrl
        pageTitle = if (privateTab) "Private tab" else "New tab"
        loadingProgress = 0
        showStartPage = initialUrl.isBlank()
        pendingFirstPaint = initialUrl.isNotBlank()
        rendererGone = false
    }

    fun activateTab(tab: OrbitBrowserTab) {
        recentTabIds = (recentTabIds - activeTabId - tab.id) + activeTabId + tab.id
        activeTabId = tab.id
        address = tab.url
        currentUrl = tab.url
        pageTitle = tab.title
        showStartPage = address.isBlank()
        pendingFirstPaint = tab.webView == null && tab.url.isNotBlank() && !prefersSecureWebsite(tab.url, tab.privateTab)
        loadingProgress = 0
        rendererGone = false
    }

    fun closeTab(tabId: Int) {
        val closing = tabs.firstOrNull { it.id == tabId } ?: return
        val returnId = BrowserTabBackPolicy.target(tabId, closing.openerTabId, tabs.map { it.id }, recentTabIds)
        val remaining = tabs.filterNot { it.id == tabId }.map { if (it.openerTabId == tabId) it.copy(openerTabId = closing.openerTabId) else it }
        recentTabIds = recentTabIds - tabId
        closing.webView?.let(::disposeOrbitWebView)
        tabs = remaining
        if (remaining.isEmpty()) {
            openNewTab(privateTab = false)
        } else if (activeTabId == tabId) {
            activateTab(remaining.firstOrNull { it.id == returnId } ?: remaining.last())
        }
    }

    fun requestSecureSite(url: String, fromPrivateTab: Boolean = activeTab?.privateTab == true, forSignIn: Boolean = false, tabId: Int = activeTabId) {
        if (!SecureWebSessions.validPage(url)) {
            Toast.makeText(context, "Secure browser sign-in needs an HTTPS page", Toast.LENGTH_SHORT).show()
            return
        }
        pendingSecureSite = HiBrowserSecureRequest(url, fromPrivateTab, forSignIn, tabId)
    }

    fun loadAddress(raw: String) {
        val destination = normalizeBrowserInput(raw, searchProvider) ?: return
        if (prefersSecureWebsite(destination, activeTab?.privateTab == true)) {
            openSecureWebsite(destination, activeTab?.privateTab == true)
            return
        }
        if (SecureWebSessions.isGoogleAuthentication(destination)) {
            requestSecureSite(SecureWebSessions.GOOGLE_ACCOUNT, forSignIn = true)
            return
        }
        val stream = StreamUrlClassifier.classify(destination)
        if (!isOrbitYoutube(destination) && stream.isPlayableInHiPlayer && !stream.requiresWebResolution) {
            onPlayDirectMedia(destination)
            return
        }
        address = destination
        currentUrl = destination
        pageTitle = destination
        // Show the loading page until the new page paints (also when searching from a page).
        pendingFirstPaint = true
        pageError = null
        showStartPage = false
        rendererGone = false
        val tab = activeTab ?: return
        redirectSources.remove(tab.id)
        updateTab(tab.id) { it.copy(url = destination, title = destination) }
        val existing = tab.webView
        if (existing != null) existing.loadUrl(destination)
    }

    fun showBrowserHome() {
        webView?.stopLoading()
        address = ""
        currentUrl = ""
        pageTitle = if (activeTab?.privateTab == true) "Private tab" else "Hi Browser"
        loadingProgress = 0
        rendererGone = false
        activeTab?.let { updateTab(it.id) { tab -> tab.copy(url = "", title = "New tab") } }
        showStartPage = true
        pendingFirstPaint = false
        pageError = null
    }

    fun navigateBrowserBack() {
        if (!showStartPage && webView?.canGoBack() == true) {
            webView?.goBack()
            return
        }
        val target = BrowserTabBackPolicy.target(activeTabId, activeTab?.openerTabId, tabs.map { it.id }, recentTabIds)
        if (target != null) {
            // Unwind this tab instead of cycling endlessly between two root tabs.
            closeTab(activeTabId)
        } else if (!showStartPage) showBrowserHome() else onBack()
    }

    fun toggleDesktopSite() {
        val tab = activeTab ?: return
        val desktop = !tab.desktop
        updateTab(tab.id) { it.copy(desktop = desktop) }
        tab.webView?.let { view ->
            applyOrbitDesktopMode(view, desktop)
            HiBrowserDocumentScripts.apply(view, context, store, desktop)
            view.reload()
            Toast.makeText(context, if (desktop) "Requesting desktop site…" else "Requesting mobile site…", Toast.LENGTH_SHORT).show()
        }
    }

    fun setDarkMode(enabled: Boolean) {
        store.darkMode = enabled
        darkModeEnabled = enabled
        // Both uiMode and isLightTheme are fixed at WebView creation. Save each
        // tab's latest Back/Forward list BEFORE replacing its renderer.
        tabs = tabs.map { tab ->
            val view = tab.webView
            val history = view?.let(::captureOrbitHistory) ?: tab.historyState
            view?.let(::disposeOrbitWebView)
            tab.copy(webView = null, historyState = history)
        }
        if (!showStartPage) pendingFirstPaint = true
        webViewGeneration++
    }

    fun recordHistory(title: String, url: String, redirectedFrom: String? = null) {
        hiBrowserHistoryExecutor.execute {
            runCatching {
                redirectedFrom?.let { old -> store.replaceRedirectedHistory(old, url) }
                store.addHistory(OrbitBrowserEntry(title, url))
            }
            mainHandler.post { entriesVersion++ }
        }
    }

    fun retryPage() {
        val error = pageError ?: return
        val view = tabs.firstOrNull { it.id == error.tabId }?.webView
        pageError = null
        if (error.tabId == activeTabId) {
            pendingFirstPaint = true
            loadingProgress = 1
        }
        if (view != null) view.loadUrl(error.url) else if (error.tabId == activeTabId) loadAddress(error.url)
    }

    fun applyNetworkCacheMode(online: Boolean) {
        // Offline, pages already in the cache still open; private tabs never use the cache.
        tabs.forEach { tab ->
            val view = tab.webView ?: return@forEach
            if (!tab.privateTab) view.settings.cacheMode = if (online) WebSettings.LOAD_DEFAULT else WebSettings.LOAD_CACHE_ELSE_NETWORK
        }
    }

    fun setAutoPlayEnabled(enabled: Boolean) {
        store.autoPlayInPlayer = enabled
        autoPlayEnabled = enabled
    }

    fun setVideoHandoffEnabled(enabled: Boolean) {
        store.videoHandoff = enabled
        videoHandoffEnabled = enabled
    }

    /** Opens a web video in Hi Player with the page's referrer, user agent and cookies. */
    fun playBrowserVideo(tabId: Int, url: String) {
        if (BrowserVideoPolicy.isAdUrl(url) || OrbitBrowserFilterEngine.shouldBlock(Uri.parse(url), true, mediaSniffer.pageHost(tabId))) {
            Toast.makeText(context, "Ad video blocked", Toast.LENGTH_SHORT).show()
            return
        }
        val tab = tabs.firstOrNull { it.id == tabId }
        val pageUrl = mediaSniffer.pageUrl(tabId).ifBlank { tab?.url.orEmpty() }
        val userAgent = tab?.webView?.settings?.userAgentString ?: runCatching {
            val mobile = WebSettings.getDefaultUserAgent(context)
            if (tab?.desktop == true) BrowserAppearancePolicy.desktopUserAgent(mobile) else mobile
        }.getOrNull()
        registerBrowserStreamHeaders(url, pageUrl, userAgent, mediaSniffer.mediaHosts(tabId))
        val title = mediaSniffer.title(tabId).ifBlank { tab?.title.orEmpty() }.takeIf { it.isNotBlank() && !it.startsWith("http") }
        tab?.webView?.evaluateJavascript(HI_BROWSER_PAUSE_MEDIA_SCRIPT, null)
        val player = latestOnPlayBrowserVideo
        if (player != null) player(url, hiBrowserStreamMimeType(url), title) else latestOnPlayDirectMedia(url)
    }

    fun refreshDocumentScripts() {
        tabs.forEach { tab -> tab.webView?.let { HiBrowserDocumentScripts.apply(it, context, store, tab.desktop) } }
        webView?.reload()
    }

    fun setAdBlockEnabled(enabled: Boolean) {
        store.adBlock = enabled
        adBlockEnabled = enabled
        adBlockFlag.enabled = enabled
        // Re-registers the page ad shield and reloads the page.
        refreshDocumentScripts()
    }

    fun injectCosmeticCss(view: WebView, url: String) {
        val css = OrbitBrowserFilterEngine.cosmeticCss(runCatching { Uri.parse(url).host }.getOrNull(), adBlockFlag.enabled)
        if (css.isNotBlank()) view.evaluateJavascript(
            "(function(){var s=document.getElementById('__orbit_filter_css');if(!s){s=document.createElement('style');s.id='__orbit_filter_css';(document.head||document.documentElement).appendChild(s);}s.textContent=" + JSONObject.quote(css) + ";})();",
            null,
        )
    }

    fun updateFilterLists() {
        Toast.makeText(context, "Updating filter lists…", Toast.LENGTH_SHORT).show()
        Thread {
            val ok = OrbitBrowserFilterEngine.update(context)
            android.os.Handler(context.mainLooper).post {
                Toast.makeText(context, if (ok) "Updated ${OrbitBrowserFilterEngine.ruleCount()} rules" else "Filter update failed", Toast.LENGTH_SHORT).show()
                webView?.reload()
            }
        }.start()
    }

    fun addBookmark() {
        val url = currentUrl
        if (!url.startsWith("http://") && !url.startsWith("https://")) return
        if (store.isBookmarked(url)) store.removeBookmark(url)
        else store.addBookmark(OrbitBrowserEntry(pageTitle.ifBlank { url }, url))
        entriesVersion++
    }

    fun readCurrentPage() {
        val view = webView ?: return
        view.evaluateJavascript(OrbitReaderMode.extractionScript) { raw ->
            val json = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull()
            val payload = runCatching { json?.let { JSONObject(it) } }.getOrNull()
            val items = payload?.optJSONArray("items")
            if (items == null || items.length() == 0) {
                Toast.makeText(context, "No readable article found", Toast.LENGTH_SHORT).show()
            } else {
                val baseUrl = view.url ?: currentUrl
                view.loadDataWithBaseURL(baseUrl, OrbitReaderMode.html(payload.optString("title", pageTitle), items), "text/html", "utf-8", null)
            }
        }
    }

    fun showBrowserToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun isWebPage(url: String): Boolean = url.startsWith("http://") || url.startsWith("https://")

    /** Opens a page-requested window (target=_blank link or window.open) as a new tab. */
    fun openUrlFromPage(url: String, source: WebView, privateTab: Boolean, userGesture: Boolean) {
        val sourceId = tabs.firstOrNull { it.webView === source }?.id ?: return
        if (prefersSecureWebsite(url, privateTab)) {
            if (userGesture) openSecureWebsite(url, privateTab, sourceId)
            else openNewTab(privateTab, url, openerId = sourceId)
            return
        }
        if (SecureWebSessions.isGoogleAuthentication(url)) {
            requestSecureSite(SecureWebSessions.entryForGoogleAuth(source.url.orEmpty()), privateTab, forSignIn = true, tabId = sourceId)
            return
        }
        if (OrbitBrowserFilterEngine.shouldBlock(Uri.parse(url), store.adBlock) || (store.adBlock && BrowserVideoPolicy.isAdUrl(url))) {
            showBrowserToast("Pop-up blocked")
            return
        }
        openNewTab(privateTab, url, openerId = sourceId)
    }

    fun setTextZoom(percent: Int) {
        val value = BrowserAppearancePolicy.textZoom(percent)
        store.textZoom = value
        textZoomPercent = value
        tabs.forEach { tab -> tab.webView?.let { applyOrbitTextZoom(it, value) } }
    }

    fun setPopupBlockEnabled(enabled: Boolean) {
        store.popupBlock = enabled
        popupBlockEnabled = enabled
        tabs.forEach { tab -> tab.webView?.settings?.javaScriptCanOpenWindowsAutomatically = !enabled }
    }

    fun clearBrowsingData(history: Boolean, cookies: Boolean, cache: Boolean) {
        HiBrowserTools.clearBrowsingData(context, tabs.mapNotNull { it.webView }, history, cookies, cache)
        if (cookies) {
            SecureWebSessions.clearRouting(context)
            privateSecureOrigins = emptySet()
            secureRoutingVersion++
        }
        if (history) {
            store.clearHistory()
            HiSiteIcons.clearSaved(context)
            entriesVersion++
        }
        showBrowserToast("Hi Browser data cleared. Device-browser sign-ins are managed in that browser.")
    }

    fun findInPage(query: String) {
        findQuery = query
        val view = webView ?: return
        if (query.isBlank()) {
            view.clearMatches()
            findMatchLabel = ""
        } else {
            view.findAllAsync(query)
        }
    }

    fun translateCurrentPage() {
        val url = currentUrl
        if (!isWebPage(url)) {
            showBrowserToast("Open a web page to translate it")
            return
        }
        if (HiBrowserTools.isTranslatedPage(url)) {
            showBrowserToast("This page is already translated")
            return
        }
        loadAddress(HiBrowserTools.translateUrl(url, HiBrowserTools.translateTargetLanguage(Locale.getDefault())))
    }

    fun addCurrentPageToHomeScreen() {
        val url = currentUrl
        if (!isWebPage(url)) {
            showBrowserToast("Open a web page to add it to your home screen")
            return
        }
        val requested = HiBrowserTools.pinPageShortcut(context, url, pageTitle, webView?.favicon)
        showBrowserToast(if (requested) "Adding to home screen…" else "Your launcher doesn't support adding shortcuts")
    }

    fun startPictureInPicture() {
        val view = webView
        val enter = onEnterPictureInPicture
        if (view == null || enter == null || showStartPage) {
            showBrowserToast("Open a page with a video first")
            return
        }
        view.evaluateJavascript(HI_BROWSER_PIP_PIN_SCRIPT) { result ->
            if (result?.contains("ok") == true) {
                pipPinned.value = true
                pipSession.value = true
                if (!enter()) {
                    pipSession.value = false
                    pipPinned.value = false
                    view.evaluateJavascript(HI_BROWSER_PIP_UNPIN_SCRIPT, null)
                    showBrowserToast("Picture-in-picture isn't available on this device")
                }
            } else {
                showBrowserToast("No video found on this page")
            }
        }
    }

    val voiceSearchLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()
        if (result.resultCode == Activity.RESULT_OK && !spoken.isNullOrBlank()) {
            address = spoken
            loadAddress(spoken)
        }
    }

    fun startVoiceSearch() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a search or website")
        try {
            voiceSearchLauncher.launch(intent)
        } catch (error: ActivityNotFoundException) {
            showBrowserToast("Voice search isn't available on this device")
        }
    }

    fun startQrScan() {
        HiBrowserTools.scanQrCode(
            context = context,
            onResult = { value ->
                address = value
                loadAddress(value)
            },
            onError = { message -> showBrowserToast(message) },
        )
    }

    /** Saves a small preview of the current page for the tab switcher. */
    fun captureThumbnail() {
        val view = webView ?: return
        val tabId = activeTabId
        if (showStartPage || view.width <= 0 || view.height <= 0) {
            thumbnails.remove(tabId)
            return
        }
        runCatching {
            val scale = 0.35f
            val bitmap = android.graphics.Bitmap.createBitmap(
                (view.width * scale).toInt().coerceAtLeast(1),
                (view.height * scale).toInt().coerceAtLeast(1),
                android.graphics.Bitmap.Config.RGB_565,
            )
            val canvas = android.graphics.Canvas(bitmap)
            canvas.scale(scale, scale)
            canvas.translate(-view.scrollX.toFloat(), -view.scrollY.toFloat())
            view.draw(canvas)
            thumbnails[tabId] = bitmap.asImageBitmap()
        }
    }

    fun closeAllTabs(privateTabs: Boolean) {
        val closing = tabs.filter { it.privateTab == privateTabs }
        closing.forEach { tab ->
            tab.webView?.let(::disposeOrbitWebView)
            thumbnails.remove(tab.id)
        }
        val remaining = tabs.filterNot { it.privateTab == privateTabs }
        tabs = remaining
        if (remaining.isEmpty()) {
            openNewTab(privateTab = false)
        } else if (remaining.none { it.id == activeTabId }) {
            activateTab(remaining.first())
        }
    }

    val defaultBrowserLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        defaultBrowserCheck++
    }

    fun requestDefaultBrowser() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roles = context.getSystemService(android.app.role.RoleManager::class.java)
                if (roles != null && roles.isRoleAvailable(android.app.role.RoleManager.ROLE_BROWSER)) {
                    if (roles.isRoleHeld(android.app.role.RoleManager.ROLE_BROWSER)) {
                        showBrowserToast("Hi Player is already your default browser")
                        defaultBrowserCheck++
                        return
                    }
                    defaultBrowserLauncher.launch(roles.createRequestRoleIntent(android.app.role.RoleManager.ROLE_BROWSER))
                    return
                }
            }
            defaultBrowserLauncher.launch(Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        } catch (error: Exception) {
            showBrowserToast("Open Settings > Apps > Default apps > Browser app")
        }
    }

    DisposableEffect(findBarVisible, webView) {
        val view = webView
        if (findBarVisible && view != null) {
            view.setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
                findMatchLabel = when {
                    numberOfMatches > 0 -> "${activeMatchOrdinal + 1}/$numberOfMatches"
                    isDoneCounting && findQuery.isNotBlank() -> "0/0"
                    else -> ""
                }
            }
            if (findQuery.isNotBlank()) view.findAllAsync(findQuery)
        }
        onDispose {
            runCatching {
                view?.clearMatches()
                view?.setFindListener(null)
            }
        }
    }

    // Closing the picture-in-picture window stops the activity without
    // resuming it; pause page media then so audio does not keep playing.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> if (pipSession.value) {
                    latestTabs.forEach { it.webView?.onPause() }
                    pausedForPip.value = true
                }
                Lifecycle.Event.ON_START -> if (pausedForPip.value) {
                    latestTabs.forEach { it.webView?.onResume() }
                    pausedForPip.value = false
                }
                Lifecycle.Event.ON_RESUME -> pipSession.value = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(isInPictureInPicture) {
        if (!isInPictureInPicture && pipPinned.value) {
            pipPinned.value = false
            webView?.evaluateJavascript(HI_BROWSER_PIP_UNPIN_SCRIPT, null)
        }
    }

    LaunchedEffect(tabs.any { it.privateTab }) {
        if (tabs.none { it.privateTab }) privateSecureOrigins = emptySet()
    }

    LaunchedEffect(externalUrl) {
        val url = externalUrl ?: return@LaunchedEffect
        onExternalUrlConsumed()
        if (tabs.size >= 12) loadAddress(url) else {
            openNewTab(privateTab = false, initialUrl = url)
            if (prefersSecureWebsite(url, false)) openSecureWebsite(url, false)
        }
    }

    BackHandler {
        when {
            fullscreen.active -> fullscreen.exit(context.findHiActivity())
            findBarVisible -> {
                findBarVisible = false
                findQuery = ""
                findMatchLabel = ""
            }
            else -> navigateBrowserBack()
        }
    }

    // Leaving browser mode keeps the tabs, and the active page stays loaded (paused)
    // so it is exactly where the user left it. Other tabs keep their history and
    // reload when opened. Everything is released when the activity finishes.
    DisposableEffect(Unit) {
        onDispose {
            val leavingApp = hostActivity?.let { it.isFinishing || it.isChangingConfigurations } ?: true
            val keepId = if (leavingApp) null else HiBrowserSession.activeTabId
            HiBrowserSession.tabs = latestTabs.map { tab ->
                val view = tab.webView ?: return@map tab
                if (tab.id == keepId) {
                    runCatching {
                        view.evaluateJavascript(HI_BROWSER_PAUSE_MEDIA_SCRIPT, null)
                        view.onPause()
                    }
                    tab
                } else {
                    val history = if (tab.privateTab) null else captureOrbitHistory(view)
                    disposeOrbitWebView(view)
                    tab.copy(webView = null, historyState = history ?: tab.historyState)
                }
            }
        }
    }

    // Opens a video in Hi Player as soon as the user starts it on the page.
    DisposableEffect(Unit) {
        val handler: (Int, String?) -> Boolean = handler@{ tabId, directUrl ->
            if (!store.autoPlayInPlayer || !store.videoHandoff) return@handler false
            if (tabId != mediaSniffer.activeTabId) return@handler false
            if (isOrbitYoutube(mediaSniffer.pageUrl(tabId))) return@handler false
            val candidate = directUrl?.takeUnless { isOrbitYoutube(it) } ?: mediaSniffer.bestManifest(tabId) ?: return@handler false
            if (BrowserVideoPolicy.isAdUrl(candidate)) return@handler false
            val parsed = runCatching { Uri.parse(candidate) }.getOrNull() ?: return@handler false
            if (OrbitBrowserFilterEngine.shouldBlock(parsed, adBlockFlag.enabled, mediaSniffer.pageHost(tabId), mainFrame = false)) return@handler false
            if (!mediaSniffer.claimHandoff(tabId)) return@handler false
            mainHandler.post { playBrowserVideo(tabId, candidate) }
            true
        }
        HiBrowserSession.autoPlayHandler = handler
        onDispose { if (HiBrowserSession.autoPlayHandler === handler) HiBrowserSession.autoPlayHandler = null }
    }

    // Retry a page that failed for lack of network as soon as the connection is back.
    DisposableEffect(context) {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        fun online(): Boolean {
            val manager = connectivity ?: return true
            return runCatching {
                manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            }.getOrDefault(true)
        }
        networkOnline = online()
        applyNetworkCacheMode(networkOnline)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                mainHandler.post {
                    if (!networkOnline) {
                        networkOnline = true
                        applyNetworkCacheMode(true)
                    }
                    if (pageError != null) retryPage()
                }
            }
            override fun onLost(network: Network) {
                mainHandler.postDelayed({
                    val now = online()
                    if (now != networkOnline) {
                        networkOnline = now
                        applyNetworkCacheMode(now)
                    }
                }, 400L)
            }
        }
        val registered = runCatching { connectivity?.registerDefaultNetworkCallback(callback) }.isSuccess && connectivity != null
        onDispose { if (registered) runCatching { connectivity?.unregisterNetworkCallback(callback) } }
    }

    // Start the WebView engine while the start page shows, so the first search opens faster.
    LaunchedEffect(Unit) {
        delay(300L)
        runCatching { WebSettings.getDefaultUserAgent(context) }
    }

    LaunchedEffect(context) {
        BrowserDownloads.restore(context)
        // Parse (and, when a few days old, refresh) the block lists off the main thread.
        withContext(Dispatchers.IO) {
            OrbitBrowserFilterEngine.load(context)
            OrbitBrowserFilterEngine.autoUpdateIfStale(context)
        }
    }
    // A page that cannot scroll (short page, or a dialog that locks scrolling)
    // would keep part of itself under the floating bar, so give it room.
    LaunchedEffect(activeTabId, showStartPage, webView, webViewGeneration) {
        reserveBarSpace = false
        if (showStartPage || webView == null) return@LaunchedEffect
        while (true) {
            delay(1_200L)
            if (!reserveBarSpace && webView.isAttachedToWindow && webView.height > 0 &&
                !webView.canScrollVertically(1) && !webView.canScrollVertically(-1)
            ) {
                reserveBarSpace = true
            }
        }
    }
    LaunchedEffect(pendingFirstPaint, activeTabId, currentUrl) {
        slowLoad = false
        if (pendingFirstPaint) {
            delay(6_000L)
            slowLoad = true
            // Time passing is not a successful paint. Keep the skeleton and
            // Stop/Retry available until actual content, error or cancellation.
        }
    }
    DisposableEffect(Unit) {
        onDispose { fullscreen.exit(context.findHiActivity()) }
    }

    val fileChooserCallback = remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val fileChooserLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val callback = fileChooserCallback.value
        fileChooserCallback.value = null
        callback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
    }

    val useSecureWebsite = remember(currentUrl, activeTab?.privateTab, showStartPage, secureRoutingVersion, privateSecureOrigins) {
        !showStartPage && prefersSecureWebsite(currentUrl, activeTab?.privateTab == true)
    }
    LaunchedEffect(useSecureWebsite) {
        if (useSecureWebsite) {
            pendingFirstPaint = false
            loadingProgress = 100
            pageError = null
        }
    }
    val density = LocalDensity.current
    val imeVisibleNow = hiImeVisible()
    val onPageNow = !showStartPage
    val showBrowserBar = !onPageNow || !browserBarHidden || findBarVisible || imeVisibleNow || pendingFirstPaint
    // The address bar floats over the page and slides away by moving its layer,
    // so the WebView is never resized while the page scrolls.
    val barHiddenFraction by animateFloatAsState(
        targetValue = if (showBrowserBar) 0f else 1f,
        animationSpec = tween(durationMillis = 220),
        label = "browser-bar",
    )
    val navBottomPx = WindowInsets.navigationBars.getBottom(density)
    val barHeightDp = with(density) { barHeightPx.toDp() }
    val webBottomPadding = if (!isInPictureInPicture && (imeVisibleNow || findBarVisible || reserveBarSpace)) barHeightDp else 0.dp

    // Inherit the already-scaled app density; do not multiply the app text size twice.
    HiPlayerTheme(themeMode = browserTheme, uiTextScale = 1f) {
    BrowserNavigationHost(
        modifier = modifier.fillMaxSize().background(palette.background),
        enabled = !isInPictureInPicture && !fullscreen.active,
        revealButtonBottomInset = 88.dp,
        navigation = appNavigation,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isInPictureInPicture) Modifier
                    else Modifier.windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                )
                .padding(bottom = webBottomPadding),
        ) {
        if (rendererGone) {
            Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("The browser page closed to free memory", color = palette.textPrimary)
                Spacer(Modifier.height(10.dp))
                Button(onClick = { rendererGone = false; activeTab?.let { showStartPage = it.url.isBlank() } }) { Text("Reload browser") }
            }
        } else if (!showStartPage && activeTab != null && !useSecureWebsite) {
            key(activeTab.id, webViewGeneration) {
                AndroidView(
                    factory = { viewContext ->
                        val tabId = activeTab.id
                        // The WebView's theme decides prefers-color-scheme for every site:
                        // a light theme unless the browser's dark theme is switched on.
                        val themedContext = createOrbitWebContext(viewContext, store.darkMode)
                        // A tab that is still loaded (e.g. after returning to browser mode) keeps
                        // its WebView and page; only its callbacks are rebound to this screen.
                        val existing = activeTab.webView?.takeIf { isOrbitWebViewUsable(it) }
                        val view = existing ?: WebView(themedContext).apply {
                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            setBackgroundColor(if (store.darkMode) android.graphics.Color.rgb(18, 18, 18) else android.graphics.Color.WHITE)
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = false
                                cacheMode = when {
                                    activeTab.privateTab -> WebSettings.LOAD_NO_CACHE
                                    !networkOnline -> WebSettings.LOAD_CACHE_ELSE_NETWORK
                                    else -> WebSettings.LOAD_DEFAULT
                                }
                                allowFileAccess = false
                                allowContentAccess = false
                                setSupportMultipleWindows(true)
                                javaScriptCanOpenWindowsAutomatically = !store.popupBlock
                                textZoom = store.textZoom
                                mediaPlaybackRequiresUserGesture = true
                                builtInZoomControls = true
                                displayZoomControls = false
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
                            }
                            applyOrbitDesktopMode(this, activeTab.desktop)
                            applyOrbitDarkMode(this, store.darkMode)
                            HiBrowserDocumentScripts.apply(this, viewContext, store, activeTab.desktop)
                            // The bridge only talks to session-level objects, so it stays valid
                            // when this WebView is reattached to a later browser screen.
                            addJavascriptInterface(
                                HiBrowserVideoBridge(
                                    onReport = { src -> if (store.videoHandoff) mediaSniffer.reportVideo(tabId, src) },
                                    onAutoPlay = { direct -> HiBrowserSession.autoPlayHandler?.invoke(tabId, direct) ?: false },
                                ),
                                "HiBrowserNative",
                            )
                        }
                        (view.parent as? ViewGroup)?.removeView(view)
                        view.apply {
                            val browserWebView = this
                            var scrollTravel = 0
                            setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                                if (activeTabId != tabId) return@setOnScrollChangeListener
                                val dy = scrollY - oldScrollY
                                if (dy != 0 && reserveBarSpace) reserveBarSpace = false
                                if (scrollY <= 0) {
                                    scrollTravel = 0
                                    if (browserBarHidden) browserBarHidden = false
                                    return@setOnScrollChangeListener
                                }
                                // Count travel in one direction so tiny jitters don't toggle the bar.
                                scrollTravel = if (dy == 0 || (dy > 0) == (scrollTravel > 0)) scrollTravel + dy else dy
                                if (scrollTravel > 40 && scrollY > 120 && !browserBarHidden) browserBarHidden = true
                                else if (scrollTravel < -30 && browserBarHidden) browserBarHidden = false
                            }
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView, progress: Int) {
                                    if (activeTabId == tabId) loadingProgress = progress
                                }
                                override fun onReceivedTitle(view: WebView, title: String?) {
                                    val value = title?.takeIf(String::isNotBlank) ?: view.url ?: "Hi Browser"
                                    updateTab(tabId) { it.copy(title = value, url = view.url ?: it.url) }
                                    mediaSniffer.setTitle(tabId, value)
                                    if (activeTabId == tabId) pageTitle = value
                                }
                                override fun onReceivedIcon(view: WebView, icon: android.graphics.Bitmap?) {
                                    if (!activeTab.privateTab) view.url?.let { HiSiteIcons.saveFavicon(viewContext, it, icon) }
                                }
                                override fun onShowCustomView(customView: View, callback: CustomViewCallback) {
                                    val activity = viewContext.findHiActivity()
                                    if (activity == null) callback.onCustomViewHidden()
                                    else fullscreen.enter(activity, customView, callback)
                                }
                                // Tapped links are user intent: only block destinations on the ad/tracker lists.
                                fun isAdPopup(url: String): Boolean =
                                    OrbitBrowserFilterEngine.shouldBlock(Uri.parse(url), store.adBlock, mediaSniffer.pageHost(tabId))
                                override fun onCloseWindow(window: WebView) {
                                    tabs.firstOrNull { it.webView === window }?.let { closeTab(it.id) }
                                }
                                override fun onHideCustomView() {
                                    fullscreen.exit(viewContext.findHiActivity())
                                }
                                override fun onShowFileChooser(
                                    webView: WebView,
                                    filePathCallback: ValueCallback<Array<Uri>>,
                                    fileChooserParams: FileChooserParams,
                                ): Boolean {
                                    fileChooserCallback.value?.onReceiveValue(null)
                                    fileChooserCallback.value = filePathCallback
                                    return try {
                                        fileChooserLauncher.launch(fileChooserParams.createIntent())
                                        true
                                    } catch (error: Exception) {
                                        fileChooserCallback.value = null
                                        Toast.makeText(viewContext, "No app can pick files here", Toast.LENGTH_SHORT).show()
                                        false
                                    }
                                }
                                override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message): Boolean {
                                    if (store.popupBlock) {
                                        // Only a real link tap may open a window; it opens in this tab like a normal link.
                                        val hit = view.hitTestResult
                                        val tappedLink = isUserGesture && (
                                            hit.type == WebView.HitTestResult.SRC_ANCHOR_TYPE ||
                                                hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
                                            )
                                        if (!tappedLink && isUserGesture) {
                                            val authTransport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                                            authTransport.webView = HiBrowserPopupCatcher.create(viewContext) { linkUrl ->
                                                if (SecureWebSessions.isGoogleAuthentication(linkUrl)) requestSecureSite(SecureWebSessions.entryForGoogleAuth(view.url.orEmpty()), fromPrivateTab = activeTab.privateTab, forSignIn = true, tabId = tabId)
                                                else Toast.makeText(viewContext, "Pop-up blocked", Toast.LENGTH_SHORT).show()
                                            }
                                            resultMsg.sendToTarget()
                                            return true
                                        }
                                        if (!tappedLink) {
                                            Toast.makeText(viewContext, "Pop-up blocked", Toast.LENGTH_SHORT).show()
                                            return false
                                        }
                                        val href = hit.extra?.takeIf {
                                            hit.type == WebView.HitTestResult.SRC_ANCHOR_TYPE &&
                                                (it.startsWith("http://") || it.startsWith("https://"))
                                        }
                                        if (href != null) {
                                            if (SecureWebSessions.isGoogleAuthentication(href)) requestSecureSite(SecureWebSessions.entryForGoogleAuth(view.url.orEmpty()), fromPrivateTab = activeTab.privateTab, forSignIn = true, tabId = tabId)
                                            else if (prefersSecureWebsite(href, activeTab.privateTab)) openSecureWebsite(href, activeTab.privateTab, tabId)
                                            else if (!isAdPopup(href)) view.loadUrl(href)
                                            else Toast.makeText(viewContext, "Ad pop-up blocked", Toast.LENGTH_SHORT).show()
                                            return false
                                        }
                                        val linkTransport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                                        linkTransport.webView = HiBrowserPopupCatcher.create(viewContext) { linkUrl ->
                                            if (SecureWebSessions.isGoogleAuthentication(linkUrl)) requestSecureSite(SecureWebSessions.entryForGoogleAuth(view.url.orEmpty()), fromPrivateTab = activeTab.privateTab, forSignIn = true, tabId = tabId)
                                            else if (prefersSecureWebsite(linkUrl, activeTab.privateTab)) openSecureWebsite(linkUrl, activeTab.privateTab, tabId)
                                            else if (!isAdPopup(linkUrl)) view.loadUrl(linkUrl)
                                            else Toast.makeText(viewContext, "Ad pop-up blocked", Toast.LENGTH_SHORT).show()
                                        }
                                        resultMsg.sendToTarget()
                                        return true
                                    }
                                    val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                                    transport.webView = HiBrowserPopupCatcher.create(viewContext) { popupUrl ->
                                        openUrlFromPage(popupUrl, view, activeTab.privateTab, isUserGesture)
                                    }
                                    resultMsg.sendToTarget()
                                    return true
                                }
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    val uri = request.url
                                    if (request.isForMainFrame && prefersSecureWebsite(uri.toString(), activeTab.privateTab)) {
                                        if (request.hasGesture()) openSecureWebsite(uri.toString(), activeTab.privateTab, tabId)
                                        else {
                                            // A script/redirect must not launch another app on its own.
                                            val history = captureOrbitHistory(view)
                                            updateTab(tabId) { it.copy(url = uri.toString(), webView = null, historyState = history ?: it.historyState) }
                                            view.onPause()
                                            view.post { disposeOrbitWebView(view) }
                                            if (activeTabId == tabId) {
                                                currentUrl = uri.toString()
                                                address = uri.toString()
                                                pendingFirstPaint = false
                                                loadingProgress = 100
                                            }
                                        }
                                        return true
                                    }
                                    if (request.isForMainFrame && SecureWebSessions.isGoogleAuthentication(uri.toString())) {
                                        requestSecureSite(SecureWebSessions.entryForGoogleAuth(view.url.orEmpty()), fromPrivateTab = activeTab.privateTab, forSignIn = true, tabId = tabId)
                                        return true
                                    }
                                    if (request.isForMainFrame && (BrowserVideoPolicy.isAdUrl(uri.toString()) || OrbitBrowserFilterEngine.shouldBlock(uri, store.adBlock, mediaSniffer.pageHost(tabId)))) return true
                                    if (uri.scheme == "http" || uri.scheme == "https") {
                                        if (request.isForMainFrame && !isOrbitYoutube(uri.toString())) {
                                            val stream = StreamUrlClassifier.classify(uri.toString())
                                            if (BrowserVideoPolicy.isAdUrl(uri.toString())) return true
                                            if (stream.isPlayableInHiPlayer && !stream.requiresWebResolution) {
                                                // Preserve the current page. Script redirects and autoplay
                                                // must never replace it with an ad in the native player.
                                                if (BrowserVideoPolicy.offerNavigation(request.hasGesture(), request.isRedirect, false)) {
                                                    // A tapped link to a video file: play it now when auto-play is on.
                                                    if (store.autoPlayInPlayer && activeTabId == tabId) playBrowserVideo(tabId, uri.toString())
                                                    else pendingVideoNavigation = uri.toString()
                                                }
                                                return true
                                            }
                                        }
                                        if (request.isForMainFrame && !activeTab.privateTab) {
                                            if (request.isRedirect) {
                                                view.url?.takeIf { BrowserHistoryPolicy.isWebUrl(it) }?.let { redirectSources.putIfAbsent(tabId, it) }
                                            } else redirectSources.remove(tabId)
                                        }
                                        return false
                                    }
                                    if (uri.scheme in setOf("mailto", "tel", "sms")) {
                                        runCatching { viewContext.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                                    }
                                    return true
                                }
                                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                                    if (tabs.firstOrNull { it.id == tabId }?.url.isNullOrBlank()) return
                                    mediaSniffer.setPage(tabId, url)
                                    updateTab(tabId) { it.copy(url = url) }
                                    if (pageError?.tabId == tabId) pageError = null
                                    if (activeTabId == tabId) {
                                        currentUrl = url
                                        if (!addressFocused) address = url
                                        loadingProgress = 1
                                        pendingFirstPaint = true
                                        browserBarHidden = false
                                    }
                                }
                                override fun onPageCommitVisible(view: WebView, url: String) {
                                    applyOrbitTextZoom(view, store.textZoom)
                                    if (!HiBrowserDocumentScripts.isSupported() && tabs.firstOrNull { it.id == tabId }?.desktop == true)
                                        injectOrbitAsset(view, viewContext, "orbit_desktop_viewport.js")
                                    if (activeTabId == tabId) pendingFirstPaint = false
                                    injectCosmeticCss(view, url)
                                }
                                override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                                    if (tabs.firstOrNull { it.id == tabId }?.url.isNullOrBlank()) return
                                    if (!url.startsWith("http://") && !url.startsWith("https://")) return
                                    // Single-page sites change address without loading a new page.
                                    mediaSniffer.setPage(tabId, url)
                                    if (tabs.firstOrNull { it.id == tabId }?.url != url) updateTab(tabId) { it.copy(url = url) }
                                    if (!activeTab.privateTab && view.progress == 100) recordHistory(view.title ?: url, url)
                                    if (activeTabId == tabId) {
                                        currentUrl = url
                                        if (!addressFocused) address = url
                                    }
                                }
                                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
                                    if (!request.isForMainFrame) return
                                    redirectSources.remove(tabId)
                                    if (activeTabId == tabId) pendingFirstPaint = false
                                    if (activeTabId == tabId) {
                                        pageError = HiBrowserPageError(tabId, request.url.toString(), offline = !networkOnline)
                                        loadingProgress = 100
                                    }
                                }
                                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                                    if (request.isForMainFrame && activeTabId == tabId && response.statusCode >= 400) {
                                        android.util.Log.w("HiBrowser", "Page request failed (${response.statusCode})")
                                        pendingFirstPaint = false
                                        pageError = HiBrowserPageError(tabId, request.url.toString(), offline = false)
                                        loadingProgress = 100
                                    }
                                }
                                override fun onReceivedSslError(view: WebView, handler: android.webkit.SslErrorHandler, error: android.net.http.SslError) {
                                    handler.cancel() // Never bypass certificate validation to make an error disappear.
                                    // A broken third-party subresource must not replace an otherwise valid page.
                                    if (activeTabId == tabId && error.url == view.url) {
                                        pendingFirstPaint = false
                                        pageError = HiBrowserPageError(tabId, view.url.orEmpty(), offline = false)
                                        loadingProgress = 100
                                    }
                                }
                                override fun onPageFinished(view: WebView, url: String) {
                                    applyOrbitTextZoom(view, store.textZoom)
                                    if (!HiBrowserDocumentScripts.isSupported() && tabs.firstOrNull { it.id == tabId }?.desktop == true)
                                        injectOrbitAsset(view, viewContext, "orbit_desktop_viewport.js")
                                    if (tabs.firstOrNull { it.id == tabId }?.url.isNullOrBlank()) return
                                    // One update per finished page: history snapshot, address and title.
                                    val history = captureOrbitHistory(view)
                                    updateTab(tabId) { it.copy(url = url, title = view.title ?: it.title, historyState = history ?: it.historyState) }
                                    view.title?.takeIf(String::isNotBlank)?.let { mediaSniffer.setTitle(tabId, it) }
                                    if (activeTabId == tabId) {
                                        currentUrl = url
                                        if (!addressFocused) address = url
                                        pageTitle = view.title?.takeIf(String::isNotBlank) ?: url
                                        loadingProgress = 100
                                        pendingFirstPaint = false
                                    }
                                    if (!activeTab.privateTab && BrowserHistoryPolicy.isWebUrl(url)) {
                                        recordHistory(view.title ?: url, url, redirectedFrom = redirectSources.remove(tabId))
                                    }
                                    injectCosmeticCss(view, url)
                                    if (!HiBrowserDocumentScripts.isSupported()) {
                                        if (store.adBlock) injectOrbitAsset(view, viewContext, "orbit_adblock.js")
                                        if (store.pageSkipper) injectOrbitAsset(view, viewContext, "orbit_page_skipper.js")
                                        if (store.youtubeAdSkip && isOrbitYoutube(url)) injectOrbitAsset(view, viewContext, "orbit_youtube.js")
                                        if (!isOrbitYoutube(url)) view.evaluateJavascript(HI_BROWSER_VIDEO_HOOK_SCRIPT, null)
                                    }
                                }
                                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                    val pageHost = if (request.isForMainFrame) null else mediaSniffer.pageHost(tabId)
                                    val filtered = OrbitBrowserFilterEngine.shouldBlock(request.url, adBlockFlag.enabled, pageHost, mainFrame = request.isForMainFrame)
                                    if (filtered) return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                                    mediaSniffer.observeRequest(tabId, request.url)
                                    return null
                                }
                                override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                                    disposeOrbitWebView(view)
                                    updateTab(tabId) { it.copy(webView = null) }
                                    // Also when the browser screen is closed and this page was kept loaded.
                                    HiBrowserSession.tabs = HiBrowserSession.tabs?.map { if (it.webView === view) it.copy(webView = null) else it }
                                    if (activeTabId == tabId) rendererGone = true
                                    return true
                                }
                            }
                            setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, size ->
                                val name = DownloadFileResolver.resolve(URLUtil.guessFileName(url, contentDisposition, mimeType), url, mimeType, contentDisposition).fileName
                                pendingDownload = BrowserDownloadRequest(url, userAgent.orEmpty(), contentDisposition.orEmpty(), mimeType, name, size.coerceAtLeast(0), this.url.orEmpty())
                            })
                            if (existing == null) {
                                val restored = activeTab.historyState?.let { state -> runCatching { restoreState(state) }.getOrNull() }
                                if (activeTab.url.isNotBlank() && (restored == null || restored.currentItem?.url != activeTab.url)) loadUrl(activeTab.url)
                            } else {
                                onResume()
                            }
                        }
                        updateTab(tabId) { it.copy(webView = view) }
                        view
                    },
                    update = { view ->
                        // Retained tabs must receive live settings, not just factory defaults.
                        applyOrbitTextZoom(view, textZoomPercent)
                        applyOrbitDarkMode(view, darkModeEnabled)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        }
        val visibleError = pageError?.takeIf { it.tabId == activeTabId && !showStartPage }
        if (useSecureWebsite && !isInPictureInPicture) {
            HiBrowserWebsiteSessionPage(
                url = currentUrl,
                onOpen = { openSecureWebsite(currentUrl, activeTab?.privateTab == true) },
                onUseEmbedded = {
                    val url = currentUrl
                    if (activeTab?.privateTab == true) privateSecureOrigins = privateSecureOrigins - setOfNotNull(SecureWebsitePolicy.originKey(url))
                    else SecureWebSessions.forgetBrowser(context, url)
                    secureRoutingVersion++
                    loadAddress(url)
                },
                bottomInset = barHeightDp,
            )
        } else if (visibleError != null && !isInPictureInPicture && !rendererGone) {
            HiBrowserErrorPage(
                url = visibleError.url,
                offline = visibleError.offline || !networkOnline,
                onRetry = { retryPage() },
                onHome = { showBrowserHome() },
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                bottomInset = barHeightDp,
            )
        } else if (!showStartPage && pendingFirstPaint && !isInPictureInPicture && !rendererGone) {
            HiBrowserLoadingPage(
                url = currentUrl,
                slow = slowLoad,
                offline = !networkOnline,
                onStop = {
                    webView?.stopLoading()
                    pendingFirstPaint = false
                    loadingProgress = 100
                },
                onRetry = {
                    pendingFirstPaint = true
                    slowLoad = false
                    val target = currentUrl
                    if (webView != null && target.isNotBlank()) webView.loadUrl(target) else webView?.reload()
                },
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                bottomInset = barHeightDp,
            )
        }
        if (showStartPage && !isInPictureInPicture && !rendererGone) {
            HiBrowserHome(
                isPrivate = activeTab?.privateTab == true,
                shortcuts = shortcuts,
                adBlockEnabled = adBlockEnabled,
                onVisit = { loadAddress(it) },
                onAddShortcut = { name, url ->
                    store.saveShortcuts(store.shortcuts() + OrbitBrowserEntry(name, url))
                    shortcutsVersion++
                },
                onRemoveShortcut = { url ->
                    store.saveShortcuts(store.shortcuts().filterNot { it.url == url })
                    shortcutsVersion++
                },
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                bottomInset = barHeightDp,
            )
        }
        if (!isInPictureInPicture) {
            val onPage = !showStartPage
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                    .graphicsLayer {
                        translationY = barHiddenFraction * (barHeightPx + navBottomPx + 24.dp.toPx())
                    }
                    .onSizeChanged { barHeightPx = it.height },
            ) {
                if (onPage && findBarVisible) {
                    BrowserFindBar(
                        query = findQuery,
                        matchLabel = findMatchLabel,
                        onQueryChange = { findInPage(it) },
                        onPrevious = { webView?.findNext(false) },
                        onNext = { webView?.findNext(true) },
                        onClose = {
                            findBarVisible = false
                            findQuery = ""
                            findMatchLabel = ""
                        },
                    )
                }
                // Fixed-height slot so the bar's height never changes while pages load.
                Box(modifier = Modifier.padding(horizontal = 30.dp).fillMaxWidth().height(2.dp)) {
                    if (onPage && loadingProgress in 1..99) {
                        LinearProgressIndicator(
                            progress = { loadingProgress / 100f }, color = palette.primary, trackColor = palette.surfaceBorder.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(1.dp)),
                        )
                    }
                }
                if (addressFocused && activeTab?.privateTab != true) {
                    BrowserHistorySuggestions(
                        entries = remember(recentEntries, address) { BrowserHistoryPolicy.suggestions(recentEntries, address) },
                        onOpen = { browserFocusManager.clearFocus(); addressFocused = false; loadAddress(it) },
                    )
                }
                HiBrowserToolbar(
                    address = address,
                    onFocusChange = { addressFocused = it },
                    onAddressChange = { address = it },
                    onSubmit = { loadAddress(it) },
                    isPrivate = activeTab?.privateTab == true,
                    isSecure = onPage && currentUrl.startsWith("https://"),
                    isLoading = onPage && loadingProgress in 1..99,
                    showReload = onPage,
                    tabCount = tabs.size,
                    providers = BrowserSearchProvider.entries.map { it.displayName },
                    selectedProvider = searchProvider.displayName,
                    onProviderSelect = { index ->
                        searchProvider = BrowserSearchProvider.entries[index]
                        store.searchProviderName = searchProvider.name
                    },
                    onHome = { showBrowserHome() },
                    onVoiceSearch = { startVoiceSearch() },
                    onReloadOrStop = {
                        if (useSecureWebsite) {
                            openSecureWebsite(currentUrl, activeTab?.privateTab == true)
                        } else if (loadingProgress in 1..99) {
                            webView?.stopLoading()
                            loadingProgress = 100
                            pendingFirstPaint = false
                        } else {
                            webView?.reload()
                        }
                    },
                    onShowTabs = {
                        captureThumbnail()
                        showTabsDialog = true
                    },
                    onMenu = {
                        captureThumbnail()
                        menuExpanded = true
                    },
                )
            }
        }
    }

    if (showBrowserSettings) {
        BrowserSettingsDialog(
            accountModel = accountModel,
            onPlayAccountMedia = onPlayAccountMedia,
            appTextSizeSp = appTextSizeSp,
            onAppTextSizeChange = onAppTextSizeChange,
            darkMode = darkModeEnabled,
            adBlock = adBlockEnabled,
            pageSkipper = pageSkipperEnabled,
            youtubeAdSkip = youtubeAdSkipEnabled,
            videoHandoff = videoHandoffEnabled,
            onVideoHandoffChange = { setVideoHandoffEnabled(it) },
            autoPlayInPlayer = autoPlayEnabled,
            onAutoPlayInPlayerChange = { setAutoPlayEnabled(it) },
            desktopSite = activeTab?.desktop == true,
            onDarkModeChange = { setDarkMode(it) },
            onAdBlockChange = { setAdBlockEnabled(it) },
            onPageSkipperChange = { enabled ->
                store.pageSkipper = enabled
                pageSkipperEnabled = enabled
                refreshDocumentScripts()
            },
            onYoutubeAdSkipChange = { enabled ->
                store.youtubeAdSkip = enabled
                youtubeAdSkipEnabled = enabled
                refreshDocumentScripts()
            },
            onDesktopSiteChange = { enabled -> if (enabled != (activeTab?.desktop == true)) toggleDesktopSite() },
            onUpdateFilterLists = { updateFilterLists() },
            textZoom = textZoomPercent,
            popupBlock = popupBlockEnabled,
            onTextZoomChange = { setTextZoom(it) },
            onPopupBlockChange = { setPopupBlockEnabled(it) },
            onClearBrowsingData = { showBrowserSettings = false; showClearDataDialog = true },
            onDismiss = { showBrowserSettings = false },
        )
    }

    if (showClearDataDialog) {
        BrowserClearDataDialog(
            onConfirm = { history, cookies, cache ->
                showClearDataDialog = false
                clearBrowsingData(history, cookies, cache)
            },
            onDismiss = { showClearDataDialog = false },
        )
    }

    if (showTabsDialog) {
        HiBrowserTabSwitcher(
            tabs = tabs.map { tab ->
                HiBrowserTabCard(
                    id = tab.id,
                    title = tab.title,
                    url = tab.url,
                    privateTab = tab.privateTab,
                    thumbnail = thumbnails[tab.id],
                    favicon = runCatching { tab.webView?.favicon?.asImageBitmap() }.getOrNull(),
                )
            },
            activeTabId = activeTabId,
            startPrivate = activeTab?.privateTab == true,
            onSelect = { id ->
                tabs.firstOrNull { it.id == id }?.let { activateTab(it) }
                showTabsDialog = false
            },
            onClose = { id ->
                thumbnails.remove(id)
                closeTab(id)
            },
            onNewTab = { isPrivate ->
                showTabsDialog = false
                openNewTab(isPrivate)
            },
            onCloseAll = { isPrivate -> closeAllTabs(isPrivate) },
            onDismiss = { showTabsDialog = false },
        )
    }

    librarySection?.let { section ->
        val libraryBookmarks = remember(entriesVersion) { store.bookmarks().map { it.title to it.url } }
        val libraryHistory = remember(entriesVersion) { store.history().map { it.title to it.url } }
        key(section) {
        HiBrowserLibrary(
            initialSection = section,
            appNavigation = appNavigation,
            bookmarks = libraryBookmarks,
            history = libraryHistory,
            onOpen = { loadAddress(it) },
            onRemove = { from, url ->
                if (from == HiLibrarySection.BOOKMARKS) store.removeBookmark(url) else store.removeHistory(url)
                entriesVersion++
            },
            onClearHistory = {
                store.clearHistory()
                entriesVersion++
            },
            downloadsContent = { query ->
                BrowserDownloadsPanel(downloads, query, onPlay = { id -> BrowserDownloads.playInPlayer(context, id, onPlayDirectMedia) })
            },
            onDismiss = { librarySection = null },
        )
        }
    }

    pendingDownload?.let { request ->
        BrowserDownloadSheet(
            request = request,
            onDismiss = { pendingDownload = null },
            onDownload = { info, name, customName, treeUri, label ->
                BrowserDownloads.enqueue(context, info.url, info.userAgent, info.contentDisposition, info.mimeType, info.referer,
                    fileName = name, totalBytes = info.totalBytes, destinationTreeUri = treeUri, destinationLabel = label, customName = customName)
                pendingDownload = null
                librarySection = HiLibrarySection.DOWNLOADS
            },
            onPlay = { info ->
                pendingDownload = null
                registerBrowserStreamHeaders(info.url, info.referer, info.userAgent, emptyList())
                if (onPlayBrowserVideo != null) onPlayBrowserVideo(info.url, info.mimeType, info.fileName) else onPlayDirectMedia(info.url)
            },
        )
    }

    pendingSecureSite?.let { request ->
        val host = Uri.parse(request.url).host.orEmpty()
        AlertDialog(
            onDismissRequest = { pendingSecureSite = null },
            title = { Text(if (request.forSignIn) "Website sign-in" else "Open website in browser") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (request.forSignIn) {
                        // Real Firebase profile, clearly labelled as app identity, not a website cookie session.
                        GoogleAccountStatusRow(profile = accountProfile)
                        Text("$host needs its own website sign-in. Continue in your device browser, then use the website's Sign in button.", color = palette.textPrimary)
                        Text("This site will keep opening in the same browser, where its website session stays. Hi Player cannot read which Google account you choose there.", color = palette.textPrimary)
                        if (accountProfile == null) TextButton(onClick = {
                            pendingSecureSite = null
                            showAccount = true
                        }) { Text("Sign in to Hi Player instead") }
                    } else {
                        Text("Open $host with your device browser's saved website sessions. Future visits to this site will use that browser too.", color = palette.textPrimary)
                    }
                    if (request.fromPrivateTab) Text(
                        "This leaves private browsing. Your device browser may use its saved accounts.",
                        color = palette.textPrimary,
                    )
                }
            },
            confirmButton = { TextButton(onClick = {
                if (openSecureWebsite(request.url, request.fromPrivateTab, request.tabId)) pendingSecureSite = null
            }) { Text("Continue in browser") } },
            dismissButton = { TextButton(onClick = { pendingSecureSite = null }) { Text("Cancel") } },
        )
    }

    pendingVideoNavigation?.let { candidate ->
        AlertDialog(
            modifier = androidx.compose.ui.Modifier.hiContainerBorder(androidx.compose.foundation.shape.RoundedCornerShape(28.dp)),
            onDismissRequest = { pendingVideoNavigation = null },
            title = { Text("Open video in Hi Player?") },
            text = { Text("Your original page will stay open.\n\n" + candidate.take(300)) },
            confirmButton = { TextButton(onClick = { pendingVideoNavigation = null; playBrowserVideo(activeTabId, candidate) }) { Text("Play video") } },
            dismissButton = { TextButton(onClick = { pendingVideoNavigation = null }) { Text("Stay on page") } },
        )
    }

    if (menuExpanded) {
        val onPage = !showStartPage && !useSecureWebsite
        val bookmarked = onPage && store.isBookmarked(currentUrl)
        HiBrowserMenuSheet(
            tiles = listOf(
                HiMenuAction(if (accountProfile == null) "Sign in with Google" else "Your account · signed in", Icons.Default.AccountCircle) { showAccount = true },
                HiMenuAction("Bookmarks", Icons.Default.Bookmark) { librarySection = HiLibrarySection.BOOKMARKS },
                HiMenuAction("History", Icons.Default.History) { librarySection = HiLibrarySection.HISTORY },
                HiMenuAction("Downloads", Icons.Default.Download) { librarySection = HiLibrarySection.DOWNLOADS },
                HiMenuAction("New tab", Icons.Default.Add) { openNewTab(false) },
            ),
            onSetDefaultBrowser = if (isDefaultBrowser) null else ({ requestDefaultBrowser() }),
            tools = buildList {
                if (onPage) {
                    add(HiMenuAction("Share", Icons.Default.Share) {
                        runCatching {
                            context.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, currentUrl),
                                    "Share page",
                                ),
                            )
                        }
                    })
                    add(HiMenuAction("Find in page", Icons.Default.FindInPage) { findBarVisible = true })
                    add(HiMenuAction("Open in secure browser", Icons.Default.OpenInNew) { requestSecureSite(currentUrl) })
                    add(HiMenuAction("Translate", Icons.Default.Translate) { translateCurrentPage() })
                    add(HiMenuAction("Reader mode", Icons.Default.Language) { readCurrentPage() })
                    add(HiMenuAction("Desktop site", Icons.Default.DesktopWindows, checked = activeTab?.desktop == true) { toggleDesktopSite() })
                    if (onEnterPictureInPicture != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        add(HiMenuAction("Picture-in-picture", Icons.Default.PictureInPictureAlt) { startPictureInPicture() })
                    }
                    add(HiMenuAction("Add to home screen", Icons.Default.AddCircle) { addCurrentPageToHomeScreen() })
                    if (!isOrbitYoutube(currentUrl)) {
                        add(HiMenuAction("Play in Hi Player", Icons.Default.PlayCircle) {
                            // Prefer the video the page is playing, then a stream it loaded.
                            val found = mediaSniffer.reportedVideo(activeTabId) ?: mediaSniffer.bestManifest(activeTabId)
                            if (found != null) playBrowserVideo(activeTabId, found) else onPlayDirectMedia(currentUrl)
                        })
                    }
                }
                add(HiMenuAction("Scan QR code", Icons.Default.QrCodeScanner) { startQrScan() })
                add(HiMenuAction("Clear data", Icons.Default.DeleteSweep) { showClearDataDialog = true })
            },
            rows = listOf(
                HiMenuAction("New private tab", Icons.Default.VisibilityOff) { openNewTab(true) },
                HiMenuAction("Settings", Icons.Default.Settings) { showBrowserSettings = true },
                HiMenuAction("Google website sign-in", Icons.Default.Language) {
                    val site = currentUrl.takeIf { !showStartPage && SecureWebSessions.validPage(it) }
                        ?.let(SecureWebSessions::entryForGoogleAuth) ?: SecureWebSessions.GOOGLE_ACCOUNT
                    requestSecureSite(site, forSignIn = true)
                },
                HiMenuAction("Close this tab", Icons.Default.Close) { closeTab(activeTabId) },
            ),
            bottomRow = listOf(
                HiMenuAction("Back", Icons.Default.ArrowBack, enabled = !showStartPage || tabs.size > 1) { navigateBrowserBack() },
                HiMenuAction("Forward", Icons.Default.ArrowForward, enabled = webView?.canGoForward() == true) { webView?.goForward() },
                HiMenuAction(
                    if (bookmarked) "Remove bookmark" else "Add bookmark",
                    if (bookmarked) Icons.Default.Star else Icons.Default.StarBorder,
                    enabled = onPage,
                    checked = bookmarked,
                ) { addBookmark() },
                HiMenuAction("Exit browser", Icons.Default.PowerSettingsNew) { onBack() },
                HiMenuAction("Reload", Icons.Default.Refresh, enabled = onPage) { webView?.reload() },
            ),
            onDismiss = { menuExpanded = false },
        )
    }
    if (showAccount) GoogleAccountDialog(
        onDismiss = { showAccount = false },
        onPlayDriveMedia = { video -> showAccount = false; onPlayAccountMedia(video) },
        model = accountModel,
    )
    } // Browser appearance theme
}

/** WebViews already destroyed; a stale tab reference to one must never be reattached. */
private val hiDestroyedWebViews: MutableSet<WebView> = java.util.Collections.newSetFromMap(WeakHashMap())

private fun isOrbitWebViewUsable(webView: WebView?): Boolean = webView != null && webView !in hiDestroyedWebViews

private fun disposeOrbitWebView(webView: WebView) {
    if (!hiDestroyedWebViews.add(webView)) return
    HiBrowserDocumentScripts.remove(webView)
    orbitMobileMetadata.remove(webView)
    runCatching {
        webView.removeJavascriptInterface("HiBrowserNative")
        // A released page must not deliver late completion into a browser-owned tab.
        webView.setWebViewClient(WebViewClient())
        webView.setWebChromeClient(null)
        webView.stopLoading()
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
    }
}

/** Main-frame failures caused by the connection (not by the site or a blocked request). */
private fun hiBrowserIsNetworkError(code: Int, description: String): Boolean {
    if (description.contains("ERR_ABORTED", ignoreCase = true) || description.contains("ERR_BLOCKED", ignoreCase = true)) return false
    if (code == WebViewClient.ERROR_HOST_LOOKUP || code == WebViewClient.ERROR_CONNECT ||
        code == WebViewClient.ERROR_IO || code == WebViewClient.ERROR_TIMEOUT
    ) return true
    return listOf(
        "ERR_INTERNET_DISCONNECTED", "ERR_NAME_NOT_RESOLVED", "ERR_CONNECTION_", "ERR_TIMED_OUT",
        "ERR_NETWORK_CHANGED", "ERR_ADDRESS_UNREACHABLE", "ERR_PROXY_CONNECTION_FAILED", "ERR_NETWORK_ACCESS_DENIED",
    ).any { description.contains(it, ignoreCase = true) }
}

/** A flag read from WebView's network threads. */
private class HiBrowserFlag(initial: Boolean) {
    @Volatile var enabled: Boolean = initial
}

/** Finds the Activity behind a (possibly themed) context. */
private tailrec fun Context.findHiActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHiActivity()
    else -> null
}

/** MIME type for a stream URL Hi Player should open (null lets the player sniff). */
private fun hiBrowserStreamMimeType(url: String): String? {
    val path = runCatching { Uri.parse(url).path }.getOrNull()?.lowercase().orEmpty()
    return when {
        path.endsWith(".m3u8") -> "application/x-mpegURL"
        path.endsWith(".mpd") -> "application/dash+xml"
        else -> null
    }
}

/**
 * Lets Hi Player fetch a web video the way the page did: same referrer, user
 * agent and cookies, for the stream's host and the hosts that served its media.
 */
private fun registerBrowserStreamHeaders(streamUrl: String, pageUrl: String, userAgent: String?, mediaHosts: Collection<String>) {
    val base = LinkedHashMap<String, String>()
    if (pageUrl.startsWith("http://") || pageUrl.startsWith("https://")) {
        base["Referer"] = pageUrl
        runCatching { Uri.parse(pageUrl) }.getOrNull()?.let { page ->
            if (!page.scheme.isNullOrBlank() && !page.authority.isNullOrBlank()) base["Origin"] = "${page.scheme}://${page.authority}"
        }
    }
    userAgent?.takeIf { it.isNotBlank() }?.let { base["User-Agent"] = it }
    val origins = LinkedHashSet<String>()
    runCatching { Uri.parse(streamUrl) }.getOrNull()?.let { stream ->
        if (!stream.scheme.isNullOrBlank() && !stream.authority.isNullOrBlank()) origins += "${stream.scheme}://${stream.authority}"
    }
    origins += mediaHosts
    val cookies = CookieManager.getInstance()
    origins.take(8).forEach { origin ->
        val headers = LinkedHashMap(base)
        runCatching { cookies.getCookie(origin) }.getOrNull()?.takeIf { it.isNotBlank() }?.let { headers["Cookie"] = it }
        if (headers.isNotEmpty()) RemoteStreamHeadersRegistry.register(origin, headers)
    }
}

/**
 * What each tab's page is playing or streaming: video sources the page reports
 * and HLS/DASH manifests it fetches (for players that use blob: sources).
 * Written from WebView's network/JavaBridge threads, so everything is thread-safe.
 */
private class HiBrowserMediaSniffer {
    @Volatile var activeTabId: Int = 1

    private class Manifest(val url: String, val atMs: Long)

    private class TabMedia {
        @Volatile var pageUrl: String = ""
        @Volatile var pageHost: String? = null
        @Volatile var title: String = ""
        @Volatile var reportedVideo: String? = null
        @Volatile var lastHandoffAtMs: Long = 0L
        val manifests = CopyOnWriteArrayList<Manifest>()
        val mediaHosts: MutableSet<String> = ConcurrentHashMap.newKeySet()
    }

    private val tabs = ConcurrentHashMap<Int, TabMedia>()

    private fun tab(id: Int): TabMedia = tabs.getOrPut(id) { TabMedia() }

    fun setPage(id: Int, url: String) {
        val media = tab(id)
        if (media.pageUrl.substringBefore('#') != url.substringBefore('#')) {
            media.manifests.clear()
            media.mediaHosts.clear()
            media.reportedVideo = null
            media.title = ""
        }
        media.pageUrl = url
        media.pageHost = runCatching { Uri.parse(url).host }.getOrNull()
    }

    fun setTitle(id: Int, title: String) { tab(id).title = title }
    fun pageUrl(id: Int): String = tabs[id]?.pageUrl.orEmpty()
    fun pageHost(id: Int): String? = tabs[id]?.pageHost
    fun title(id: Int): String = tabs[id]?.title.orEmpty()
    fun mediaHosts(id: Int): Set<String> = tabs[id]?.mediaHosts?.toSet().orEmpty()
    fun reportedVideo(id: Int): String? = tabs[id]?.reportedVideo

    fun reportVideo(id: Int, src: String) {
        if (!isOrbitYoutube(src) && !BrowserVideoPolicy.isAdUrl(src)) tab(id).reportedVideo = src
    }

    fun observeRequest(id: Int, uri: Uri) {
        if (BrowserVideoPolicy.isAdUrl(uri.toString())) return
        val scheme = uri.scheme
        if (scheme != "http" && scheme != "https") return
        val path = uri.path?.lowercase() ?: return
        val manifest = path.endsWith(".m3u8") || path.endsWith(".mpd")
        val segment = !manifest && (path.endsWith(".ts") || path.endsWith(".m4s") || path.endsWith(".mp4") ||
            path.endsWith(".m4v") || path.endsWith(".webm") || path.endsWith(".aac") || path.endsWith(".m4a"))
        if (!manifest && !segment) return
        val media = tab(id)
        val authority = uri.authority ?: return
        if (media.mediaHosts.size < 8) media.mediaHosts += "$scheme://$authority"
        if (manifest && media.manifests.size < 16) {
            val url = uri.toString()
            if (media.manifests.none { it.url == url }) media.manifests += Manifest(url, System.currentTimeMillis())
        }
    }

    /** The page's main stream: an HLS master playlist when present, else the first manifest seen. */
    fun bestManifest(id: Int): String? {
        val recent = tabs[id]?.manifests?.filter { System.currentTimeMillis() - it.atMs < 30 * 60_000L }.orEmpty()
        if (recent.isEmpty()) return null
        val hls = recent.filter { Uri.parse(it.url).path.orEmpty().lowercase().endsWith(".m3u8") }
        return (hls.firstOrNull { it.url.contains("master", ignoreCase = true) } ?: hls.firstOrNull() ?: recent.first()).url
    }

    /** True at most once every few seconds per tab, so one play doesn't open the player twice. */
    fun claimHandoff(id: Int): Boolean {
        val media = tab(id)
        synchronized(media) {
            val now = System.currentTimeMillis()
            if (now - media.lastHandoffAtMs < 4_000L) return false
            media.lastHandoffAtMs = now
            return true
        }
    }
}

/** Shows a page's full-screen video (YouTube's full-screen button, etc.) over the app. */
private class HiBrowserFullscreen {
    private var view: View? = null
    private var callback: WebChromeClient.CustomViewCallback? = null
    private var previousOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    val active: Boolean get() = view != null

    fun enter(activity: Activity, customView: View, customCallback: WebChromeClient.CustomViewCallback) {
        if (view != null) {
            customCallback.onCustomViewHidden()
            return
        }
        val decor = activity.window.decorView as? FrameLayout
        if (decor == null) {
            customCallback.onCustomViewHidden()
            return
        }
        view = customView
        callback = customCallback
        previousOrientation = activity.requestedOrientation
        customView.setBackgroundColor(android.graphics.Color.BLACK)
        decor.addView(customView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        WindowCompat.getInsetsController(activity.window, decor).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    fun exit(activity: Activity?) {
        val current = view ?: return
        val currentCallback = callback
        view = null
        callback = null
        (current.parent as? ViewGroup)?.removeView(current)
        if (activity != null) {
            WindowCompat.getInsetsController(activity.window, activity.window.decorView).show(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = previousOrientation
        }
        runCatching { currentCallback?.onCustomViewHidden() }
    }
}

internal class HiBrowserVideoBridge(
    private val onReport: (String) -> Unit,
    /** Opens a user-started page video in Hi Player; returns true when it did. */
    private val onAutoPlay: (String?) -> Boolean = { false },
) {
    /** The page has a video with this direct source (used by "Play in Hi Player"). */
    @JavascriptInterface
    fun reportVideo(rawUrl: String?) {
        val value = rawUrl?.trim()?.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: return
        if (!BrowserVideoPolicy.isAdUrl(value)) runCatching { onReport(value) }
    }

    /**
     * A full-length video the user just started (tap or key press) began playing.
     * [rawUrl] is empty for blob:/MSE players; the page's stream manifest is used then.
     * Without a user gesture, or for ad sources, nothing is handed off.
     */
    @Suppress("UNUSED_PARAMETER")
    @JavascriptInterface
    fun videoStarted(rawUrl: String?, userGesture: Boolean, muted: Boolean): Boolean {
        if (!userGesture) return false
        val direct = rawUrl?.trim()?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        if (direct != null && BrowserVideoPolicy.isAdUrl(direct)) return false
        direct?.let { runCatching { onReport(it) } }
        return runCatching { onAutoPlay(direct) }.getOrDefault(false)
    }
}

private const val HI_BROWSER_PAUSE_MEDIA_SCRIPT =
    "(function(){try{document.querySelectorAll('video,audio').forEach(function(m){try{m.pause();}catch(e){}});}catch(e){}})()"

/**
 * Finds the page's video for "Play in Hi Player" and, when the user starts a
 * video themselves, offers it for auto-play in Hi Player. Runs in every frame
 * (embedded players live in iframes). A video only counts when it was started
 * within a few seconds of a trusted tap/key press, is large on screen, is not
 * inside an ad container and is long (live, or at least MIN_DURATION seconds),
 * so pre-roll and banner ads keep playing on the page. It never pauses media.
 */
internal const val HI_BROWSER_VIDEO_HOOK_SCRIPT = """
(function(){
  var host=(window.location.hostname||'').toLowerCase();
  if(host==='youtu.be'||/(^|\.)youtube(-nocookie)?\.com$/.test(host)) return;
  if(window.__hiVideoHookV4) return;
  window.__hiVideoHookV4=true;
  var GESTURE_MS=4000, MIN_DURATION=45, lastGesture=0, armedUntil=0;
  var AD=/(^|[\s_-])(ad|ads|advert|advertisement|preroll|midroll|vast|vpaid|ima|sponsor|sponsored)([\s_-]|$)/i;
  function gesture(e){ if(e.isTrusted) lastGesture=Date.now(); }
  ['pointerdown','touchend','click','keydown'].forEach(function(t){ document.addEventListener(t,gesture,true); });
  function inAd(v){
    for(var n=v,d=0;n&&n.nodeType===1&&d<8;n=n.parentElement,d++){
      if(n.hasAttribute('data-ad')||n.hasAttribute('data-ad-slot')||n.hasAttribute('data-vast')) return true;
      var cls=typeof n.className==='string'?n.className:'';
      if(AD.test(n.id||'')||AD.test(cls)) return true;
    }
    return false;
  }
  function big(v){ var r=v.getBoundingClientRect(); return r.width>=160&&r.height>=90; }
  function source(v){
    var s=v.currentSrc||v.src||'';
    if(!s){ var el=v.querySelector('source[src]'); if(el) s=el.src||''; }
    return s;
  }
  function report(v){
    if(!v||v.tagName!=='VIDEO'||inAd(v)||!big(v)) return;
    var s=source(v);
    if(!/^https?:\/\//i.test(s)) return;
    try { if(window.HiBrowserNative) window.HiBrowserNative.reportVideo(s); } catch(e) {}
  }
  function userStarted(){ var now=Date.now(); return now-lastGesture<=GESTURE_MS||now<armedUntil; }
  function consider(v){
    if(v.__hiHanded||!v.__hiUserPlay||!window.HiBrowserNative) return;
    if(!big(v)||inAd(v)) return;
    var d=v.duration;
    if(d!==Infinity&&!(d>0)) return;
    if(d!==Infinity&&d<MIN_DURATION){
      // A short clip right after the tap is usually a pre-roll: the real video
      // that follows it (often in the same player) may still open in Hi Player.
      armedUntil=Date.now()+90000;
      return;
    }
    var s=source(v), direct=/^https?:\/\//i.test(s)?s:'';
    try { if(window.HiBrowserNative.videoStarted(direct,true,!!v.muted)) v.__hiHanded=true; } catch(e) {}
  }
  ['play','playing','loadedmetadata','durationchange'].forEach(function(t){
    document.addEventListener(t,function(e){
      var v=e.target;
      if(!v||v.tagName!=='VIDEO'||v.paused) return;
      if((t==='play'||t==='playing')&&userStarted()) v.__hiUserPlay=true;
      consider(v);
    },true);
  });
  document.addEventListener('click',function(e){ if(e.isTrusted) report(e.target); },true);
  document.addEventListener('keydown',function(e){ if(e.isTrusted&&(e.key===' '||e.key==='Enter')) report(e.target); },true);
})()
"""

@SuppressLint("SetJavaScriptEnabled")
private fun injectOrbitAsset(webView: WebView, context: Context, name: String) {
    runCatching { context.assets.open(name).bufferedReader().use { it.readText().take(100_000) } }
        .onSuccess { script -> webView.post { webView.evaluateJavascript(script, null) } }
}

@Suppress("DEPRECATION")
private fun applyOrbitDarkMode(webView: WebView, enabled: Boolean) {
    when {
        WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING) ->
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, enabled)
        WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK) ->
            WebSettingsCompat.setForceDark(
                webView.settings,
                if (enabled) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF,
            )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
        webView.settings.forceDark = if (enabled) WebSettings.FORCE_DARK_ON else WebSettings.FORCE_DARK_OFF
    }
}

/** Explicit configuration AND theme: WebView uses both to negotiate page colors. */
private fun createOrbitWebContext(context: Context, dark: Boolean): Context {
    val configuration = android.content.res.Configuration(context.resources.configuration).apply {
        uiMode = (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or
            if (dark) android.content.res.Configuration.UI_MODE_NIGHT_YES else android.content.res.Configuration.UI_MODE_NIGHT_NO
    }
    return ContextThemeWrapper(context.createConfigurationContext(configuration),
        if (dark) com.example.R.style.Theme_HiBrowser_Dark else com.example.R.style.Theme_HiBrowser_Light)
}

private fun applyOrbitTextZoom(view: WebView, percent: Int) {
    val value = BrowserAppearancePolicy.textZoom(percent)
    if (view.settings.textZoom != value) {
        view.settings.textZoom = value
        view.requestLayout()
        view.invalidate()
    }
}

private val orbitMobileMetadata = WeakHashMap<WebView, androidx.webkit.UserAgentMetadata>()

private fun applyOrbitDesktopMode(view: WebView, desktop: Boolean) {
    val settings = view.settings
    val supportsHints = WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)
    val mobileMetadata = if (supportsHints) orbitMobileMetadata.getOrPut(view) {
        WebSettingsCompat.getUserAgentMetadata(settings)
    } else null
    settings.userAgentString = if (desktop)
        BrowserAppearancePolicy.desktopUserAgent(WebSettings.getDefaultUserAgent(view.context)) else null
    if (mobileMetadata != null) {
        val metadata = if (desktop) androidx.webkit.UserAgentMetadata.Builder(mobileMetadata)
            .setPlatform("Linux").setPlatformVersion("").setArchitecture("x86")
            .setModel("").setMobile(false).setBitness(64).setWow64(false).build() else mobileMetadata
        WebSettingsCompat.setUserAgentMetadata(settings, metadata)
    }
    // Mobile pages also need their viewport meta tag honored. Overview is not desktop-only.
    settings.useWideViewPort = true
    settings.loadWithOverviewMode = true
    view.setInitialScale(0)
}

@Composable
private fun BrowserSettingsDialog(
    accountModel: GoogleAccountViewModel,
    onPlayAccountMedia: (com.example.model.VideoItem) -> Unit,
    appTextSizeSp: Int,
    onAppTextSizeChange: (Int) -> Unit,
    darkMode: Boolean,
    adBlock: Boolean,
    pageSkipper: Boolean,
    youtubeAdSkip: Boolean,
    videoHandoff: Boolean,
    onVideoHandoffChange: (Boolean) -> Unit,
    autoPlayInPlayer: Boolean,
    onAutoPlayInPlayerChange: (Boolean) -> Unit,
    desktopSite: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    onAdBlockChange: (Boolean) -> Unit,
    onPageSkipperChange: (Boolean) -> Unit,
    onYoutubeAdSkipChange: (Boolean) -> Unit,
    onDesktopSiteChange: (Boolean) -> Unit,
    onUpdateFilterLists: () -> Unit,
    textZoom: Int,
    popupBlock: Boolean,
    onTextZoomChange: (Int) -> Unit,
    onPopupBlockChange: (Boolean) -> Unit,
    onClearBrowsingData: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalHiPalette.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(modifier = Modifier.fillMaxSize().background(palette.background).safeDrawingPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = palette.textPrimary)
                }
                Text("Browser settings", color = palette.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GoogleAccountCard(onPlayDriveMedia = { video -> onDismiss(); onPlayAccountMedia(video) }, model = accountModel)
                BrowserSettingsGroup("Appearance") {
                    BrowserSettingsSwitchRow("Dark theme", "Dark browser controls and web pages where supported. Open pages reload; unsent page input may be lost.", darkMode, onDarkModeChange)
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text("Webpage text · $textZoom%", color = palette.textPrimary, fontWeight = FontWeight.Medium)
                        Text("Applies to all tabs. Some fixed-layout sites limit text resizing.", color = palette.textSecondary)
                        Slider(value = textZoom.toFloat(), valueRange = 80f..200f, steps = 11,
                            onValueChange = { onTextZoomChange((it / 10f).roundToInt() * 10) },
                            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Webpage text size" })
                        TextButton(onClick = { onTextZoomChange(100) }, enabled = textZoom != 100) { Text("Reset webpage text to 100%") }
                        Text("App text · ${appTextSizeSp}sp", color = palette.textPrimary, fontWeight = FontWeight.Medium)
                        Text("Changes Hi Player controls and menus, including this screen. System font size is respected too.", color = palette.textSecondary)
                        Slider(value = appTextSizeSp.coerceIn(12, 24).toFloat(), valueRange = 12f..24f, steps = 11,
                            onValueChange = { onAppTextSizeChange(it.roundToInt()) },
                            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "App text size" })
                        TextButton(onClick = { onAppTextSizeChange(14) }, enabled = appTextSizeSp != 14) { Text("Reset app text to 14sp") }
                    }
                }
                BrowserSettingsGroup("Privacy and content") {
                    BrowserSettingsSwitchRow("Ad and tracker blocking", "Block common ad and tracker requests", adBlock, onAdBlockChange)
                    BrowserSettingsSwitchRow("Block pop-ups", "Links open in this tab; other new windows are blocked", popupBlock, onPopupBlockChange)
                    BrowserSettingsSwitchRow("YouTube ad skip", "Mute, fast-forward and skip YouTube ads", youtubeAdSkip, onYoutubeAdSkipChange)
                    BrowserSettingsSwitchRow("Skip page transitions", "Try to continue through supported pages", pageSkipper, onPageSkipperChange)
                }
                BrowserSettingsGroup("Videos") {
                    BrowserSettingsSwitchRow(
                        "Detect website videos",
                        "Find the video on a page for Play in Hi Player in the menu",
                        videoHandoff,
                        onVideoHandoffChange,
                    )
                    if (videoHandoff) {
                        BrowserSettingsSwitchRow(
                            "Auto-play in Hi Player",
                            "When you start a video on a page, it opens in Hi Player. Ads and short clips stay on the page",
                            autoPlayInPlayer,
                            onAutoPlayInPlayerChange,
                        )
                    }
                }
                BrowserSettingsGroup("This tab") {
                    BrowserSettingsSwitchRow("Desktop site", "Ask sites for their desktop layout", desktopSite, onDesktopSiteChange)
                }
                BrowserSettingsGroup("Data") {
                    BrowserSettingsActionRow("Update ad-block filter lists", onUpdateFilterLists)
                    BrowserSettingsActionRow("Clear browsing data", onClearBrowsingData)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun BrowserSettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    val palette = LocalHiPalette.current
    Text(
        title.uppercase(),
        color = palette.textTertiary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 6.dp, top = 6.dp),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)).hiContainerBorder(RoundedCornerShape(20.dp))
            .background(palette.surface)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        content = content,
    )
}

@Composable
private fun BrowserSettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val palette = LocalHiPalette.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(description, color = palette.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = palette.primary,
                uncheckedThumbColor = palette.textSecondary,
                uncheckedTrackColor = palette.surfaceElevated,
                uncheckedBorderColor = palette.textTertiary,
            ),
        )
    }
}

@Composable
private fun BrowserSettingsActionRow(title: String, onClick: () -> Unit) {
    val palette = LocalHiPalette.current
    Text(
        title,
        color = palette.primary,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
    )
}

private enum class BrowserSearchProvider(
    val displayName: String,
    private val searchBaseUrl: String,
) {
    GOOGLE("Google", "https://www.google.com/search?q="),
    BING("Bing", "https://www.bing.com/search?q="),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q="),
    ;

    fun searchUrl(query: String): String =
        searchBaseUrl + URLEncoder.encode(query, StandardCharsets.UTF_8.name())
}

private fun normalizeBrowserInput(raw: String, provider: BrowserSearchProvider): String? {
    val value = raw.trim()
    if (value.isBlank()) return null
    if (value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)) return value
    if (value.contains('.') && !value.contains(' ')) return "https://$value"
    return provider.searchUrl(value)
}

private fun formatBytes(value: Long): String = when {
    value <= 0L -> "0 B"
    value < 1_024L -> "$value B"
    value < 1_024L * 1_024L -> "%.1f KB".format(value / 1_024.0)
    value < 1_024L * 1_024L * 1_024L -> "%.1f MB".format(value / (1_024.0 * 1_024.0))
    else -> "%.2f GB".format(value / (1_024.0 * 1_024.0 * 1_024.0))
}

/** Neutral skeleton until real main-frame content paints; never a guessed site preview. */
@Composable
private fun HiBrowserLoadingPage(
    url: String,
    slow: Boolean,
    offline: Boolean,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val palette = LocalHiPalette.current
    val host = hiBrowserDisplayAddress(url).ifBlank { "page" }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .padding(bottom = bottomInset),
    ) {
        // Real loading status plus generic content-shaped placeholders. No blank
        // full-screen progress bar and no invented website content/percentages.
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("Loading $host…", color = palette.textPrimary, fontSize = 16.sp,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = onStop) { Text("Stop loading") }
        }
        if (slow || offline) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (offline) "You're offline. Check your connection and retry."
                    else "This page is taking longer than usual.",
                    color = palette.textPrimary,
                )
                TextButton(onClick = onRetry) { Text("Retry") }
            }
        }
        BrowserPageSkeleton(label = "Page content is loading", modifier = Modifier.weight(1f))
    }
}

/** Clear recovery for network, HTTP and certificate failures; no raw WebView errors. */
@Composable
private fun HiBrowserErrorPage(
    url: String,
    offline: Boolean,
    onRetry: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val palette = LocalHiPalette.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .padding(start = 32.dp, end = 32.dp, bottom = bottomInset + 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            if (offline) Icons.Default.WifiOff else Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = palette.textSecondary,
            modifier = Modifier.size(52.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            if (offline) "No internet connection" else "Can't reach this page",
            color = palette.textPrimary,
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (offline) "Check Wi-Fi or mobile data. The page reloads by itself when you're back online."
            else "This page is unavailable or couldn't be opened securely. Try again, check the address, or return Home.",
            color = palette.textSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onRetry) { Text("Retry") }
            TextButton(onClick = onHome) { Text("Home") }
        }
    }
}

/** Return surface for a browser-owned website session; never claims login succeeded. */
@Composable
private fun HiBrowserWebsiteSessionPage(
    url: String,
    onOpen: () -> Unit,
    onUseEmbedded: () -> Unit,
    bottomInset: androidx.compose.ui.unit.Dp,
) {
    val palette = LocalHiPalette.current
    Column(
        Modifier.fillMaxSize().background(palette.background)
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 40.dp, bottom = bottomInset + 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(Icons.Default.OpenInNew, null, Modifier.size(40.dp), tint = palette.textPrimary)
        Text(hiBrowserDisplayAddress(url), color = palette.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Continue this website in your browser", color = palette.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text("Complete Google sign-in and keep browsing there. The browser manages the website account and saved session; Hi Player cannot see which account is signed in.", color = palette.textPrimary)
        Button(onClick = onOpen, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Continue website") }
        Text("Hi Browser's filters and player controls do not apply inside the device browser.", color = palette.textPrimary)
        Text("Use Hi Browser opens a separate page that may be signed out. It also resets this site's browser preference.", color = palette.textPrimary)
        OutlinedButton(onClick = onUseEmbedded, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Use Hi Browser instead") }
    }
}
