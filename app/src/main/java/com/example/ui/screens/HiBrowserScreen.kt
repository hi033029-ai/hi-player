package com.example.ui.screens

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
import android.app.DownloadManager
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

private data class OrbitBrowserTab(
    val id: Int,
    val privateTab: Boolean,
    val webView: WebView? = null,
    val url: String = "",
    val title: String = "New tab",
    val desktop: Boolean = false,
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
                    webView, it, setOf("https://*.youtube.com", "https://youtube.com"),
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
        tabs.flatMap { tab ->
            listOf(
                tab.id,
                tab.privateTab,
                if (tab.privateTab) "" else tab.url,
                if (tab.privateTab) "Private tab" else tab.title,
                tab.desktop,
            )
        }
    },
    restore = { saved ->
        saved.chunked(5).mapNotNull { values ->
            val id = values.getOrNull(0) as? Int ?: return@mapNotNull null
            OrbitBrowserTab(
                id = id,
                privateTab = values.getOrNull(1) as? Boolean ?: false,
                url = values.getOrNull(2) as? String ?: "",
                title = values.getOrNull(3) as? String ?: "New tab",
                desktop = values.getOrNull(4) as? Boolean ?: false,
            )
        }
    },
)

@Composable
fun HiBrowserScreen(
    onBack: () -> Unit,
    onPlayDirectMedia: (String) -> Unit,
    modifier: Modifier = Modifier,
    isInPictureInPicture: Boolean = false,
    onEnterPictureInPicture: (() -> Boolean)? = null,
    externalUrl: String? = null,
    onExternalUrlConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val palette = LocalHiPalette.current
    val store = remember(context) { OrbitBrowserStore(context) }
    val downloads by BrowserDownloads.downloads.collectAsState()
    var darkModeEnabled by remember { mutableStateOf(store.darkMode) }
    var adBlockEnabled by remember { mutableStateOf(store.adBlock) }
    var pageSkipperEnabled by remember { mutableStateOf(store.pageSkipper) }
    var youtubeAdSkipEnabled by remember { mutableStateOf(store.youtubeAdSkip) }
    var tabs by rememberSaveable(stateSaver = orbitBrowserTabsSaver) { mutableStateOf(listOf(OrbitBrowserTab(id = 1, privateTab = false))) }
    val latestTabs by rememberUpdatedState(tabs)
    val latestOnPlayDirectMedia by rememberUpdatedState(onPlayDirectMedia)
    var activeTabId by rememberSaveable { mutableIntStateOf(1) }
    var nextTabId by rememberSaveable { mutableIntStateOf(2) }
    val activeTab = tabs.firstOrNull { it.id == activeTabId }
    val webView = activeTab?.webView
    var address by remember { mutableStateOf("") }
    var currentUrl by remember { mutableStateOf("") }
    var detectedVideoUrl by remember { mutableStateOf<String?>(null) }
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
    val downloadsActive = downloads.any { it.isActive }
    var textZoomPercent by remember { mutableIntStateOf(store.textZoom) }
    var popupBlockEnabled by remember { mutableStateOf(store.popupBlock) }
    var findBarVisible by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var findMatchLabel by remember { mutableStateOf("") }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var librarySection by remember { mutableStateOf<HiLibrarySection?>(null) }
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

    LaunchedEffect(activeTabId) {
        val restoredTab = tabs.firstOrNull { it.id == activeTabId }
        address = restoredTab?.url.orEmpty()
        currentUrl = restoredTab?.url.orEmpty()
        pageTitle = restoredTab?.title ?: "Hi Browser"
        showStartPage = restoredTab?.url.isNullOrBlank()
        findBarVisible = false
        findQuery = ""
        findMatchLabel = ""
    }

    fun updateTab(id: Int, transform: (OrbitBrowserTab) -> OrbitBrowserTab) {
        tabs = tabs.map { if (it.id == id) transform(it) else it }
    }

    fun openNewTab(privateTab: Boolean, initialUrl: String = "") {
        if (tabs.size >= 12) {
            Toast.makeText(context, "Close a tab before opening another (12-tab limit)", Toast.LENGTH_SHORT).show()
            return
        }
        val id = nextTabId++
        tabs = tabs + OrbitBrowserTab(id = id, privateTab = privateTab, url = initialUrl)
        activeTabId = id
        address = initialUrl
        currentUrl = initialUrl
        pageTitle = if (privateTab) "Private tab" else "New tab"
        loadingProgress = 0
        showStartPage = initialUrl.isBlank()
        rendererGone = false
    }

    fun activateTab(tab: OrbitBrowserTab) {
        activeTabId = tab.id
        address = tab.url
        currentUrl = tab.url
        pageTitle = tab.title
        showStartPage = address.isBlank()
        loadingProgress = 0
        rendererGone = false
    }

    fun closeTab(tabId: Int) {
        val closing = tabs.firstOrNull { it.id == tabId } ?: return
        val remaining = tabs.filterNot { it.id == tabId }
        closing.webView?.let(::disposeOrbitWebView)
        tabs = remaining
        if (remaining.isEmpty()) {
            openNewTab(privateTab = false)
        } else if (activeTabId == tabId) {
            activateTab(remaining.first())
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
        tabs.forEach { tab -> tab.webView?.let { applyOrbitDarkMode(it, enabled) } }
    }

    fun setAdBlockEnabled(enabled: Boolean) {
        store.adBlock = enabled
        adBlockEnabled = enabled
        webView?.reload()
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
        if (tabs.size >= 12) source.loadUrl(url) else openNewTab(privateTab, url)
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
            findBarVisible -> {
                findBarVisible = false
                findQuery = ""
                findMatchLabel = ""
            }
            webView?.canGoBack() == true -> webView?.goBack()
            !showStartPage -> showBrowserHome()
            tabs.size > 1 -> {
                closeTab(activeTabId)
            }
            else -> onBack()
        }
    }

    DisposableEffect(Unit) {
        onDispose { latestTabs.mapNotNull { it.webView }.forEach(::disposeOrbitWebView) }
    }

    LaunchedEffect(context) {
        OrbitBrowserFilterEngine.load(context)
        BrowserDownloads.restore(context)
    }
    LaunchedEffect(downloadsActive) {
        while (downloadsActive) {
            BrowserDownloads.refresh(context)
            delay(650L)
        }
    }
    LaunchedEffect(activeTabId, currentUrl, showStartPage, webView) {
        detectedVideoUrl = null
        if (showStartPage || webView == null || isOrbitYoutube(currentUrl)) return@LaunchedEffect
        while (true) {
            webView.evaluateJavascript(HI_BROWSER_VIDEO_DETECT_SCRIPT) { raw ->
                val found = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull()
                detectedVideoUrl = found?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
            }
            delay(1_500L)
        }
    }

    Column(
        modifier = modifier.fillMaxSize().background(palette.background),
    ) {
        if (!isInPictureInPicture && !showStartPage) {
            detectedVideoUrl?.takeIf { !isOrbitYoutube(currentUrl) }?.let { videoUrl ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = palette.surfaceElevated),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.PlayCircle, contentDescription = null, tint = palette.primary)
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Online video detected", color = palette.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("Use Hi Player's video controls", color = palette.textSecondary, fontSize = 10.sp)
                        }
                        TextButton(onClick = { onPlayDirectMedia(videoUrl) }) { Text("Play") }
                    }
                }
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
            )
        }

        if (rendererGone) {
            Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("The browser page closed to free memory", color = palette.textPrimary)
                Spacer(Modifier.height(10.dp))
                Button(onClick = { rendererGone = false; activeTab?.let { showStartPage = it.url.isBlank() } }) { Text("Reload browser") }
            }
        } else if (!showStartPage && activeTab != null) {
            key(activeTab.id) {
                AndroidView(
                    factory = { viewContext ->
                        val tabId = activeTab.id
                        val view = activeTab.webView ?: WebView(viewContext).apply {
                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
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
                            var lastUserTouchAt = 0L
                            browserWebView.setOnTouchListener { _, event ->
                                if (event.actionMasked == MotionEvent.ACTION_UP) lastUserTouchAt = SystemClock.uptimeMillis()
                                false
                            }
                            addJavascriptInterface(
                                HiBrowserVideoBridge { mediaUrl ->
                                    browserWebView.post {
                                        val pageUrl = browserWebView.url.orEmpty()
                                        val recentUserTouch = lastUserTouchAt > 0L &&
                                            SystemClock.uptimeMillis() - lastUserTouchAt <= 8_000L
                                        if (recentUserTouch && activeTabId == tabId && !isOrbitYoutube(pageUrl) && !isOrbitYoutube(mediaUrl)) {
                                            latestOnPlayDirectMedia(mediaUrl)
                                        }
                                    }
                                },
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
                                    if (activeTabId == tabId) pageTitle = value
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
                                    updateTab(tabId) { it.copy(url = url) }
                                    if (activeTabId == tabId) {
                                        currentUrl = url
                                        address = url
                                        loadingProgress = 1
                                    }
                                }
                                override fun onPageFinished(view: WebView, url: String) {
                                    if (tabs.firstOrNull { it.id == tabId }?.url.isNullOrBlank()) return
                                    updateTab(tabId) { it.copy(url = url, title = view.title ?: it.title) }
                                    if (activeTabId == tabId) {
                                        currentUrl = url
                                        address = url
                                        pageTitle = view.title?.takeIf(String::isNotBlank) ?: url
                                        loadingProgress = 100
                                    }
                                    if (!activeTab.privateTab) store.addHistory(OrbitBrowserEntry(view.title ?: url, url))
                                    val host = Uri.parse(url).host
                                    val css = OrbitBrowserFilterEngine.cosmeticCss(host, store.adBlock)
                                    if (css.isNotBlank()) view.evaluateJavascript(
                                        "(function(){var s=document.getElementById('__orbit_filter_css');if(!s){s=document.createElement('style');s.id='__orbit_filter_css';(document.head||document.documentElement).appendChild(s);}s.textContent=" + JSONObject.quote(css) + ";})();",
                                        null,
                                    )
                                    if (!HiBrowserDocumentScripts.isSupported()) {
                                        if (store.pageSkipper) injectOrbitAsset(view, viewContext, "orbit_page_skipper.js")
                                        if (store.youtubeAdSkip && isOrbitYoutube(url)) injectOrbitAsset(view, viewContext, "orbit_youtube.js")
                                        if (!isOrbitYoutube(url)) view.evaluateJavascript(HI_BROWSER_VIDEO_HOOK_SCRIPT, null)
                                    }
                                }
                                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                    val filtered = OrbitBrowserFilterEngine.shouldBlock(request.url, store.adBlock)
                                    return if (filtered) WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0))) else null
                                }
                                override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                                    disposeOrbitWebView(view)
                                    updateTab(tabId) { it.copy(webView = null) }
                                    if (activeTabId == tabId) rendererGone = true
                                    return true
                                }
                            }
                            setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                                BrowserDownloads.enqueue(viewContext, url, userAgent.orEmpty(), contentDisposition.orEmpty(), mimeType)
                            })
                            if (activeTab.url.isNotBlank()) loadUrl(activeTab.url)
                        }
                        updateTab(tabId) { it.copy(webView = view) }
                        view
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        }
        if (!isInPictureInPicture) {
            val onPage = !showStartPage
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
            if (onPage && loadingProgress in 1..99) {
                LinearProgressIndicator(
                    progress = { loadingProgress / 100f }, color = palette.primary, trackColor = palette.surfaceBorder,
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                )
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
                modifier = Modifier.windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars)),
            )
        }
    }

    if (showBrowserSettings) {
        BrowserSettingsDialog(
            darkMode = darkModeEnabled,
            adBlock = adBlockEnabled,
            pageSkipper = pageSkipperEnabled,
            youtubeAdSkip = youtubeAdSkipEnabled,
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
                val shownDownloads = downloads.filter { query.isBlank() || it.fileName.contains(query, ignoreCase = true) }
                if (shownDownloads.isEmpty()) {
                    Text(
                        if (query.isBlank()) "Your browser downloads will appear here." else "Nothing matches \"$query\"",
                        color = palette.textSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(12.dp),
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(palette.surface)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp),
                    ) {
                        shownDownloads.forEach { download ->
                            BrowserDownloadRow(
                                download = download,
                                onCancel = { BrowserDownloads.cancel(context, download.id) },
                                onRetry = { BrowserDownloads.retry(context, download.id) },
                                onRemove = { BrowserDownloads.removeFromList(context, download.id) },
                                onPlay = { BrowserDownloads.playInPlayer(context, download.id, onPlayDirectMedia) },
                            )
                        }
                        if (shownDownloads.none { it.isActive }) {
                            TextButton(onClick = { BrowserDownloads.clearCompleted(context) }) { Text("Clear completed") }
                        }
                    }
                }
            },
            onDismiss = { librarySection = null },
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
                        add(HiMenuAction("Play in Hi Player", Icons.Default.PlayCircle) { onPlayDirectMedia(currentUrl) })
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
                HiMenuAction("Back", Icons.Default.ArrowBack, enabled = webView?.canGoBack() == true) { webView?.goBack() },
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

private class HiBrowserVideoBridge(private val onPlay: (String) -> Unit) {
    @JavascriptInterface
    fun playVideo(rawUrl: String?) {
        val value = rawUrl?.trim()?.takeIf(String::isNotBlank) ?: return
        val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return
        if (uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank() && !isOrbitYoutube(value)) {
            onPlay(value)
        }
    }
}

private const val HI_BROWSER_VIDEO_DETECT_SCRIPT = """
(function(){
  function sourceFor(video){
    var source=video.currentSrc||video.src||Array.from(video.querySelectorAll('source[src]')).map(function(s){return s.src;}).find(Boolean)||'';
    if(/^https?:\/\//i.test(source)) return source;
    if(/^(blob:|data:)/i.test(source)) return window.location.href;
    return '';
  }
  var video=Array.from(document.querySelectorAll('video')).find(function(item){return !!sourceFor(item);});
  return video?sourceFor(video):'';
})()
"""

private const val HI_BROWSER_VIDEO_HOOK_SCRIPT = """
(function(){
  var host=(window.location.hostname||'').toLowerCase();
  if(host==='youtu.be'||/(^|\.)youtube(?:-nocookie)?\.com$/.test(host)) return;
  if(window.__hiBrowserNativeVideoHooked) return;
  window.__hiBrowserNativeVideoHooked=true;
  function sourceFor(video){
    var source=video.currentSrc||video.src||Array.from(video.querySelectorAll('source[src]')).map(function(s){return s.src;}).find(Boolean)||'';
    if(/^https?:\/\//i.test(source)) return source;
    return '';
  }
  function isYoutubeUrl(value){
    try{
      var sourceHost=new URL(value).hostname.toLowerCase();
      return sourceHost==='youtu.be'||sourceHost==='youtube.com'||sourceHost.endsWith('.youtube.com')||sourceHost==='youtube-nocookie.com'||sourceHost.endsWith('.youtube-nocookie.com');
    }catch(e){return false;}
  }
  function requestNative(video){
    if(video.__hiBrowserHandoffSent) return;
    var source=sourceFor(video);
    if(!source||isYoutubeUrl(source)||!window.HiBrowserNative||typeof window.HiBrowserNative.playVideo!=='function') return;
    video.__hiBrowserHandoffSent=true;
    try{video.pause();}catch(e){}
    try{window.HiBrowserNative.playVideo(source);}catch(e){}
  }
  function hook(video){
    if(video.__hiBrowserNativeHooked) return;
    video.__hiBrowserNativeHooked=true;
    video.addEventListener('play',function(){requestNative(video);},true);
    video.addEventListener('click',function(event){
      if(event.isTrusted&&video.paused) requestNative(video);
    },true);
  }
  function scan(){document.querySelectorAll('video').forEach(hook);}
  var observer=new MutationObserver(scan);
  function install(){scan();if(document.documentElement)observer.observe(document.documentElement,{childList:true,subtree:true});}
  if(document.documentElement) install(); else document.addEventListener('DOMContentLoaded',install,{once:true});
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

@Composable
private fun BrowserDownloadsDialog(
    downloads: List<BrowserDownload>,
    onCancel: (Long) -> Unit,
    onRetry: (Long) -> Unit,
    onRemove: (Long) -> Unit,
    onPlayDownload: (Long) -> Unit,
    onClearCompleted: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalHiPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Downloads")
                Text("Saved to Android/Downloads", color = palette.textSecondary, fontSize = 11.sp)
            }
        },
        text = {
            if (downloads.isEmpty()) {
                Text("Your browser downloads will appear here.", color = palette.textSecondary)
            } else {
                Column {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                        itemsIndexed(downloads, key = { _, download -> download.id }) { _, download ->
                            BrowserDownloadRow(
                                download = download,
                                onCancel = { onCancel(download.id) },
                                onRetry = { onRetry(download.id) },
                                onRemove = { onRemove(download.id) },
                                onPlay = { onPlayDownload(download.id) },
                            )
                            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.surfaceBorder))
                        }
                    }
                    if (downloads.none { it.isActive }) {
                        TextButton(onClick = onClearCompleted, modifier = Modifier.align(Alignment.End)) {
                            Text("Clear completed")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun BrowserDownloadRow(
    download: BrowserDownload,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onPlay: () -> Unit,
) {
    val palette = LocalHiPalette.current
    val fraction = if (download.totalBytes > 0L) {
        (download.downloadedBytes.toFloat() / download.totalBytes.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val isVideo = download.mimeType?.startsWith("video/") == true ||
        download.fileName.substringAfterLast('.', "").lowercase() in setOf("mp4", "m4v", "mkv", "webm", "mov", "avi", "3gp")
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = when (download.status) {
                    BrowserDownloadStatus.COMPLETED -> Icons.Default.CheckCircle
                    BrowserDownloadStatus.FAILED -> Icons.Default.ErrorOutline
                    else -> Icons.Default.Download
                },
                contentDescription = null,
                tint = when (download.status) {
                    BrowserDownloadStatus.FAILED -> Color(0xFFEF4444)
                    BrowserDownloadStatus.COMPLETED -> Color(0xFF22A06B)
                    else -> palette.primary
                },
            )
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(download.fileName, color = palette.textPrimary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = when (download.status) {
                        BrowserDownloadStatus.RUNNING -> "${formatBytes(download.downloadedBytes)} of ${formatBytes(download.totalBytes)} • ${formatBytes(download.speedBytesPerSecond)}/s"
                        BrowserDownloadStatus.PAUSED -> "Paused by Android • ${formatBytes(download.downloadedBytes)} downloaded"
                        BrowserDownloadStatus.COMPLETED -> "Saved to Downloads • ${formatBytes(download.downloadedBytes)}"
                        BrowserDownloadStatus.FAILED -> "Download failed"
                    },
                    color = palette.textSecondary,
                    fontSize = 10.sp,
                    maxLines = 2,
                )
            }
            if (download.status == BrowserDownloadStatus.RUNNING && download.totalBytes > 0L) {
                Text("${(fraction * 100).toInt()}%", color = palette.primary, fontSize = 10.sp)
            }
        }
        if (download.isActive) {
            LinearProgressIndicator(
                progress = { fraction },
                color = palette.primary,
                trackColor = palette.surfaceBorder,
                modifier = Modifier.fillMaxWidth().height(4.dp),
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            when (download.status) {
                BrowserDownloadStatus.RUNNING, BrowserDownloadStatus.PAUSED -> TextButton(onClick = onCancel) { Text("Cancel") }
                BrowserDownloadStatus.FAILED -> {
                    TextButton(onClick = onRetry) { Text("Retry") }
                    TextButton(onClick = onRemove) { Text("Remove") }
                }
                BrowserDownloadStatus.COMPLETED -> {
                    if (isVideo) TextButton(onClick = onPlay) { Text("Play in Hi Player") }
                    TextButton(onClick = onRemove) { Text("Remove") }
                }
            }
        }
    }
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

private object BrowserDownloads {
    private val _downloads = MutableStateFlow<List<BrowserDownload>>(emptyList())
    val downloads = _downloads.asStateFlow()
    private val samples = mutableMapOf<Long, DownloadSample>()
    private var restoreAttempted = false

    fun restore(context: Context) {
        if (restoreAttempted) {
            refresh(context)
            return
        }
        restoreAttempted = true
        val savedIds = runCatching {
            val json = JSONArray(context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(PREFS_IDS, "[]"))
            (0 until json.length()).mapNotNull { json.optLong(it).takeIf { id -> id > 0L } }
        }.getOrDefault(emptyList())
        _downloads.value = savedIds.distinct().map { BrowserDownload(id = it, fileName = "Download") }
        refresh(context)
    }

    fun enqueue(
        context: Context,
        rawUrl: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String? = null,
        fileNameOverride: String? = null,
    ) {
        val uri = Uri.parse(rawUrl)
        if (uri.scheme !in setOf("http", "https")) {
            Toast.makeText(context, "Downloads require an HTTP or HTTPS file link", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            val filename = (fileNameOverride ?: URLUtil.guessFileName(rawUrl, contentDisposition, mimeType))
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .take(120)
            val request = DownloadManager.Request(uri)
                .setTitle(filename)
                .setDescription("Downloading from Hi Player browser")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
            if (userAgent.isNotBlank()) request.addRequestHeader("User-Agent", userAgent)
            CookieManager.getInstance().getCookie(rawUrl)
                ?.takeIf(String::isNotBlank)
                ?.let { cookies -> request.addRequestHeader("Cookie", cookies) }
            mimeType?.takeIf(String::isNotBlank)?.let(request::setMimeType)
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val id = manager.enqueue(request)
            _downloads.value = _downloads.value + BrowserDownload(
                id = id,
                fileName = filename,
                sourceUrl = rawUrl,
                mimeType = mimeType,
            )
            persistIds(context)
        }.onSuccess {
            Toast.makeText(context, "Download started • saved to Downloads", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "Could not start this download", Toast.LENGTH_SHORT).show()
        }
    }

    fun refresh(context: Context) {
        val existing = _downloads.value
        if (existing.isEmpty()) return
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val updates = mutableMapOf<Long, BrowserDownload>()
        runCatching {
            manager.query(DownloadManager.Query().setFilterById(*existing.map { it.id }.toLongArray()))?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
                val statusColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                val bytesColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val totalColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val titleColumn = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
                val uriColumn = cursor.getColumnIndex(DownloadManager.COLUMN_URI)
                val mimeColumn = cursor.getColumnIndex(DownloadManager.COLUMN_MEDIA_TYPE)
                val now = SystemClock.elapsedRealtime()
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val downloaded = cursor.getLong(bytesColumn).coerceAtLeast(0L)
                    val previous = samples[id]
                    val elapsedMs = (now - (previous?.elapsedMs ?: now)).coerceAtLeast(1L)
                    val speed = if (previous == null) 0L else {
                        ((downloaded - previous.bytes).coerceAtLeast(0L) * 1_000L / elapsedMs)
                    }
                    samples[id] = DownloadSample(downloaded, now)
                    val status = when (cursor.getInt(statusColumn)) {
                        DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING -> BrowserDownloadStatus.RUNNING
                        DownloadManager.STATUS_PAUSED -> BrowserDownloadStatus.PAUSED
                        DownloadManager.STATUS_SUCCESSFUL -> BrowserDownloadStatus.COMPLETED
                        else -> BrowserDownloadStatus.FAILED
                    }
                    val old = existing.firstOrNull { it.id == id }
                    updates[id] = BrowserDownload(
                        id = id,
                        fileName = cursor.getString(titleColumn)?.takeIf(String::isNotBlank) ?: old?.fileName ?: "Download",
                        downloadedBytes = downloaded,
                        totalBytes = cursor.getLong(totalColumn).coerceAtLeast(0L),
                        speedBytesPerSecond = if (status == BrowserDownloadStatus.RUNNING) speed else 0L,
                        status = status,
                        sourceUrl = if (uriColumn >= 0) cursor.getString(uriColumn) ?: old?.sourceUrl else old?.sourceUrl,
                        mimeType = if (mimeColumn >= 0) cursor.getString(mimeColumn) ?: old?.mimeType else old?.mimeType,
                    )
                }
            }
        }
        _downloads.value = existing.map { download ->
            updates[download.id] ?: download.copy(status = BrowserDownloadStatus.FAILED, speedBytesPerSecond = 0L)
        }
    }

    fun cancel(context: Context, id: Long) {
        runCatching { (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).remove(id) }
        samples.remove(id)
        _downloads.value = _downloads.value.filterNot { it.id == id }
        persistIds(context)
    }

    fun retry(context: Context, id: Long) {
        val download = _downloads.value.firstOrNull { it.id == id } ?: return
        val url = download.sourceUrl
        if (url.isNullOrBlank()) {
            Toast.makeText(context, "The source link is no longer available", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching { (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).remove(id) }
        _downloads.value = _downloads.value.filterNot { it.id == id }
        samples.remove(id)
        persistIds(context)
        enqueue(context, url, "", download.fileName, download.mimeType, fileNameOverride = download.fileName)
    }

    fun removeFromList(context: Context, id: Long) {
        _downloads.value = _downloads.value.filterNot { it.id == id }
        samples.remove(id)
        persistIds(context)
    }

    fun playInPlayer(context: Context, id: Long, onPlay: (String) -> Unit) {
        val uri = runCatching {
            (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).getUriForDownloadedFile(id)
        }.getOrNull()
        if (uri == null) Toast.makeText(context, "The downloaded file is not available", Toast.LENGTH_SHORT).show()
        else onPlay(uri.toString())
    }

    fun clearCompleted(context: Context) {
        _downloads.value = _downloads.value.filter { it.isActive }
        samples.keys.retainAll(_downloads.value.map { it.id }.toSet())
        persistIds(context)
    }

    private fun persistIds(context: Context) {
        val ids = JSONArray()
        _downloads.value.forEach { ids.put(it.id) }
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(PREFS_IDS, ids.toString()).apply()
    }

    private const val PREFS_NAME = "hi_browser_downloads"
    private const val PREFS_IDS = "download_ids"
}

private data class DownloadSample(val bytes: Long, val elapsedMs: Long)

private data class BrowserDownload(
    val id: Long,
    val fileName: String,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSecond: Long = 0L,
    val status: BrowserDownloadStatus = BrowserDownloadStatus.RUNNING,
    val sourceUrl: String? = null,
    val mimeType: String? = null,
) {
    val isActive: Boolean get() = status == BrowserDownloadStatus.RUNNING || status == BrowserDownloadStatus.PAUSED
}

private enum class BrowserDownloadStatus { RUNNING, PAUSED, COMPLETED, FAILED }

private fun formatBytes(value: Long): String = when {
    value <= 0L -> "0 B"
    value < 1_024L -> "$value B"
    value < 1_024L * 1_024L -> "%.1f KB".format(value / 1_024.0)
    value < 1_024L * 1_024L * 1_024L -> "%.1f MB".format(value / (1_024.0 * 1_024.0))
    else -> "%.2f GB".format(value / (1_024.0 * 1_024.0 * 1_024.0))
}
