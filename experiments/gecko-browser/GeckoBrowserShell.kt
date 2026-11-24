package com.example.browser.gecko

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import com.example.ui.theme.AppAlertDialog as AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.example.ui.components.BrowserPageSkeleton
import com.example.ui.screens.BrowserFindBar
import com.example.ui.screens.BrowserHistoryPolicy
import com.example.ui.screens.BrowserHistorySuggestions
import com.example.ui.screens.HiBrowserHome
import com.example.ui.screens.HiBrowserTabCard
import com.example.ui.screens.HiBrowserTabSwitcher
import com.example.ui.screens.HiBrowserToolbar
import com.example.ui.screens.OrbitBrowserEntry
import com.example.ui.screens.OrbitBrowserStore
import com.example.ui.theme.LocalHiPalette
import org.mozilla.geckoview.GeckoView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Experimental browser chrome connected to Gecko. The host supplies search normalization,
 * the settings/menu and voice actions. Not a complete browser or a production entry point.
 */
@Composable
internal fun GeckoBrowserShell(
    coordinator: GeckoBrowserCoordinator<GeckoView>,
    store: OrbitBrowserStore,
    providerNames: List<String>,
    selectedProvider: String,
    onProviderSelect: (Int) -> Unit,
    normalizeAddress: (String) -> String?,
    onVoiceSearch: () -> Unit,
    onMenu: () -> Unit,
    onNavigate: ((String) -> Unit)? = null,
    onVideoBack: (() -> Boolean)? = null,
    onDownloads: (() -> Unit)? = null,
    find: GeckoFindController? = null,
    modifier: Modifier = Modifier,
    permissionUi: GeckoPermissionUi? = null,
    historyRevision: Int = 0,
    bookmarks: List<Pair<String, String>>? = null,
    secureRouting: GeckoSecureRouting? = null,
    pageContent: @Composable (GeckoTabController<GeckoView>) -> Unit = { GeckoBrowserSurface(it, Modifier.fillMaxSize()) },
) {
    // Observe tab structure and current snapshots independently.
    @Suppress("UNUSED_VARIABLE") val revision = coordinator.revision
    val activeId = coordinator.activeId
    val activeTab = coordinator.tabs.single { it.id == activeId }
    val snapshot = coordinator.activeSnapshot
    fun capturePage() = GeckoTabPreviews.capture(coordinator.controller, snapshot.url) { coordinator.activeSnapshot.url }
    LaunchedEffect(activeId, snapshot.url, snapshot.loading, snapshot.awaitingFirstPaint) {
        if (!snapshot.loading && !snapshot.awaitingFirstPaint && snapshot.error == null && !activeTab.privateTab) {
            kotlinx.coroutines.delay(350)
            capturePage()
        }
    }
    val secureUrl = secureRouting?.route(activeId)
    val home = snapshot.url.isBlank() && secureUrl == null
    var address by remember(activeId) { mutableStateOf(snapshot.url) }
    var focused by remember(activeId) { mutableStateOf(false) }
    var showTabs by remember { mutableStateOf(false) }
    var shortcutRevision by remember { mutableIntStateOf(0) }
    var history by remember { mutableStateOf(emptyList<OrbitBrowserEntry>()) }
    val focusManager = LocalFocusManager.current
    val shortcuts = remember(shortcutRevision) { store.shortcuts().map { it.title to it.url } }
    val displayedBookmarks = bookmarks ?: remember(historyRevision) {
        store.bookmarks().map { it.title to it.url }
    }
    val scrollChrome = coordinator.controller.scrollChrome
    var barHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val navBottomPx = WindowInsets.navigationBars.getBottom(density)
    var toolbarReveal by remember(activeId, snapshot.url) { mutableIntStateOf(0) }
    var toolbarIdle by remember(activeId, snapshot.url) { mutableStateOf(false) }
    val editing = focused || find?.visible == true
    LaunchedEffect(activeId, snapshot.url, editing, toolbarReveal) {
        toolbarIdle = false
        if (!editing) {
            kotlinx.coroutines.delay(3_000)
            toolbarIdle = true
        }
    }
    val manuallyRevealed = toolbarReveal > 0 && !toolbarIdle
    val showBrowserBar = home || secureUrl != null || editing || manuallyRevealed ||
        (!snapshot.videoPlaying && !toolbarIdle && !scrollChrome.hidden)
    val barHiddenFraction by animateFloatAsState(
        targetValue = if (showBrowserBar) 0f else 1f,
        animationSpec = tween(durationMillis = 220),
        label = "gecko-browser-bar",
    )
    LaunchedEffect(activeId, snapshot.url, secureUrl) { scrollChrome.show() }
    LaunchedEffect(snapshot.url, secureUrl, focused) { if (!focused) address = secureUrl ?: snapshot.url }
    LaunchedEffect(focused, activeTab.privateTab, historyRevision) {
        history = if (focused && !activeTab.privateTab) withContext(Dispatchers.IO) { store.history() } else emptyList()
    }
    BackHandler(enabled = find?.visible == true) { find?.dismiss() }
    BackHandler(enabled = find?.visible != true && secureUrl != null) { secureRouting?.leaveTab(activeId) }
    BackHandler(enabled = find?.visible != true && secureUrl == null && !home && (snapshot.fullscreen || coordinator.canGoBack || (snapshot.videoPlaying && onVideoBack != null))) {
        if (snapshot.videoPlaying && onVideoBack?.invoke() == true) return@BackHandler
        if (snapshot.fullscreen) coordinator.exitFullScreen()
        else if (coordinator.canGoBack) coordinator.back() else coordinator.home()
    }

    // Editing is the topmost local state, before page history or browser exit.
    BackHandler(enabled = focused && find?.visible != true) {
        focusManager.clearFocus(force = true)
        focused = false
    }

    fun navigate(text: String) {
        val url = normalizeAddress(text) ?: return
        focusManager.clearFocus(force = true)
        focused = false
        address = url
        secureRouting?.leaveTab(activeId)
        if (onNavigate != null) onNavigate(url) else coordinator.load(url)
    }

    Box(modifier.fillMaxSize().imePadding()) {
        if (secureUrl != null && secureRouting != null) {
            GeckoSecureSitePage(secureRouting, activeId) { coordinator.load(it) }
        } else if (home) {
            HiBrowserHome(
                isPrivate = activeTab.privateTab,
                shortcuts = shortcuts,
                bookmarks = displayedBookmarks,
                onVisit = ::navigate,
                onAddShortcut = { name, url ->
                    store.saveShortcuts(store.shortcuts() + OrbitBrowserEntry(name, url))
                    shortcutRevision++
                },
                onRemoveShortcut = { url ->
                    store.saveShortcuts(store.shortcuts().filterNot { it.url == url })
                    shortcutRevision++
                },
                bottomInset = with(density) { barHeightPx.toDp() }.coerceAtLeast(72.dp),
            )
        } else {
            pageContent(coordinator.controller)
            if (snapshot.awaitingFirstPaint && !snapshot.fullscreen) {
                val layout = com.example.ui.screens.BrowserLoadingPolicy.placeholder(snapshot.url, coordinator.desktopMode())
                if (layout == com.example.ui.screens.BrowserPlaceholderLayout.NONE) BrowserPageSkeleton()
                else com.example.ui.components.BrowserSiteSkeleton(layout)
            }
            if (!snapshot.fullscreen) androidx.compose.runtime.key(activeId) {
                val bottomOffset = with(density) { (barHeightPx * (1f - barHiddenFraction)).toDp() }
                BrowserLoadingBar(snapshot.progress / 100f, snapshot.loading,
                    Modifier.align(Alignment.BottomCenter).padding(bottom = bottomOffset))
            }
        }
        if (!snapshot.fullscreen && !home && secureUrl == null && !editing) {
            val palette = LocalHiPalette.current
            val toolbarOffset = with(density) { (barHeightPx * (1f - barHiddenFraction)).toDp() }
            IconButton(
                onClick = { if (showBrowserBar) toolbarIdle = true else toolbarReveal++ },
                modifier = Modifier.align(Alignment.BottomStart)
                    .padding(start = 8.dp, bottom = toolbarOffset + 8.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = palette.surface,
                    contentColor = palette.textPrimary,
                ),
            ) {
                Icon(
                    if (showBrowserBar) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = if (showBrowserBar) "Hide browser toolbar" else "Show browser toolbar",
                )
            }
        }
        if (!snapshot.fullscreen) Column(Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .graphicsLayer { translationY = barHiddenFraction * (barHeightPx + navBottomPx + 24.dp.toPx()) }
            .onSizeChanged { barHeightPx = it.height }) {
            if (find?.visible == true) BrowserFindBar(
                query = find.query, matchLabel = find.label,
                onQueryChange = find::changeQuery, onPrevious = { find.next(true) },
                onNext = { find.next(false) }, onClose = find::dismiss,
            )
            if (focused && !activeTab.privateTab) {
                BrowserHistorySuggestions(
                    entries = remember(history, address) { BrowserHistoryPolicy.suggestions(history, address) },
                    onOpen = { focusManager.clearFocus(); navigate(it) },
                )
            }
            androidx.compose.runtime.key(activeId) { HiBrowserToolbar(
                address = address,
                onAddressChange = { address = it },
                onFocusChange = { focused = it },
                onSubmit = ::navigate,
                isPrivate = activeTab.privateTab,
                isSecure = secureUrl == null && !home && snapshot.secure,
                isLoading = secureUrl == null && snapshot.loading,
                showReload = !home,
                tabCount = coordinator.tabs.size,
                providers = providerNames,
                selectedProvider = selectedProvider,
                onProviderSelect = onProviderSelect,
                onHome = { focusManager.clearFocus(force = true); secureRouting?.leaveTab(activeId); coordinator.home(); address = "" },
                onVoiceSearch = onVoiceSearch,
                onReloadOrStop = { if (secureUrl != null) secureRouting?.continueWebsite(activeId) else if (snapshot.loading) coordinator.stop() else coordinator.reload() },
                onShowTabs = { capturePage(); focusManager.clearFocus(force = true); showTabs = true },
                onMenu = { focusManager.clearFocus(force = true); onMenu() },
                onDownloads = onDownloads,
            ) }
        }
    }
    if (!home && secureUrl == null && snapshot.error != null) {
        fun leaveError() {
            if (coordinator.canGoBack) coordinator.back() else coordinator.home()
        }
        AlertDialog(
            onDismissRequest = ::leaveError,
            title = { Text("Page could not be loaded") },
            text = { Text(if (snapshot.error == GeckoPageError.SECURITY) "The connection could not be verified." else "Check the address or connection and try again.") },
            confirmButton = { TextButton(onClick = { coordinator.reload() }) { Text("Retry") } },
            dismissButton = { TextButton(onClick = ::leaveError) { Text(if (coordinator.canGoBack) "Back" else "Home") } },
        )
    }
    if (permissionUi != null) GeckoPermissionDialog(permissionUi)
    if (showTabs && permissionUi?.request == null) {
        HiBrowserTabSwitcher(
            tabs = coordinator.tabs.map { tab ->
                val state = coordinator.snapshots[tab.id] ?: GeckoTabSnapshot(tab.id, tab.privateTab)
                HiBrowserTabCard(tab.id, state.title, state.url, tab.privateTab, thumbnail = if (tab.privateTab) null else com.example.ui.screens.WebPagePreviews.pages[state.url])
            },
            activeTabId = activeId,
            startPrivate = activeTab.privateTab,
            onSelect = { coordinator.select(it); showTabs = false },
            onClose = { coordinator.closeTab(it) },
            onNewTab = { isPrivate -> if (coordinator.open(isPrivate)) showTabs = false },
            onCloseAll = { isPrivate -> coordinator.tabs.filter { it.privateTab == isPrivate }.map { it.id }.forEach { coordinator.closeTab(it) } },
            onDismiss = { showTabs = false },
        )
    }
}
