package com.example.browser.gecko

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add; import androidx.compose.material.icons.filled.AccountCircle; import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.example.auth.SecureWebsitePolicy
import com.example.ui.screens.BrowserClearDataDialog
import com.example.ui.screens.HiBrowserLibrary
import com.example.ui.screens.HiBrowserMenuSheet
import com.example.ui.screens.HiBrowserTools
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
    pageContent: @Composable (GeckoTabController<GeckoView>) -> Unit = { GeckoBrowserSurface(it) }, account: GeckoAccountHooks? = null,
    onPlayDetected: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showClearData by remember { mutableStateOf(false) }
    var showAppearance by remember { mutableStateOf(false) }
    var librarySection by remember { mutableStateOf<HiLibrarySection?>(null) }
    var libraryRevision by remember { mutableIntStateOf(0) }
    var clearing by remember { mutableStateOf(false) }
    var clearFailed by remember { mutableStateOf(false) }
    var qrNotice by remember { mutableStateOf<String?>(null) }
    @Suppress("UNUSED_VARIABLE") val coordinatorRevision = coordinator.revision
    val current = coordinator.activeSnapshot
    val find = remember(coordinator) { GeckoFindController(coordinator.controller::find, coordinator.controller::clearFind) }
    DisposableEffect(find) { onDispose { find.close() } }
    LaunchedEffect(coordinator.activeId, current.url, current.loading) { find.pageChanged(coordinator.activeId, current.url, current.loading) }
    val onPage = current.url.isNotBlank() && services?.secureRouting?.route(coordinator.activeId) == null
    val bookmarkUrl = if (onPage) GeckoTabPersistence.safeUrl(current.url) else ""
    val saved = bookmarkUrl.isNotEmpty() && store.isBookmarked(bookmarkUrl)
    var popupRevision by remember { mutableIntStateOf(0) }
    var popupNotice by remember { mutableStateOf<String?>(null) }
    @Suppress("UNUSED_VARIABLE") val popupStateRevision = popupRevision
    val popupSession = (coordinator.controller.sessionFor(coordinator.activeId) as? GeckoManagedTab)?.engine?.session
    val popupAllowed = geckoPopupExceptions.isAllowed(popupSession, current.url)


    GeckoBrowserShell(
        coordinator = coordinator, store = store, providerNames = providerNames,
        selectedProvider = selectedProvider, onProviderSelect = onProviderSelect,
        normalizeAddress = normalizeAddress, onVoiceSearch = onVoiceSearch,
        onMenu = { showMenu = true }, find = find, modifier = modifier, permissionUi = services?.permissions ?: permissionUi, historyRevision = libraryRevision, secureRouting = services?.secureRouting, pageContent = pageContent,
    )
    if (services != null) {
        LaunchedEffect(coordinator.revision) { services.secureRouting.prune(coordinator.tabs.map { it.id }.toSet()) }
        LaunchedEffect(coordinator.activeId, current.url, current.loading) { services.contextActions.dismiss() }
        GeckoSecureRoutingDialogs(services.secureRouting, coordinator.activeId)
        GeckoContextActionsDialog(services.contextActions, coordinator.activeId)
        GeckoDownloadUi(services.downloads)
        GeckoWebsitePromptDialog(services.filePicker, popupSession, current.url)
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
        val fontScale = LocalConfiguration.current.fontScale
        LaunchedEffect(services.settings, fontScale) { services.settings.applySystemScale(fontScale) }
        LaunchedEffect(services.clearData.state) {
            if (services.clearData.state == GeckoClearDataState.COMPLETE || services.clearData.state == GeckoClearDataState.FAILED) libraryRevision++
        }
        if (showAppearance) GeckoAppearanceDialog(services.settings) { showAppearance = false }
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
    popupNotice?.let { notice ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { popupNotice = null },
            title = { androidx.compose.material3.Text("Site pop-ups") },
            text = { androidx.compose.material3.Text(notice) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { popupNotice = null }) { androidx.compose.material3.Text("OK") } },
        )
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
            tools = listOf(
                HiMenuAction("Find in page", Icons.Default.FindInPage, enabled = onPage) { find.open(coordinator.activeId, current.url) },
                *if (services != null) arrayOf(HiMenuAction("Share page", Icons.Default.Share, enabled = onPage) { services.contextActions.offer(coordinator.activeId, current.url, null) }) else emptyArray(),
                *if (services != null) arrayOf(HiMenuAction("Open in secure browser", Icons.Default.OpenInNew,
                    enabled = onPage && SecureWebsitePolicy.originKey(current.url) != null) {
                    services.secureRouting.decision(coordinator.activeId, GeckoNavigationDecision.SecureSite(current.url, userGesture = true))
                }) else emptyArray(),
                *if (onPlayDetected != null && current.mediaUrl != null) arrayOf(HiMenuAction("Play detected video", Icons.Default.FindInPage, enabled = onPage) { onPlayDetected() }) else emptyArray(),
                HiMenuAction("Scan QR code", Icons.Default.QrCodeScanner) {
                    showMenu = false
                    HiBrowserTools.scanQrCode(
                        context,
                        onResult = { value ->
                            val url = normalizeAddress(value)
                            if (url == null) qrNotice = "That QR code doesn't contain a supported page or search."
                            else { services?.secureRouting?.leaveTab(coordinator.activeId); coordinator.load(url) }
                        },
                        onError = { qrNotice = it },
                    )
                },
                HiMenuAction(if (popupAllowed) "Block site pop-ups" else "Allow site pop-ups", Icons.Default.OpenInNew,
                    enabled = onPage && popupSession != null && geckoPopupExceptions.origin(current.url) != null,
                    checked = popupAllowed) {
                    popupSession?.let { session ->
                        if (popupAllowed) {
                            geckoPopupExceptions.revoke(session)
                            popupNotice = "Site pop-up exception removed."
                        } else if (geckoPopupExceptions.allow(session, current.url)) {
                            popupNotice = "Pop-ups are allowed for this site in this tab until it closes. Ad filters still apply, but some unwanted pop-ups may get through. Try the link again; reload only if needed."
                        }
                        popupRevision++
                    }
                },
                HiMenuAction("Desktop site", Icons.Default.DesktopWindows, enabled = onPage, checked = coordinator.desktopMode()) {
                    coordinator.setDesktop(!coordinator.desktopMode())
                    coordinator.reload()
                },
            ),
            rows = listOf(
                *if (account != null) arrayOf(HiMenuAction(if (account.signedIn()) account.name()?.takeIf { it.isNotBlank() } ?: account.email()?.takeIf { it.isNotBlank() } ?: "Hi Player account" else "Sign in to Hi Player", Icons.Default.AccountCircle, avatarUrl = account.photoUrl()) { account.openAccount() }) else emptyArray(),
                // Website sign-in remains available from each website, not as a menu tile.
                HiMenuAction("New private tab", Icons.Default.VisibilityOff) { coordinator.open(true) },
                HiMenuAction("Settings", Icons.Default.Settings) { onSettings() },
                *if (services != null) arrayOf(HiMenuAction("Clear browsing data", Icons.Default.History) { showClearData = true }) else emptyArray(),
                HiMenuAction("Close this tab", Icons.Default.Close) { coordinator.closeTab(coordinator.activeId) },
            ),
            bottomRow = listOf(
                HiMenuAction("Back", Icons.Default.ArrowBack, enabled = onPage && current.canGoBack) { coordinator.back() },
                HiMenuAction("Forward", Icons.Default.ArrowForward, enabled = onPage && current.canGoForward) { coordinator.forward() },
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
    qrNotice?.let { notice -> androidx.compose.material3.AlertDialog(
        onDismissRequest = { qrNotice = null },
        title = { androidx.compose.material3.Text("QR scanner") },
        text = { androidx.compose.material3.Text(notice) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { qrNotice = null }) { androidx.compose.material3.Text("OK") } },
    ) }
}
