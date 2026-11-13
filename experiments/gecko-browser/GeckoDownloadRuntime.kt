package com.example.browser.gecko

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import androidx.annotation.MainThread
import com.example.downloads.BrowserDownloadStatus
import com.example.downloads.BrowserDownloads
import com.example.downloads.ExternalBrowserDownloadControl
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/** Process-scoped owner for the authenticated response stream, independent of browser UI lifetime. */
internal object GeckoDownloadRuntime {
    private var current: Runtime? = null

    @MainThread
    fun attach(context: Context): Runtime = synchronized(this) {
        val runtime = current?.takeUnless { it.isReleased } ?: Runtime(context.applicationContext).also { current = it }
        runtime.addClient()
        runtime
    }

    @MainThread
    fun detach(runtime: Runtime) {
        runtime.removeClient()
        if (runtime.clientCount == 0) {
            when (val state = runtime.controller.state) {
                is GeckoDownloadState.Active -> if (!state.recordInHistory) runtime.controller.cancel()
                else Unit // Keep recorded transfers alive for notification controls.
                is GeckoDownloadState.Offer -> runtime.controller.reject()
                else -> dispose(runtime)
            }
        }
    }

    @MainThread
    private fun dispose(runtime: Runtime) = synchronized(this) {
        if (runtime.clientCount != 0 || runtime.isReleased || runtime.controller.state is GeckoDownloadState.Active) return@synchronized
        runtime.shutdown()
        if (current === runtime) current = null
    }

    internal class Runtime(context: Context) {
        private val appContext = context.applicationContext
        private val main = Handler(Looper.getMainLooper())
        private val worker = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "gecko-download").apply { isDaemon = true } }
        private val activeHistoryId = AtomicReference<Long?>(null)
        @Volatile var clientCount: Int = 0
            private set
        @Volatile var isReleased: Boolean = false
            private set
        val controller: GeckoDownloadController

        init {
            controller = GeckoDownloadController(worker, { action -> main.post(action) }, onPublished = { target, fallback, mime, bytes, location ->
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
            controller.onStateChanged = ::onStateChanged
            controller.onTransferStarted = { cancelTransfer ->
                val id = activeHistoryId.get()
                if (id == null) null else BrowserDownloads.registerExternalTransfer(
                    appContext,
                    id,
                    ExternalBrowserDownloadControl(
                        pause = { main.post { controller.pause() } },
                        resume = { main.post { controller.resume() } },
                        cancel = { main.post { cancelTransfer() } },
                    ),
                )
            }
        }

        @MainThread
        fun addClient() { if (!isReleased) clientCount++ }

        @MainThread
        fun removeClient() { if (clientCount > 0) clientCount-- }

        private fun onStateChanged(state: GeckoDownloadState) {
            if (isReleased) return
            when (state) {
                is GeckoDownloadState.Active -> if (state.recordInHistory) {
                    val id = activeHistoryId.get() ?: synchronized(activeHistoryId) {
                        activeHistoryId.get() ?: BrowserDownloads.beginGeckoTransfer(
                            appContext, state.name, state.mimeType, state.length, state.location,
                        ).also(activeHistoryId::set)
                    }
                    if (state.paused) BrowserDownloads.pauseGeckoTransfer(id)
                    else BrowserDownloads.updateGeckoTransfer(
                        id, state.name, state.mimeType, state.bytes, state.length, state.speedBytesPerSecond,
                    )
                }
                is GeckoDownloadState.Finished -> {
                    activeHistoryId.getAndSet(null)?.let { id ->
                        when {
                            state.outcome is GeckoDownloadOutcome.Complete && state.recorded == true -> Unit
                            state.outcome is GeckoDownloadOutcome.Complete -> BrowserDownloads.finishGeckoTransfer(
                                id, BrowserDownloadStatus.FAILED, "Saved, but could not be added to download history.",
                            )
                            state.outcome == GeckoDownloadOutcome.Cancelled -> BrowserDownloads.finishGeckoTransfer(id, BrowserDownloadStatus.CANCELLED)
                            else -> BrowserDownloads.finishGeckoTransfer(
                                id, BrowserDownloadStatus.FAILED, "Download failed. Try again from the website.",
                            )
                        }
                    }
                    if (clientCount == 0) main.post { GeckoDownloadRuntime.dispose(this) }
                }
                GeckoDownloadState.Idle -> {
                    activeHistoryId.getAndSet(null)?.let { id ->
                        BrowserDownloads.finishGeckoTransfer(
                            id, BrowserDownloadStatus.CANCELLED, "Browser closed before download finished.",
                        )
                    }
                    if (clientCount == 0) main.post { GeckoDownloadRuntime.dispose(this) }
                }
                is GeckoDownloadState.Offer -> Unit
            }
        }

        @MainThread
        fun shutdown() {
            if (isReleased) return
            isReleased = true
            controller.close()
            worker.shutdown()
        }
    }
}
