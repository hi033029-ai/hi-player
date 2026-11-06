package com.example.browser.gecko

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.ui.screens.BrowserClearDataDialog
import com.example.ui.screens.HiBrowserLibrary
import com.example.ui.screens.HiBrowserMenuSheet
import com.example.ui.screens.HiLibrarySection
import com.example.ui.screens.HiMenuAction
import com.example.ui.screens.OrbitBrowserEntry
import com.example.ui.screens.OrbitBrowserStore
import org.mozilla.geckoview.GeckoView

/** Experimental menu/library wiring. Platform actions remain the host's responsibility. */
@Composable
internal fun GeckoBrowserChrome(
    coordinator: GeckoBrowserCoordinator<GeckoView>,
    store: OrbitBrowserStore,
    providerNames: List<String>,
    selectedProvider: String,
    onProviderSelect: (Int) -> Unit,
    normalizeAddress: (String) -> String?,
    onVoiceSearch: () -> Unit,
    onSettings: () -> Unit,
    onExit: () -> Unit,
    downloadsContent: @Composable (String) -> Unit,
    appNavigation: @Composable () -> Unit = {},
    permissionUi: GeckoPermissionUi? = null,
    services: GeckoBrowserServices? = null,
    modifier: Modifier = Modifier,
    pageContent: @Composable (GeckoTabController<GeckoView>) -> Unit = { GeckoBrowserSurface(it) },
) {
    var showMenu by remember { mutableStateOf(false) }
    var showClearData by remember { mutableStateOf(false) }
    var librarySection by remember { mutableStateOf<HiLibrarySection?>(null) }
    var libraryRevision by remember { mutableIntStateOf(0) }
    var clearing by remember { mutableStateOf(false) }
    var clearFailed by remember { mutableStateOf(false) }
    @Suppress("UNUSED_VARIABLE") val coordinatorRevision = coordinator.revision
    val current = coordinator.activeSnapshot
    val onPage = current.url.isNotBlank()
    val bookmarkUrl = GeckoTabPersistence.safeUrl(current.url)
    val saved = bookmarkUrl.isNotEmpty() && store.isBookmarked(bookmarkUrl)

    GeckoBrowserShell(
        coordinator = coordinator, store = store, providerNames = providerNames,
        selectedProvider = selectedProvider, onProviderSelect = onProviderSelect,
        normalizeAddress = normalizeAddress, onVoiceSearch = onVoiceSearch,
        onMenu = { showMenu = true }, modifier = modifier, permissionUi = services?.permissions ?: permissionUi, historyRevision = libraryRevision, pageContent = pageContent,
    )
    if (services != null) {
        GeckoDownloadUi(services.downloads)
        services.downloadNotice?.takeIf { services.downloads.state !is GeckoDownloadState.Offer && services.downloads.state !is GeckoDownloadState.Finished }?.let { notice ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = services::dismissDownloadNotice,
                title = { androidx.compose.material3.Text("Download unavailable") },
                text = { androidx.compose.material3.Text(notice) },
                confirmButton = { androidx.compose.material3.TextButton(onClick = services::dismissDownloadNotice) { androidx.compose.material3.Text("OK") } },
            )
        }
    }
    if (services != null) {
        LaunchedEffect(services.clearData.state) {
            if (services.clearData.state == GeckoClearDataState.COMPLETE || services.clearData.state == GeckoClearDataState.FAILED) libraryRevision++
        }
        if (showClearData) BrowserClearDataDialog(
            onConfirm = { history, cookies, cache ->
                showClearData = false
                services.clearData.clear(GeckoClearDataSelection(history, cookies, cache))
            },
            onDismiss = { showClearData = false },
        )
        when (services.clearData.state) {
            GeckoClearDataState.CLEARING -> androidx.compose.material3.AlertDialog(
                onDismissRequest = {},
                title = { androidx.compose.material3.Text("Clearing browsing data") },
                text = { androidx.compose.material3.LinearProgressIndicator() },
                confirmButton = {},
            )
            GeckoClearDataState.COMPLETE, GeckoClearDataState.FAILED -> androidx.compose.material3.AlertDialog(
                onDismissRequest = services.clearData::dismissResult,
                title = { androidx.compose.material3.Text(if (services.clearData.state == GeckoClearDataState.COMPLETE) "Clear request finished" else "Some data could not be cleared") },
                text = { androidx.compose.material3.Text(if (services.clearData.state == GeckoClearDataState.COMPLETE) "Open-tab history clearing was requested. Device-browser sign-ins are unchanged." else "Please try again. Some data may already have been cleared.") },
                confirmButton = { androidx.compose.material3.TextButton(onClick = services.clearData::dismissResult) { androidx.compose.material3.Text("OK") } },
            )
            GeckoClearDataState.IDLE -> Unit
        }
    }
    if (showMenu) {
        HiBrowserMenuSheet(
            tiles = listOf(
                HiMenuAction("Bookmarks", Icons.Default.Bookmark) { librarySection = HiLibrarySection.BOOKMARKS },
                HiMenuAction("History", Icons.Default.History) { librarySection = HiLibrarySection.HISTORY },
                HiMenuAction("Downloads", Icons.Default.Download) { librarySection = HiLibrarySection.DOWNLOADS },
                HiMenuAction("New tab", Icons.Default.Add) { coordinator.open(false) },
            ),
            onSetDefaultBrowser = null,
            tools = listOf(HiMenuAction("Desktop site", Icons.Default.DesktopWindows, enabled = onPage, checked = coordinator.desktopMode()) { coordinator.setDesktop(!coordinator.desktopMode()) }),
            rows = listOf(
                HiMenuAction("New private tab", Icons.Default.VisibilityOff) { coordinator.open(true) },
                HiMenuAction("Settings", Icons.Default.Settings) { onSettings() },
                *if (services != null) arrayOf(HiMenuAction("Clear browsing data", Icons.Default.History) { showClearData = true }) else emptyArray(),
                HiMenuAction("Close this tab", Icons.Default.Close) { coordinator.closeTab(coordinator.activeId) },
            ),
            bottomRow = listOf(
                HiMenuAction("Back", Icons.Default.ArrowBack, enabled = current.canGoBack) { coordinator.back() },
                HiMenuAction("Forward", Icons.Default.ArrowForward, enabled = current.canGoForward) { coordinator.forward() },
                HiMenuAction(if (saved) "Remove bookmark" else "Add bookmark", if (saved) Icons.Default.Star else Icons.Default.StarBorder, enabled = bookmarkUrl.isNotEmpty(), checked = saved) {
                    if (saved) store.removeBookmark(bookmarkUrl)
                    else store.addBookmark(OrbitBrowserEntry(current.title.ifBlank { bookmarkUrl }, bookmarkUrl))
                    libraryRevision++
                },
                HiMenuAction("Exit browser", Icons.Default.PowerSettingsNew) { onExit() },
                HiMenuAction("Reload", Icons.Default.Refresh, enabled = onPage) { coordinator.reload() },
            ),
            onDismiss = { showMenu = false },
        )
    }
    librarySection?.let { section ->
        val bookmarks = remember(libraryRevision) { store.bookmarks().map { it.title to it.url } }
        val history = remember(libraryRevision) { store.history().map { it.title to it.url } }
        HiBrowserLibrary(
            initialSection = section, appNavigation = appNavigation, bookmarks = bookmarks, history = history,
            onOpen = { url -> coordinator.load(url); librarySection = null },
            onRemove = { from, url ->
                if (from == HiLibrarySection.BOOKMARKS) {
                    store.removeBookmark(url)
                    libraryRevision++
                } else {
                    coordinator.removeHistory(url) { success -> clearFailed = !success; libraryRevision++ }
                }
            },
            onClearHistory = {
                if (!clearing) {
                    clearing = true
                    coordinator.clearHistory { success -> clearing = false; clearFailed = !success; libraryRevision++ }
                }
            },
            downloadsContent = downloadsContent,
            onDismiss = { librarySection = null },
        )
    }
    if (clearFailed) androidx.compose.material3.AlertDialog(
        onDismissRequest = { clearFailed = false },
        title = { androidx.compose.material3.Text("Could not update history") },
        text = { androidx.compose.material3.Text("Please try again.") },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { clearFailed = false }) { androidx.compose.material3.Text("OK") } },
    )
}
