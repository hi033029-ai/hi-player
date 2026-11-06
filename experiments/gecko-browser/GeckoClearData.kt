package com.example.browser.gecko

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.auth.SecureWebSessions
import com.example.ui.screens.HiSiteIcons
import java.util.concurrent.Executor
import org.mozilla.geckoview.StorageController

internal data class GeckoClearDataSelection(val history: Boolean, val cookies: Boolean, val cache: Boolean)
internal enum class GeckoClearDataState { IDLE, CLEARING, COMPLETE, FAILED }

/** Coordinates independent operations and reports partial failure without claiming full success. */
internal class GeckoClearDataController(
    private val purgeTabs: () -> Boolean,
    private val clearHistory: ((Boolean) -> Unit) -> Unit,
    private val clearIcons: ((Boolean) -> Unit) -> Unit,
    private val clearRouting: ((Boolean) -> Unit) -> Unit,
    private val clearStorage: (Long, (Boolean) -> Unit) -> Unit,
) : AutoCloseable {
    var state by mutableStateOf(GeckoClearDataState.IDLE)
        private set
    private var closed = false
    private var generation = 0

    @MainThread fun clear(selection: GeckoClearDataSelection): Boolean {
        if (closed || state == GeckoClearDataState.CLEARING || !(selection.history || selection.cookies || selection.cache)) return false
        val jobs = mutableListOf<((Boolean) -> Unit) -> Unit>()
        if (selection.history) {
            jobs += { done -> done(purgeTabs()) }
            jobs += clearHistory
            jobs += clearIcons
        }
        if (selection.cookies) jobs += clearRouting
        var flags = 0L
        if (selection.cookies) flags = flags or StorageController.ClearFlags.COOKIES or StorageController.ClearFlags.DOM_STORAGES or StorageController.ClearFlags.AUTH_SESSIONS
        if (selection.cache) flags = flags or StorageController.ClearFlags.ALL_CACHES
        if (flags != 0L) jobs += { done -> clearStorage(flags, done) }
        state = GeckoClearDataState.CLEARING
        val request = ++generation
        var remaining = jobs.size
        var allSucceeded = true
        jobs.forEach { job ->
            var completed = false
            val done: (Boolean) -> Unit = done@{ success ->
                if (closed || request != generation || completed) return@done
                completed = true
                allSucceeded = allSucceeded && success
                if (--remaining == 0) state = if (allSucceeded) GeckoClearDataState.COMPLETE else GeckoClearDataState.FAILED
            }
            try { job(done) } catch (_: Exception) { done(false) }
        }
        return true
    }

    @MainThread fun dismissResult() {
        if (state == GeckoClearDataState.COMPLETE || state == GeckoClearDataState.FAILED) state = GeckoClearDataState.IDLE
    }
    @MainThread override fun close() { closed = true; generation++ }
}

internal fun createGeckoClearDataController(
    context: Context,
    coordinator: GeckoBrowserCoordinator<*>,
    worker: Executor,
    clearPrivateRouting: () -> Unit = {},
): GeckoClearDataController {
    val appContext = context.applicationContext
    val main = Handler(Looper.getMainLooper())
    return GeckoClearDataController(
        purgeTabs = { coordinator.controller.purgeHistory() },
        clearHistory = coordinator::clearHistory,
        clearIcons = { completion -> HiSiteIcons.clearSavedAndWait(appContext, completion) },
        clearRouting = { completion -> clearPrivateRouting(); worker.execute {
            val success = runCatching { SecureWebSessions.clearRoutingAndWait(appContext) }.getOrDefault(false)
            main.post { completion(success) }
        } },
        clearStorage = { flags, completion ->
            GeckoRuntimeHolder.get(appContext).storageController.clearData(flags).accept(
                { completion(true) }, { completion(false) },
            )
        },
    )
}
