package com.example.browser.gecko

import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import com.example.auth.SecureWebSessions
import android.os.Handler
import android.os.Looper
import com.example.downloads.BrowserDownloads
import androidx.activity.result.ActivityResultRegistry
import androidx.annotation.MainThread
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ui.screens.OrbitBrowserStore
import java.util.concurrent.Executors

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
    private val downloadRuntime = GeckoDownloadRuntime.attach(appContext)
    val downloads: GeckoDownloadController get() = downloadRuntime.controller
    private val pickerLauncher = GeckoFilePickerLauncher(registry)
    private val filePicker = GeckoFilePicker(appContext, pickerLauncher::launch)
    private val androidPermissions = GeckoRuntimePermissionLauncher(context, registry)
    val permissions = GeckoPermissionUi(androidPermissions)
    private val permissionBroker = GeckoPermissionBroker(permissions)
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
        BrowserDownloads.cancel(appContext, id)
    }
    fun pauseDownload(id: Long) {
        BrowserDownloads.pause(appContext, id)
    }
    fun resumeDownload(id: Long) {
        BrowserDownloads.retry(appContext, id)
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
        runCatching { GeckoDownloadRuntime.detach(downloadRuntime) }
        worker.shutdown()
    }
}
