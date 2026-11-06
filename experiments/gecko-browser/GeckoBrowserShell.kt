package com.example.browser.gecko

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.example.ui.screens.BrowserHistoryPolicy
import com.example.ui.screens.BrowserHistorySuggestions
import com.example.ui.screens.HiBrowserHome
import com.example.ui.screens.HiBrowserTabCard
import com.example.ui.screens.HiBrowserTabSwitcher
import com.example.ui.screens.HiBrowserToolbar
import com.example.ui.screens.OrbitBrowserEntry
import com.example.ui.screens.OrbitBrowserStore
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
    modifier: Modifier = Modifier,
    permissionUi: GeckoPermissionUi? = null,
    historyRevision: Int = 0,
    pageContent: @Composable (GeckoTabController<GeckoView>) -> Unit = { GeckoBrowserSurface(it, Modifier.fillMaxSize()) },
) {
    // Observe tab structure and current snapshots independently.
    @Suppress("UNUSED_VARIABLE") val revision = coordinator.revision
    val activeId = coordinator.activeId
    val activeTab = coordinator.tabs.single { it.id == activeId }
    val snapshot = coordinator.activeSnapshot
    val home = snapshot.url.isBlank()
    var address by remember(activeId) { mutableStateOf(snapshot.url) }
    var focused by remember(activeId) { mutableStateOf(false) }
    var showTabs by remember { mutableStateOf(false) }
    var shortcutRevision by remember { mutableIntStateOf(0) }
    var history by remember { mutableStateOf(emptyList<OrbitBrowserEntry>()) }
    val focusManager = LocalFocusManager.current
    val shortcuts = remember(shortcutRevision) { store.shortcuts().map { it.title to it.url } }
    LaunchedEffect(snapshot.url, focused) { if (!focused) address = snapshot.url }
    LaunchedEffect(focused, activeTab.privateTab, historyRevision) {
        history = if (focused && !activeTab.privateTab) withContext(Dispatchers.IO) { store.history() } else emptyList()
    }
    BackHandler(enabled = !home && (snapshot.fullscreen || snapshot.canGoBack)) {
        if (snapshot.fullscreen) coordinator.exitFullScreen() else coordinator.back()
    }

    fun navigate(text: String) {
        val url = normalizeAddress(text) ?: return
        focused = false
        address = url
        coordinator.load(url)
    }

    Box(modifier.fillMaxSize()) {
        if (home) {
            HiBrowserHome(
                isPrivate = activeTab.privateTab,
                shortcuts = shortcuts,
                // Filtering is not connected to Gecko yet; do not claim it is enabled.
                adBlockEnabled = false,
                onVisit = ::navigate,
                onAddShortcut = { name, url ->
                    store.saveShortcuts(store.shortcuts() + OrbitBrowserEntry(name, url))
                    shortcutRevision++
                },
                onRemoveShortcut = { url ->
                    store.saveShortcuts(store.shortcuts().filterNot { it.url == url })
                    shortcutRevision++
                },
                bottomInset = 72.dp,
            )
        } else {
            pageContent(coordinator.controller)
        }
        if (!snapshot.fullscreen) Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            if (!home && snapshot.loading) {
                LinearProgressIndicator(progress = { snapshot.progress.coerceIn(0, 100) / 100f }, modifier = Modifier.fillMaxWidth().padding(horizontal = 30.dp))
            }
            if (focused && !activeTab.privateTab) {
                BrowserHistorySuggestions(
                    entries = remember(history, address) { BrowserHistoryPolicy.suggestions(history, address) },
                    onOpen = { focusManager.clearFocus(); navigate(it) },
                )
            }
            HiBrowserToolbar(
                address = address,
                onAddressChange = { address = it },
                onFocusChange = { focused = it },
                onSubmit = ::navigate,
                isPrivate = activeTab.privateTab,
                isSecure = !home && snapshot.secure,
                isLoading = snapshot.loading,
                showReload = !home,
                tabCount = coordinator.tabs.size,
                providers = providerNames,
                selectedProvider = selectedProvider,
                onProviderSelect = onProviderSelect,
                onHome = { coordinator.home(); address = "" },
                onVoiceSearch = onVoiceSearch,
                onReloadOrStop = { if (snapshot.loading) coordinator.stop() else coordinator.reload() },
                onShowTabs = { showTabs = true },
                onMenu = onMenu,
            )
        }
    }
    if (!home && snapshot.error != null) {
        AlertDialog(
            onDismissRequest = { coordinator.home() },
            title = { Text("Page could not be loaded") },
            text = { Text(if (snapshot.error == GeckoPageError.SECURITY) "The connection could not be verified." else "Check the address or connection and try again.") },
            confirmButton = { TextButton(onClick = { coordinator.reload() }) { Text("Retry") } },
            dismissButton = { TextButton(onClick = { coordinator.home() }) { Text("Home") } },
        )
    }
    if (permissionUi != null) GeckoPermissionDialog(permissionUi)
    if (showTabs && permissionUi?.request == null) {
        HiBrowserTabSwitcher(
            tabs = coordinator.tabs.map { tab ->
                val state = coordinator.snapshots[tab.id] ?: GeckoTabSnapshot(tab.id, tab.privateTab)
                HiBrowserTabCard(tab.id, state.title, state.url, tab.privateTab)
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
