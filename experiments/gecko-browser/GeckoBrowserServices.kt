package com.example.browser.gecko

import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import com.example.auth.SecureWebSessions
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import com.example.downloads.BrowserDownloads
import com.example.downloads.BrowserDownloadStatus
import androidx.activity.result.ActivityResultRegistry
import androidx.annotation.MainThread
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ui.screens.OrbitBrowserStore
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/** Owns the experimental browser session and its permission, picker and download lifetimes. */
internal class GeckoBrowserServices(
    context: Context,
    registry: ActivityResultRegistry,
    store: OrbitBrowserStore,
    onCloseRequested: (Int) -> Unit,
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "gecko-download").apply { isDaemon = true } }
    private val activeHistoryId = AtomicReference<Long?>(null)
    private val pickerLauncher = GeckoFilePickerLauncher(registry)
    private val filePicker = GeckoFilePicker(appContext, pickerLauncher::launch)
    private val androidPermissions = GeckoRuntimePermissionLauncher(context, registry)
    val permissions = GeckoPermissionUi(androidPermissions)
    private val permissionBroker = GeckoPermissionBroker(permissions)
    val downloads = GeckoDownloadController(worker, { action -> main.post(action) }, onPublished = { target, fallback, mime, bytes, location ->
        val uri = target.publishedUri
        if (uri == null) false else {
            val name = runCatching {
                appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: fallback
            BrowserDownloads.recordPublishedStream(appContext, uri, target.treeUri, name, mime, bytes, location, activeHistoryId.get())
        }
    }) { name, mime ->
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        GeckoMediaStoreDestination(appContext, name, mime)
    }
    var downloadNotice by mutableStateOf<String?>(null)
        private set
    val coordinator: GeckoBrowserCoordinator<org.mozilla.geckoview.GeckoView>
    val clearData: GeckoClearDataController
    val secureRouting: GeckoSecureRouting
    val contextActions: GeckoContextActions
    val settings = createGeckoBrowserSettings(appContext, store)
    private var closed = false

    init {
        try {
            downloads.onStateChanged = { state ->
                when (state) {
                    is GeckoDownloadState.Active -> if (state.recordInHistory) {
                        val id = activeHistoryId.get() ?: synchronized(activeHistoryId) {
                            activeHistoryId.get() ?: BrowserDownloads.beginGeckoTransfer(
                                appContext, state.name, state.mimeType, state.length, state.location,
                            ).also(activeHistoryId::set)
                        }
                        BrowserDownloads.updateGeckoTransfer(id, state.name, state.mimeType,
                            state.bytes, state.length, state.speedBytesPerSecond, state.playableUri)
                    }
                    is GeckoDownloadState.Finished -> activeHistoryId.getAndSet(null)?.let { id ->
                        when {
                            state.outcome is GeckoDownloadOutcome.Complete && state.recorded == true -> Unit
                            state.outcome is GeckoDownloadOutcome.Complete -> BrowserDownloads.finishGeckoTransfer(
                                id, BrowserDownloadStatus.FAILED, "Saved, but could not be added to download history.")
                            state.outcome == GeckoDownloadOutcome.Cancelled -> BrowserDownloads.finishGeckoTransfer(id, BrowserDownloadStatus.CANCELLED)
                            else -> BrowserDownloads.finishGeckoTransfer(
                                id, BrowserDownloadStatus.FAILED, "Download failed. Try again from the website.")
                        }
                    }
                    GeckoDownloadState.Idle -> activeHistoryId.getAndSet(null)?.let { id ->
                        BrowserDownloads.finishGeckoTransfer(
                            id, BrowserDownloadStatus.CANCELLED, "Browser closed before download finished.")
                    }
                    is GeckoDownloadState.Offer -> Unit
                }
            }
            settings.applySystemScale(context.resources.configuration.fontScale)
            secureRouting = GeckoSecureRouting(
                tabPrivacy = { id -> coordinator.tabs.firstOrNull { it.id == id }?.privateTab },
                prefers = { SecureWebSessions.prefersBrowser(appContext, it) },
                rememberOrigin = { SecureWebSessions.rememberBrowser(appContext, it) },
                forgetOrigin = { SecureWebSessions.forgetBrowser(appContext, it) },
                openWebsite = { SecureWebSessions.open(context, it, android.graphics.Color.BLACK).isSuccess },
                openExternal = { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))); true }.getOrDefault(false) },
            )
            contextActions = GeckoContextActions(
                activeTab = { coordinator.tabs.firstOrNull { it.id == coordinator.activeId }?.let { it.id to it.privateTab } },
                openTab = { privateTab, url -> if (coordinator.open(privateTab)) { coordinator.load(url); true } else false },
                copy = { url ->
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Link", url)); true
                },
                share = { url ->
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
                    context.startActivity(Intent.createChooser(send, "Share link")); true
                },
            )
            coordinator = createGeckoBrowserCoordinator(
                appContext, store, secureRouting::policyForTab, secureRouting::decision,
                filePicker, permissionBroker, onCloseRequested,
                onContextLink = contextActions::offer,
                onExternalResponse = { tabId, offer ->
                val successfulResponse = offer.statusCode in 200..299
                if (!downloads.offer(offer, recordInHistory = shouldRecordDownload(tabId))) downloadNotice = if (successfulResponse)
                    "Finish the current download and try again." else "The website did not provide the file. Please try again."
            })
            clearData = createGeckoClearDataController(appContext, coordinator, worker, secureRouting::clearPrivateRouting)
        } catch (error: Exception) {
            closeServices()
            throw error
        }
    }
    private fun shouldRecordDownload(tabId: Int): Boolean = coordinator.tabs.firstOrNull { it.id == tabId }?.privateTab == false
    fun cancelDownload(id: Long) {
        if (activeHistoryId.get() == id) downloads.cancel() else BrowserDownloads.cancel(appContext, id)
    }
    fun dismissDownloadNotice() { downloadNotice = null }
    @MainThread override fun close() {
        if (closed) return
        closed = true
        clearData.close()
        try { coordinator.close() } finally { closeServices() }
    }
    private fun closeServices() {
        runCatching { permissionBroker.close() }
        runCatching { permissions.close() }
        runCatching { filePicker.close() }
        runCatching { pickerLauncher.close() }
        runCatching { downloads.close() }
        worker.shutdown()
    }
}
