package com.example.ui.screens

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import org.json.JSONTokener
import com.example.ui.theme.LocalHiPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private data class OrbitBrowserTab(
    val id: Int,
    val privateTab: Boolean,
    val webView: WebView? = null,
    val url: String = "",
    val title: String = "New tab",
    val desktop: Boolean = false,
)

private enum class OrbitEntryList { BOOKMARKS, HISTORY }

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
fun PrivateBrowserScreen(
    onBack: () -> Unit,
    onPlayDirectMedia: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val palette = LocalHiPalette.current
    val store = remember(context) { OrbitBrowserStore(context) }
    val downloads by BrowserDownloads.downloads.collectAsState()
    var tabs by rememberSaveable(stateSaver = orbitBrowserTabsSaver) { mutableStateOf(listOf(OrbitBrowserTab(id = 1, privateTab = false))) }
    val latestTabs by rememberUpdatedState(tabs)
    var activeTabId by rememberSaveable { mutableIntStateOf(1) }
    var nextTabId by rememberSaveable { mutableIntStateOf(2) }
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
    var showTabsDialog by remember { mutableStateOf(false) }
    var entryList by remember { mutableStateOf<OrbitEntryList?>(null) }
    var entriesVersion by remember { mutableIntStateOf(0) }
    val downloadsActive = downloads.any { it.isActive }

    LaunchedEffect(activeTabId) {
        val restoredTab = tabs.firstOrNull { it.id == activeTabId }
        address = restoredTab?.url.orEmpty()
        currentUrl = restoredTab?.url.orEmpty()
        pageTitle = restoredTab?.title ?: "Hi Browser"
        showStartPage = restoredTab?.url.isNullOrBlank()
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

    fun loadAddress(raw: String) {
        val destination = normalizeBrowserInput(raw, searchProvider) ?: return
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
        tabs.forEach { tab -> tab.webView?.let { applyOrbitDarkMode(it, enabled) } }
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

    BackHandler {
        when {
            webView?.canGoBack() == true -> webView?.goBack()
            !showStartPage -> showBrowserHome()
            tabs.size > 1 -> {
                val old = activeTab
                old?.webView?.let(::disposeOrbitWebView)
                tabs = tabs.filterNot { it.id == activeTabId }
                tabs.firstOrNull()?.let(::activateTab)
            }
            else -> onBack()
        }
    }

    DisposableEffect(Unit) {
        onDispose { latestTabs.mapNotNull { it.webView }.forEach(::disposeOrbitWebView) }
    }

    LaunchedEffect(context) { OrbitBrowserFilterEngine.load(context) }
    LaunchedEffect(downloadsActive) {
        while (downloadsActive) {
            BrowserDownloads.refresh(context)
            delay(650L)
        }
    }

    Column(
        modifier = modifier.fillMaxSize().background(palette.background),
    ) {
        if (showStartPage) {
            BrowserStartPage(
                query = address,
                onQueryChange = { address = it },
                provider = searchProvider,
                onProviderChange = { searchProvider = it },
                onSearch = { loadAddress(address) },
                onManageTabs = { showTabsDialog = true },
                privateTab = activeTab?.privateTab == true,
                adBlockEnabled = store.adBlock,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().background(palette.surface).padding(horizontal = 3.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { if (webView?.canGoBack() == true) webView?.goBack() else showBrowserHome() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = palette.textPrimary)
                }
                IconButton(onClick = { if (webView?.canGoForward() == true) webView?.goForward() }) {
                    Icon(Icons.Default.ArrowForward, contentDescription = "Forward", tint = palette.textPrimary)
                }
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = palette.primary) },
                    placeholder = { Text("Search or enter a website", fontSize = 12.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = palette.textPrimary, unfocusedTextColor = palette.textPrimary,
                        focusedBorderColor = palette.primary, unfocusedBorderColor = palette.surfaceBorder,
                        focusedContainerColor = palette.surfaceElevated, unfocusedContainerColor = palette.surfaceElevated,
                    ),
                    modifier = Modifier.weight(1f),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { loadAddress(address) }),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Go),
                )
                IconButton(onClick = { webView?.reload() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = palette.textPrimary)
                }
                Box {
                    Text(
                        text = tabs.size.toString(), color = palette.textPrimary, fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 10.dp).clickable { showTabsDialog = true },
                    )
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Browser menu", tint = palette.textPrimary)
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text("New tab") }, onClick = { menuExpanded = false; openNewTab(false) })
                        DropdownMenuItem(text = { Text("New private tab") }, onClick = { menuExpanded = false; openNewTab(true) })
                        DropdownMenuItem(text = { Text("Tabs (${tabs.size})") }, onClick = { menuExpanded = false; showTabsDialog = true })
                        DropdownMenuItem(
                            text = { Text(if (store.isBookmarked(currentUrl)) "Remove bookmark" else "Add bookmark") },
                            onClick = { menuExpanded = false; addBookmark() },
                            leadingIcon = { Icon(Icons.Default.Bookmark, contentDescription = null) },
                        )
                        DropdownMenuItem(text = { Text("Bookmarks") }, onClick = { menuExpanded = false; entryList = OrbitEntryList.BOOKMARKS; entriesVersion++ })
                        DropdownMenuItem(text = { Text("History") }, onClick = { menuExpanded = false; entryList = OrbitEntryList.HISTORY; entriesVersion++ })
                        DropdownMenuItem(text = { Text("Reader mode") }, onClick = { menuExpanded = false; readCurrentPage() })
                        DropdownMenuItem(text = { Text(if (store.darkMode) "Light mode" else "Dark mode") }, onClick = { menuExpanded = false; setDarkMode(!store.darkMode) })
                        DropdownMenuItem(text = { Text(if (activeTab?.desktop == true) "Mobile site" else "Desktop site") }, onClick = { menuExpanded = false; toggleDesktopSite() })
                        DropdownMenuItem(text = { Text(if (store.adBlock) "Turn ad blocker off" else "Turn ad blocker on") }, onClick = { menuExpanded = false; store.adBlock = !store.adBlock; webView?.reload() })
                        DropdownMenuItem(text = { Text(if (store.pageSkipper) "Turn page skipper off" else "Turn page skipper on") }, onClick = { menuExpanded = false; store.pageSkipper = !store.pageSkipper; webView?.reload() })
                        DropdownMenuItem(text = { Text(if (store.youtubeAdSkip) "Turn YouTube ad skip off" else "Turn YouTube ad skip on") }, onClick = { menuExpanded = false; store.youtubeAdSkip = !store.youtubeAdSkip; webView?.reload() })
                        DropdownMenuItem(text = { Text("Update filter lists") }, onClick = {
                            menuExpanded = false
                            Toast.makeText(context, "Updating filter lists…", Toast.LENGTH_SHORT).show()
                            Thread {
                                val ok = OrbitBrowserFilterEngine.update(context)
                                android.os.Handler(context.mainLooper).post {
                                    Toast.makeText(context, if (ok) "Updated ${OrbitBrowserFilterEngine.ruleCount()} rules" else "Filter update failed", Toast.LENGTH_SHORT).show()
                                    webView?.reload()
                                }
                            }.start()
                        })
                        DropdownMenuItem(text = { Text("Share page") }, onClick = {
                            menuExpanded = false
                            if (currentUrl.isNotBlank()) runCatching {
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, currentUrl), "Share page"))
                            }
                        })
                        DropdownMenuItem(text = { Text("Downloads") }, onClick = {
                            menuExpanded = false
                            runCatching { context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) }
                        })
                        DropdownMenuItem(text = { Text("Close tab") }, onClick = {
                            menuExpanded = false
                            val oldId = activeTabId
                            val old = tabs.firstOrNull { it.id == oldId }
                            old?.webView?.let(::disposeOrbitWebView)
                            if (tabs.size > 1) {
                                tabs = tabs.filterNot { it.id == oldId }
                                tabs.firstOrNull()?.let(::activateTab)
                            } else {
                                tabs = listOf(OrbitBrowserTab(id = nextTabId++, privateTab = false))
                                activeTabId = tabs.first().id
                                showStartPage = true
                                address = ""
                                currentUrl = ""
                            }
                        })
                    }
                }
            }

            if (loadingProgress in 1..99) {
                LinearProgressIndicator(
                    progress = { loadingProgress / 100f }, color = palette.primary, trackColor = palette.surfaceBorder,
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().background(palette.surface).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = { showBrowserHome() }) {
                    Icon(Icons.Default.Home, contentDescription = "Browser home", tint = palette.textSecondary)
                }
                Text(
                    text = (if (activeTab?.privateTab == true) "Private • " else "") + pageTitle,
                    color = palette.textSecondary, fontSize = 11.sp, maxLines = 1, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { if (currentUrl.isNotBlank()) runCatching {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, currentUrl), "Share page"))
                } }) { Icon(Icons.Default.Share, contentDescription = "Share", tint = palette.textPrimary) }
                Button(
                    onClick = { onPlayDirectMedia(currentUrl) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 9.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                    enabled = currentUrl.startsWith("http://") || currentUrl.startsWith("https://"),
                ) {
                    Icon(Icons.Default.PlayCircle, contentDescription = null, modifier = Modifier.height(16.dp))
                    Spacer(Modifier.width(4.dp)); Text("Play link", fontSize = 11.sp)
                }
                IconButton(onClick = { BrowserDownloads.enqueue(context, currentUrl, webView?.settings?.userAgentString.orEmpty(), "") }) {
                    Icon(Icons.Default.Download, contentDescription = "Download", tint = palette.textPrimary)
                }
            }
            BrowserDownloadPanel(downloads = downloads, onClearCompleted = BrowserDownloads::clearCompleted)
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
                                setSupportMultipleWindows(false)
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
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    val uri = request.url
                                    if (uri.scheme == "http" || uri.scheme == "https") return false
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
                                    if (store.pageSkipper) injectOrbitAsset(view, viewContext, "orbit_page_skipper.js")
                                    if (store.youtubeAdSkip && isOrbitYoutube(url)) injectOrbitAsset(view, viewContext, "orbit_youtube.js")
                                }
                                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                    val filtered = OrbitBrowserFilterEngine.shouldBlock(request.url, store.adBlock)
                                    return if (filtered) WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0))) else null
                                }
                                override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                                    runCatching { view.destroy() }
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

    if (showTabsDialog) {
        AlertDialog(
            onDismissRequest = { showTabsDialog = false },
            title = { Text("Hi Browser tabs (${tabs.size})") },
            text = {
                Column {
                    LazyColumn(modifier = Modifier.height(260.dp)) {
                        itemsIndexed(tabs, key = { _, tab -> tab.id }) { _, tab ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { activateTab(tab); showTabsDialog = false }.padding(vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text((if (tab.privateTab) "Private • " else "") + tab.title, modifier = Modifier.weight(1f), maxLines = 1)
                                IconButton(onClick = {
                                    tab.webView?.let(::disposeOrbitWebView)
                                    val remaining = tabs.filterNot { it.id == tab.id }
                                    if (remaining.isEmpty()) {
                                        val fresh = OrbitBrowserTab(id = nextTabId++, privateTab = false)
                                        tabs = listOf(fresh); activeTabId = fresh.id; showStartPage = true; currentUrl = ""; address = ""
                                    } else {
                                        tabs = remaining
                                        if (activeTabId == tab.id) activateTab(remaining.first())
                                    }
                                }) { Icon(Icons.Default.Close, contentDescription = "Close ${tab.title}") }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTabsDialog = false; openNewTab(false) }) { Text("New tab") } },
            dismissButton = { TextButton(onClick = { showTabsDialog = false; openNewTab(true) }) { Text("Private tab") } },
        )
    }

    entryList?.let { listType ->
        val items = remember(listType, entriesVersion) {
            if (listType == OrbitEntryList.BOOKMARKS) store.bookmarks() else store.history()
        }
        AlertDialog(
            onDismissRequest = { entryList = null },
            title = { Text(if (listType == OrbitEntryList.BOOKMARKS) "Bookmarks" else "History") },
            text = {
                if (items.isEmpty()) Text(if (listType == OrbitEntryList.BOOKMARKS) "No bookmarks yet" else "No browsing history")
                else LazyColumn(modifier = Modifier.height(320.dp)) {
                    itemsIndexed(items, key = { _, item -> item.url }) { _, item ->
                        Column(Modifier.fillMaxWidth().clickable {
                            entryList = null
                            loadAddress(item.url)
                        }.padding(vertical = 9.dp)) {
                            Text(item.title, maxLines = 1, color = palette.textPrimary)
                            Text(item.url, maxLines = 1, fontSize = 11.sp, color = palette.textSecondary)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { entryList = null }) { Text("Close") } },
            dismissButton = if (listType == OrbitEntryList.HISTORY) ({
                TextButton(onClick = { store.clearHistory(); entriesVersion++ }) { Text("Clear history") }
            }) else null,
        )
    }
}

private fun disposeOrbitWebView(webView: WebView) {
    runCatching {
        webView.stopLoading()
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun injectOrbitAsset(webView: WebView, context: Context, name: String) {
    runCatching { context.assets.open(name).bufferedReader().use { it.readText().take(100_000) } }
        .onSuccess { script -> webView.post { webView.evaluateJavascript(script, null) } }
}

@Suppress("DEPRECATION")
private fun applyOrbitDarkMode(webView: WebView, enabled: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        webView.settings.forceDark = if (enabled) WebSettings.FORCE_DARK_ON else WebSettings.FORCE_DARK_OFF
    }
}

private const val DESKTOP_USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

@Composable
private fun BrowserStartPage(
    query: String,
    onQueryChange: (String) -> Unit,
    provider: BrowserSearchProvider,
    onProviderChange: (BrowserSearchProvider) -> Unit,
    onSearch: () -> Unit,
    onManageTabs: () -> Unit,
    privateTab: Boolean,
    adBlockEnabled: Boolean,
) {
    val palette = LocalHiPalette.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Language,
            contentDescription = null,
            tint = palette.primary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text("Hi Browser", color = palette.textPrimary, fontSize = 24.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Text(
            "Search the web or enter a website address",
            color = palette.textSecondary,
            fontSize = 13.sp,
        )
        TextButton(onClick = onManageTabs) { Text("Tabs · New private tab", color = palette.primary) }
        if (privateTab) {
            Text(
                "This tab won't be added to history. WebView cookies may still be shared with regular tabs.",
                color = palette.textTertiary, fontSize = 10.sp, lineHeight = 13.sp,
            )
        }
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = palette.primary) },
            trailingIcon = {
                IconButton(onClick = onSearch) {
                    Icon(Icons.Default.ArrowForward, contentDescription = "Search", tint = palette.primary)
                }
            },
            placeholder = { Text("Search with ${provider.displayName} or enter URL", fontSize = 13.sp) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = palette.textPrimary,
                unfocusedTextColor = palette.textPrimary,
                focusedBorderColor = palette.primary,
                unfocusedBorderColor = palette.surfaceBorder,
                focusedContainerColor = palette.surfaceElevated,
                unfocusedContainerColor = palette.surfaceElevated,
            ),
            modifier = Modifier.fillMaxWidth(),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onSearch() }),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Search,
            ),
        )
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
        ) {
            BrowserSearchProvider.entries.forEach { option ->
                FilterChip(
                    selected = option == provider,
                    onClick = { onProviderChange(option) },
                    label = { Text(option.displayName, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = palette.primary,
                        selectedLabelColor = Color.White,
                        containerColor = palette.surfaceElevated,
                        labelColor = palette.textPrimary,
                    ),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onSearch,
            enabled = query.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
        ) {
            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.height(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Search with ${provider.displayName}")
        }
        Spacer(Modifier.height(14.dp))
        Text(
            (if (adBlockEnabled) "Common third-party ad and tracker hosts are blocked. " else "Ad blocking is off. ") +
                "Website sign-in, access, payments, and protected playback remain controlled by each site.",
            color = palette.textTertiary,
            fontSize = 10.5.sp,
            lineHeight = 14.sp,
        )
    }
}

@Composable
private fun BrowserDownloadPanel(
    downloads: List<BrowserDownload>,
    onClearCompleted: () -> Unit,
) {
    if (downloads.isEmpty()) return
    val palette = LocalHiPalette.current
    val active = downloads.any { it.isActive }
    val downloadPulse by rememberInfiniteTransition(label = "downloadPulse").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(700), repeatMode = RepeatMode.Reverse),
        label = "downloadPulseAlpha",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.surfaceElevated)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        downloads.takeLast(3).forEach { download ->
            val fraction = if (download.totalBytes > 0) {
                (download.downloadedBytes.toFloat() / download.totalBytes.toFloat()).coerceIn(0f, 1f)
            } else 0f
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
                        BrowserDownloadStatus.COMPLETED -> Color(0xFF22C55E)
                        else -> palette.primary
                    },
                    modifier = Modifier.graphicsLayer(
                        alpha = if (download.status == BrowserDownloadStatus.RUNNING) downloadPulse else 1f,
                    ),
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = download.fileName,
                        color = palette.textPrimary,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                    Text(
                        text = when (download.status) {
                            BrowserDownloadStatus.RUNNING -> "${formatBytes(download.downloadedBytes)} of ${formatBytes(download.totalBytes)}  •  ${formatBytes(download.speedBytesPerSecond)}/s"
                            BrowserDownloadStatus.PAUSED -> "Paused • ${formatBytes(download.downloadedBytes)} downloaded"
                            BrowserDownloadStatus.COMPLETED -> "Download complete • ${formatBytes(download.downloadedBytes)}"
                            BrowserDownloadStatus.FAILED -> "Download failed"
                        },
                        color = palette.textSecondary,
                        fontSize = 10.5.sp,
                        maxLines = 1,
                    )
                }
                if (download.status == BrowserDownloadStatus.RUNNING) {
                    Text(
                        text = if (download.totalBytes > 0) "${(fraction * 100).toInt()}%" else "LIVE",
                        color = palette.primary,
                        fontSize = 11.sp,
                    )
                }
            }
            if (download.status == BrowserDownloadStatus.RUNNING || download.status == BrowserDownloadStatus.PAUSED) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        color = palette.primary,
                        trackColor = palette.surfaceBorder,
                        modifier = Modifier.fillMaxWidth().height(5.dp),
                    )
                    if (download.status == BrowserDownloadStatus.RUNNING && fraction == 0f) {
                        LinearProgressIndicator(
                            color = palette.primary.copy(alpha = 0.55f),
                            trackColor = Color.Transparent,
                            modifier = Modifier.fillMaxWidth().height(5.dp),
                        )
                    }
                }
            }
        }
        if (!active) {
            Text(
                text = "Clear download status",
                color = palette.primary,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.End).clickable(onClick = onClearCompleted),
            )
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

    fun enqueue(
        context: Context,
        rawUrl: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String? = null,
    ) {
        val uri = Uri.parse(rawUrl)
        if (uri.scheme !in setOf("http", "https")) {
            Toast.makeText(context, "Downloads require an HTTP or HTTPS file link", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            val filename = URLUtil.guessFileName(rawUrl, contentDisposition, mimeType)
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
            _downloads.value = _downloads.value + BrowserDownload(id = id, fileName = filename)
        }.onSuccess {
            Toast.makeText(context, "Download added to Android Downloads", Toast.LENGTH_SHORT).show()
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
                    )
                }
            }
        }
        _downloads.value = existing.map { download ->
            updates[download.id] ?: download.copy(status = BrowserDownloadStatus.FAILED, speedBytesPerSecond = 0L)
        }
    }

    fun clearCompleted() {
        _downloads.value = _downloads.value.filter { it.isActive }
        samples.keys.retainAll(_downloads.value.map { it.id }.toSet())
    }
}

private data class DownloadSample(val bytes: Long, val elapsedMs: Long)

private data class BrowserDownload(
    val id: Long,
    val fileName: String,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSecond: Long = 0L,
    val status: BrowserDownloadStatus = BrowserDownloadStatus.RUNNING,
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
