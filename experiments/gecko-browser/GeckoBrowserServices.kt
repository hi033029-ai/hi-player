package com.example.browser.gecko

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
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
    navigationPolicy: (Boolean) -> GeckoNavigationPolicy,
    onNavigationDecision: (Int, GeckoNavigationDecision) -> Unit,
    onCloseRequested: (Int) -> Unit,
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "gecko-download").apply { isDaemon = true } }
    private val pickerLauncher = GeckoFilePickerLauncher(registry)
    private val filePicker = GeckoFilePicker(appContext, pickerLauncher::launch)
    private val androidPermissions = GeckoRuntimePermissionLauncher(context, registry)
    val permissions = GeckoPermissionUi(androidPermissions)
    private val permissionBroker = GeckoPermissionBroker(permissions)
    val downloads = GeckoDownloadController(worker, { action -> main.post(action) }) { name, mime ->
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        GeckoMediaStoreDestination(appContext, name, mime)
    }
    var downloadNotice by mutableStateOf<String?>(null)
        private set
    val coordinator: GeckoBrowserCoordinator<org.mozilla.geckoview.GeckoView>
    private var closed = false

    init {
        try {
            coordinator = createGeckoBrowserCoordinator(
                appContext, store, navigationPolicy, onNavigationDecision,
                filePicker, permissionBroker, onCloseRequested,
            ) { _, offer ->
                val successfulResponse = offer.statusCode in 200..299
                if (!downloads.offer(offer)) downloadNotice = if (successfulResponse)
                    "Finish the current download and try again." else "The website did not provide the file. Please try again."
            }
        } catch (error: Exception) {
            closeServices()
            throw error
        }
    }
    fun dismissDownloadNotice() { downloadNotice = null }
    @MainThread override fun close() {
        if (closed) return
        closed = true
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
