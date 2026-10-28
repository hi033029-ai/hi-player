package com.example.ui.screens

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.webkit.ScriptHandler
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

private data class BrowserQuickLink(
    val label: String,
    val url: String?,
    val icon: ImageVector,
    val tint: Color,
)

private object HiBrowserDocumentScripts {
    private val handlers = WeakHashMap<WebView, MutableList<ScriptHandler>>()

    fun isSupported(): Boolean = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)

    fun apply(webView: WebView, context: Context, store: OrbitBrowserStore) {
        remove(webView)
        if (!isSupported()) return
        val registered = mutableListOf<ScriptHandler>()
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

private val orbitBrowserTabsSaver = listSaver<List<OrbitBrowserTab>, Any>(
    save = { tabs ->
        listOf("tabs_v3") + tabs.flatMap { tab ->
            listOf(tab.id, tab.privateTab, if (tab.privateTab) "" else tab.url,
                if (tab.privateTab) "Private tab" else tab.title, tab.desktop,
                tab.openerTabId ?: -1, if (tab.privateTab) android.os.Bundle() else tab.historyState ?: android.os.Bundle())
        }
    },
    restore = { saved ->
        val current = saved.firstOrNull() == "tabs_v3"
        (if (current) saved.drop(1) else saved).chunked(if (current) 7 else 5).mapNotNull { values ->
            val id = values.getOrNull(0) as? Int ?: return@mapNotNull null
            OrbitBrowserTab(id, values.getOrNull(1) as? Boolean ?: false,
                url = values.getOrNull(2) as? String ?: "", title = values.getOrNull(3) as? String ?: "New tab",
                desktop = values.getOrNull(4) as? Boolean ?: false,
                openerTabId = (values.getOrNull(5) as? Int)?.takeIf { it >= 0 },
                historyState = (values.getOrNull(6) as? android.os.Bundle)?.takeUnless { it.isEmpty })
        }
    },
)

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
    onPlayDirectMedia: (String) -> Unit,
    modifier: Modifier = Modifier,
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
    val palette = LocalHiPalette.current
    val store = remember(context) { OrbitBrowserStore(context) }
    val downloads by BrowserDownloads.downloads.collectAsState()
    var pendingDownload by remember { mutableStateOf<BrowserDownloadRequest?>(null) }
    var darkModeEnabled by remember { mutableStateOf(store.darkMode) }
    var adBlockEnabled by remember { mutableStateOf(store.adBlock) }
    var pageSkipperEnabled by remember { mutableStateOf(store.pageSkipper) }
    var youtubeAdSkipEnabled by remember { mutableStateOf(store.youtubeAdSkip) }
    var videoHandoffEnabled by remember { mutableStateOf(store.videoHandoff) }
    // Bumped to rebuild every tab's WebView (pages pick light/dark at creation).
    var webViewGeneration by remember { mutableIntStateOf(0) }
    // While a page from the start page is loading, the start page stays on screen
    // until the new page paints, instead of flashing an empty (dark) view.
    var pendingFirstPaint by remember { mutableStateOf(false) }
    // Reserve room for the floating address bar only on pages that cannot scroll.
    var reserveBarSpace by remember { mutableStateOf(false) }
    var barHeightPx by remember { mutableIntStateOf(0) }
    val mediaSniffer = remember { HiBrowserMediaSniffer() }
    val adBlockFlag = remember { HiBrowserFlag(store.adBlock) }
    val fullscreen = remember { HiBrowserFullscreen() }
    val latestOnPlayBrowserVideo by rememberUpdatedState(onPlayBrowserVideo)
    var tabs by rememberSaveable(stateSaver = orbitBrowserTabsSaver) { mutableStateOf(listOf(OrbitBrowserTab(id = 1, privateTab = false))) }
    val latestTabs by rememberUpdatedState(tabs)
    val latestOnPlayDirectMedia by rememberUpdatedState(onPlayDirectMedia)
    var activeTabId by rememberSaveable { mutableIntStateOf(1) }
    var nextTabId by rememberSaveable { mutableIntStateOf(2) }
    var recentTabIds by rememberSaveable { mutableStateOf(listOf(1)) }
    val activeTab = tabs.firstOrNull { it.id == activeTabId }
    val webView = activeTab?.webView
    var address by remember { mutableStateOf("") }
    var currentUrl by remember { mutableStateOf("") }
    var showStartPage by remember { mutableStateOf(true) }
    var searchProvider by rememberSaveable { mutableStateOf(BrowserSearchProvider.DUCKDUCKGO) }
    var pageTitle by remember { mutableStateOf("Hi Browser") }
    var loadingProgress by remember { mutableIntStateOf(0) }
    var rendererGone by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showBrowserSettings by remember { mutableStateOf(false) }
    var showTabsDialog by remember { mutableStateOf(false) }
    var showDownloadsDialog by remember { mutableStateOf(false) }
    var entryList by remember { mutableStateOf<OrbitEntryList?>(null) }
    var entriesVersion by remember { mutableIntStateOf(0) }
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
    // "Browser mode" intro plays every time the browser is entered.
    var showModeIntro by remember { mutableStateOf(true) }

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
        pendingFirstPaint = tab.webView == null && tab.url.isNotBlank()
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

    fun loadAddress(raw: String) {
        val destination = normalizeBrowserInput(raw, searchProvider) ?: return
        val stream = StreamUrlClassifier.classify(destination)
        if (!isOrbitYoutube(destination) && stream.isPlayableInHiPlayer && !stream.requiresWebResolution) {
            onPlayDirectMedia(destination)
            return
        }
        address = destination
        currentUrl = destination
        pageTitle = destination
        if (showStartPage || activeTab?.webView == null) pendingFirstPaint = true
        showStartPage = false
        rendererGone = false
        val tab = activeTab ?: return
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
            view.settings.userAgentString = if (desktop) DESKTOP_USER_AGENT else null
            view.settings.useWideViewPort = desktop
            view.settings.loadWithOverviewMode = desktop
            view.reload()
        }
    }

    fun setDarkMode(enabled: Boolean) {
        store.darkMode = enabled
        darkModeEnabled = enabled
        // Sites choose light or dark colours from the WebView's theme when it is
        // created, so rebuild the tabs' WebViews; each reloads its page.
        tabs.forEach { tab -> tab.webView?.let(::disposeOrbitWebView) }
        tabs = tabs.map { it.copy(webView = null) }
        if (!showStartPage) pendingFirstPaint = true
        webViewGeneration++
    }

    fun setVideoHandoffEnabled(enabled: Boolean) {
        store.videoHandoff = enabled
        videoHandoffEnabled = enabled
    }

    /** Opens a web video in Hi Player with the page's referrer, user agent and cookies. */
    fun playBrowserVideo(tabId: Int, url: String) {
        val tab = tabs.firstOrNull { it.id == tabId }
        val pageUrl = mediaSniffer.pageUrl(tabId).ifBlank { tab?.url.orEmpty() }
        val userAgent = if (tab?.desktop == true) DESKTOP_USER_AGENT else runCatching { WebSettings.getDefaultUserAgent(context) }.getOrNull()
        registerBrowserStreamHeaders(url, pageUrl, userAgent, mediaSniffer.mediaHosts(tabId))
        val title = mediaSniffer.title(tabId).ifBlank { tab?.title.orEmpty() }.takeIf { it.isNotBlank() && !it.startsWith("http") }
        tab?.webView?.evaluateJavascript(HI_BROWSER_PAUSE_MEDIA_SCRIPT, null)
        val player = latestOnPlayBrowserVideo
        if (player != null) player(url, hiBrowserStreamMimeType(url), title) else latestOnPlayDirectMedia(url)
    }

    fun setAdBlockEnabled(enabled: Boolean) {
        store.adBlock = enabled
        adBlockEnabled = enabled
        adBlockFlag.enabled = enabled
        webView?.reload()
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

    fun refreshDocumentScripts() {
        tabs.forEach { tab -> tab.webView?.let { HiBrowserDocumentScripts.apply(it, context, store) } }
        webView?.reload()
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
    fun openUrlFromPage(url: String, source: WebView, privateTab: Boolean) {
        if (OrbitBrowserFilterEngine.shouldBlock(Uri.parse(url), store.adBlock)) {
            showBrowserToast("Pop-up blocked")
            return
        }
        val sourceId = tabs.firstOrNull { it.webView === source }?.id ?: return
        openNewTab(privateTab, url, openerId = sourceId)
    }

    fun setTextZoom(percent: Int) {
        val value = percent.coerceIn(80, 200)
        store.textZoom = value
        textZoomPercent = value
        tabs.forEach { tab -> tab.webView?.settings?.textZoom = value }
    }

    fun setPopupBlockEnabled(enabled: Boolean) {
        store.popupBlock = enabled
        popupBlockEnabled = enabled
        tabs.forEach { tab -> tab.webView?.settings?.javaScriptCanOpenWindowsAutomatically = !enabled }
    }

    fun clearBrowsingData(history: Boolean, cookies: Boolean, cache: Boolean) {
        HiBrowserTools.clearBrowsingData(context, tabs.mapNotNull { it.webView }, history, cookies, cache)
        if (history) {
            store.clearHistory()
            HiSiteIcons.clearSaved(context)
            entriesVersion++
        }
        showBrowserToast("Browsing data cleared")
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

    LaunchedEffect(externalUrl) {
        val url = externalUrl ?: return@LaunchedEffect
        onExternalUrlConsumed()
        if (tabs.size >= 12) loadAddress(url) else openNewTab(privateTab = false, initialUrl = url)
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

    DisposableEffect(Unit) {
        onDispose { latestTabs.mapNotNull { it.webView }.forEach(::disposeOrbitWebView) }
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
    LaunchedEffect(pendingFirstPaint) {
        if (pendingFirstPaint) {
            delay(9_000L)
            pendingFirstPaint = false
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

    Box(
        modifier = modifier.fillMaxSize().background(palette.background),
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
        } else if (!showStartPage && activeTab != null) {
            key(activeTab.id, webViewGeneration) {
                AndroidView(
                    factory = { viewContext ->
                        val tabId = activeTab.id
                        // The WebView's theme decides prefers-color-scheme for every site:
                        // a light theme unless the browser's dark theme is switched on.
                        val themedContext = ContextThemeWrapper(
                            viewContext,
                            if (store.darkMode) android.R.style.Theme_DeviceDefault_NoActionBar
                            else android.R.style.Theme_DeviceDefault_Light_NoActionBar,
                        )
                        val view = activeTab.webView ?: WebView(themedContext).apply {
                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            setBackgroundColor(if (store.darkMode) android.graphics.Color.rgb(18, 18, 18) else android.graphics.Color.WHITE)
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = false
                                cacheMode = if (activeTab.privateTab) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
                                allowFileAccess = false
                                allowContentAccess = false
                                setSupportMultipleWindows(true)
                                javaScriptCanOpenWindowsAutomatically = !store.popupBlock
                                textZoom = store.textZoom
                                mediaPlaybackRequiresUserGesture = true
                                builtInZoomControls = true
                                displayZoomControls = false
                                useWideViewPort = activeTab.desktop
                                loadWithOverviewMode = activeTab.desktop
                                userAgentString = if (activeTab.desktop) DESKTOP_USER_AGENT else null
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
                            }
                            applyOrbitDarkMode(this, store.darkMode)
                            HiBrowserDocumentScripts.apply(this, viewContext, store)
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
                            addJavascriptInterface(
                                HiBrowserVideoBridge(
                                    onReport = { src -> mediaSniffer.reportVideo(tabId, src) },
                                    onStarted = { src, userGesture, muted ->
                                        // Runs on WebView's JavaBridge thread: decide from thread-safe
                                        // snapshots, then hand off on the main thread.
                                        val pageUrl = mediaSniffer.pageUrl(tabId)
                                        val candidate = when {
                                            !store.videoHandoff || mediaSniffer.activeTabId != tabId -> null
                                            isOrbitYoutube(pageUrl) || (muted && !userGesture) -> null
                                            else -> (src.takeIf { (it.startsWith("http://") || it.startsWith("https://")) && !isOrbitYoutube(it) }
                                                ?: mediaSniffer.bestManifest(tabId))
                                        }
                                        val playable = candidate != null && StreamUrlClassifier
                                            .classifyResolvedMediaUrl(candidate, hiBrowserStreamMimeType(candidate), null, trustAsDirectSource = true)
                                            .isPlayableInHiPlayer
                                        if (candidate != null && playable && mediaSniffer.claimHandoff(tabId)) {
                                            browserWebView.post { if (activeTabId == tabId) playBrowserVideo(tabId, candidate) }
                                            true
                                        } else {
                                            false
                                        }
                                    },
                                ),
                                "HiBrowserNative",
                            )
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
                                        if (!tappedLink) {
                                            Toast.makeText(viewContext, "Pop-up blocked", Toast.LENGTH_SHORT).show()
                                            return false
                                        }
                                        val href = hit.extra?.takeIf {
                                            hit.type == WebView.HitTestResult.SRC_ANCHOR_TYPE &&
                                                (it.startsWith("http://") || it.startsWith("https://"))
                                        }
                                        if (href != null) {
                                            if (!OrbitBrowserFilterEngine.shouldBlock(Uri.parse(href), store.adBlock)) view.loadUrl(href)
                                            return false
                                        }
                                        val linkTransport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                                        linkTransport.webView = HiBrowserPopupCatcher.create(viewContext) { linkUrl ->
                                            if (!OrbitBrowserFilterEngine.shouldBlock(Uri.parse(linkUrl), store.adBlock)) view.loadUrl(linkUrl)
                                        }
                                        resultMsg.sendToTarget()
                                        return true
                                    }
                                    val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                                    transport.webView = HiBrowserPopupCatcher.create(viewContext) { popupUrl ->
                                        openUrlFromPage(popupUrl, view, activeTab.privateTab)
                                    }
                                    resultMsg.sendToTarget()
                                    return true
                                }
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    val uri = request.url
                                    if (uri.scheme == "http" || uri.scheme == "https") {
                                        if (request.isForMainFrame && !isOrbitYoutube(uri.toString())) {
                                            val stream = StreamUrlClassifier.classify(uri.toString())
                                            if (stream.isPlayableInHiPlayer && !stream.requiresWebResolution) {
                                                latestOnPlayDirectMedia(uri.toString())
                                                return true
                                            }
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
                                    updateTab(tabId) { it.copy(url = url, historyState = captureOrbitHistory(view)) }
                                    if (activeTabId == tabId) {
                                        currentUrl = url
                                        address = url
                                        loadingProgress = 1
                                        browserBarHidden = false
                                    }
                                }
                                override fun onPageCommitVisible(view: WebView, url: String) {
                                    if (activeTabId == tabId) pendingFirstPaint = false
                                    injectCosmeticCss(view, url)
                                }
                                override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                                    if (tabs.firstOrNull { it.id == tabId }?.url.isNullOrBlank()) return
                                    if (!url.startsWith("http://") && !url.startsWith("https://")) return
                                    // Single-page sites change address without loading a new page.
                                    mediaSniffer.setPage(tabId, url)
                                    updateTab(tabId) { it.copy(url = url, historyState = captureOrbitHistory(view)) }
                                    if (activeTabId == tabId) {
                                        currentUrl = url
                                        address = url
                                    }
                                }
                                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
                                    if (request.isForMainFrame && activeTabId == tabId) pendingFirstPaint = false
                                }
                                override fun onPageFinished(view: WebView, url: String) {
                                    updateTab(tabId) { it.copy(historyState = captureOrbitHistory(view)) }
                                    if (tabs.firstOrNull { it.id == tabId }?.url.isNullOrBlank()) return
                                    updateTab(tabId) { it.copy(url = url, title = view.title ?: it.title) }
                                    view.title?.takeIf(String::isNotBlank)?.let { mediaSniffer.setTitle(tabId, it) }
                                    if (activeTabId == tabId) {
                                        currentUrl = url
                                        address = url
                                        pageTitle = view.title?.takeIf(String::isNotBlank) ?: url
                                        loadingProgress = 100
                                        pendingFirstPaint = false
                                    }
                                    if (!activeTab.privateTab) store.addHistory(OrbitBrowserEntry(view.title ?: url, url))
                                    injectCosmeticCss(view, url)
                                    if (!HiBrowserDocumentScripts.isSupported()) {
                                        if (store.pageSkipper) injectOrbitAsset(view, viewContext, "orbit_page_skipper.js")
                                        if (store.youtubeAdSkip && isOrbitYoutube(url)) injectOrbitAsset(view, viewContext, "orbit_youtube.js")
                                        if (!isOrbitYoutube(url)) view.evaluateJavascript(HI_BROWSER_VIDEO_HOOK_SCRIPT, null)
                                    }
                                }
                                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                    val pageHost = if (request.isForMainFrame) null else mediaSniffer.pageHost(tabId)
                                    val filtered = OrbitBrowserFilterEngine.shouldBlock(request.url, adBlockFlag.enabled, pageHost)
                                    if (filtered) return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                                    mediaSniffer.observeRequest(tabId, request.url)
                                    return null
                                }
                                override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                                    disposeOrbitWebView(view)
                                    updateTab(tabId) { it.copy(webView = null) }
                                    if (activeTabId == tabId) rendererGone = true
                                    return true
                                }
                            }
                            setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, size ->
                                val name = DownloadFileResolver.resolve(URLUtil.guessFileName(url, contentDisposition, mimeType), url, mimeType, contentDisposition).fileName
                                pendingDownload = BrowserDownloadRequest(url, userAgent.orEmpty(), contentDisposition.orEmpty(), mimeType, name, size.coerceAtLeast(0), this.url.orEmpty())
                            })
                            val restored = activeTab.historyState?.let { state -> runCatching { restoreState(state) }.getOrNull() }
                            if (activeTab.url.isNotBlank() && (restored == null || restored.currentItem?.url != activeTab.url)) loadUrl(activeTab.url)
                        }
                        updateTab(tabId) { it.copy(webView = view) }
                        view
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        }
        if ((showStartPage || pendingFirstPaint) && !isInPictureInPicture && !rendererGone) {
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
                HiBrowserToolbar(
                    address = address,
                    onAddressChange = { address = it },
                    onSubmit = { loadAddress(it) },
                    isPrivate = activeTab?.privateTab == true,
                    isSecure = onPage && currentUrl.startsWith("https://"),
                    isLoading = onPage && loadingProgress in 1..99,
                    showReload = onPage,
                    tabCount = tabs.size,
                    providers = BrowserSearchProvider.entries.map { it.displayName },
                    selectedProvider = searchProvider.displayName,
                    onProviderSelect = { index -> searchProvider = BrowserSearchProvider.entries[index] },
                    onHome = { showBrowserHome() },
                    onVoiceSearch = { startVoiceSearch() },
                    onReloadOrStop = {
                        if (loadingProgress in 1..99) {
                            webView?.stopLoading()
                            loadingProgress = 100
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
            darkMode = darkModeEnabled,
            adBlock = adBlockEnabled,
            pageSkipper = pageSkipperEnabled,
            youtubeAdSkip = youtubeAdSkipEnabled,
            videoHandoff = videoHandoffEnabled,
            onVideoHandoffChange = { setVideoHandoffEnabled(it) },
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

    if (menuExpanded) {
        val onPage = !showStartPage
        val bookmarked = onPage && store.isBookmarked(currentUrl)
        HiBrowserMenuSheet(
            tiles = listOf(
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

    if (showModeIntro && !isInPictureInPicture) {
        HiBrowserModeIntro(onFinished = { showModeIntro = false })
    }
}

private fun disposeOrbitWebView(webView: WebView) {
    HiBrowserDocumentScripts.remove(webView)
    runCatching {
        webView.removeJavascriptInterface("HiBrowserNative")
        webView.stopLoading()
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
    }
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
        if (!isOrbitYoutube(src)) tab(id).reportedVideo = src
    }

    fun observeRequest(id: Int, uri: Uri) {
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

private class HiBrowserVideoBridge(
    private val onReport: (String) -> Unit,
    private val onStarted: (src: String, userGesture: Boolean, muted: Boolean) -> Boolean,
) {
    /** The page has a video with this direct source (used by "Play in Hi Player"). */
    @JavascriptInterface
    fun reportVideo(rawUrl: String?) {
        val value = rawUrl?.trim()?.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: return
        runCatching { onReport(value) }
    }

    /** A video started playing; returns true when Hi Player takes it over. */
    @JavascriptInterface
    fun videoStarted(rawUrl: String?, userGesture: Boolean, muted: Boolean): Boolean =
        runCatching { onStarted(rawUrl?.trim().orEmpty(), userGesture, muted) }.getOrDefault(false)
}

private const val HI_BROWSER_PAUSE_MEDIA_SCRIPT =
    "(function(){try{document.querySelectorAll('video,audio').forEach(function(m){try{m.pause();}catch(e){}});}catch(e){}})()"

/**
 * Opens videos in Hi Player once they really start (not muted previews, tiny
 * players or short clips). Runs in every frame; YouTube is left alone.
 */
private const val HI_BROWSER_VIDEO_HOOK_SCRIPT = """
(function(){
  var host=(window.location.hostname||'').toLowerCase();
  if(host==='youtu.be'||/(^|\.)youtube(-nocookie)?\.com$/.test(host)) return;
  if(window.__hiVideoHookV2) return;
  window.__hiVideoHookV2=true;
  var lastGesture=0;
  function markGesture(e){ if(e.isTrusted) lastGesture=Date.now(); }
  ['pointerdown','touchend','click','keydown'].forEach(function(n){ document.addEventListener(n,markGesture,true); });
  function sourceFor(v){
    var s=v.currentSrc||v.src||'';
    if(!s){ var el=v.querySelector('source[src]'); if(el) s=el.src||''; }
    return s;
  }
  function bridge(){ return window.HiBrowserNative; }
  function report(v){
    var s=sourceFor(v), n=bridge();
    if(n&&/^https?:\/\//i.test(s)){ try{ n.reportVideo(s); }catch(e){} }
  }
  function onPlaying(ev){
    var v=ev.target;
    if(!v||v.tagName!=='VIDEO') return;
    var n=bridge();
    if(!n) return;
    report(v);
    var s=sourceFor(v);
    if(v.__hiHandoffSrc===s) return;
    var r=v.getBoundingClientRect();
    if(r.width<160||r.height<90) return;
    var gesture=(Date.now()-lastGesture)<15000;
    if(v.muted&&!gesture) return;
    var d=v.duration;
    if(isFinite(d)&&d>0&&d<20) return;
    var ok=false;
    try{ ok=n.videoStarted(/^https?:\/\//i.test(s)?s:'',gesture,!!v.muted); }catch(e){}
    if(ok===true||ok==='true'){
      v.__hiHandoffSrc=s;
      try{ v.pause(); }catch(e){}
      try{ if(document.fullscreenElement&&document.exitFullscreen) document.exitFullscreen(); }catch(e){}
    }
  }
  document.addEventListener('playing',onPlaying,true);
  document.addEventListener('loadedmetadata',function(ev){ var v=ev.target; if(v&&v.tagName==='VIDEO') report(v); },true);
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

private const val DESKTOP_USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

@Composable
private fun BrowserSettingsDialog(
    darkMode: Boolean,
    adBlock: Boolean,
    pageSkipper: Boolean,
    youtubeAdSkip: Boolean,
    videoHandoff: Boolean,
    onVideoHandoffChange: (Boolean) -> Unit,
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
        Column(modifier = Modifier.fillMaxSize().background(palette.background)) {
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
                BrowserSettingsGroup("Appearance") {
                    BrowserSettingsSwitchRow("Dark theme", "Show web pages with dark colors", darkMode, onDarkModeChange)
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Text size", color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text("How big text looks on web pages", color = palette.textSecondary, fontSize = 12.sp)
                        }
                        IconButton(onClick = { onTextZoomChange(textZoom - 10) }, enabled = textZoom > 80) {
                            Icon(Icons.Default.Remove, contentDescription = "Smaller text", tint = palette.textPrimary)
                        }
                        Text(
                            "$textZoom%",
                            color = palette.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(56.dp),
                        )
                        IconButton(onClick = { onTextZoomChange(textZoom + 10) }, enabled = textZoom < 200) {
                            Icon(Icons.Default.Add, contentDescription = "Bigger text", tint = palette.textPrimary)
                        }
                    }
                    if (textZoom != 100) {
                        TextButton(onClick = { onTextZoomChange(100) }) { Text("Reset text size to 100%") }
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
                        "Play videos in Hi Player",
                        "Videos you start on websites (not YouTube) open in Hi Player",
                        videoHandoff,
                        onVideoHandoffChange,
                    )
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
            .clip(RoundedCornerShape(20.dp))
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
