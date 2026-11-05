package com.example.browser.gecko

import android.content.Context
import android.os.Looper
import androidx.annotation.MainThread
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView

/** Experimental only: not wired to the production browser or included in its build. */
internal object GeckoRuntimeHolder {
    private var runtime: GeckoRuntime? = null

    @MainThread
    fun get(context: Context): GeckoRuntime {
        check(Looper.myLooper() == Looper.getMainLooper())
        return runtime ?: GeckoRuntime.create(context.applicationContext).also { runtime = it }
    }
}

internal data class GeckoTabSnapshot(
    val id: Int,
    val privateTab: Boolean,
    val url: String = "",
    val title: String = "New tab",
    val loading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val loadFailed: Boolean = false,
)

/** One session per tab. Callers own tab ordering and persisted non-private metadata. */
internal class GeckoTabEngine(
    context: Context,
    val id: Int,
    privateTab: Boolean,
    desktop: Boolean,
    private val onChanged: (GeckoTabSnapshot) -> Unit,
) : AutoCloseable {
    private val runtime = GeckoRuntimeHolder.get(context)
    private val settings = GeckoSessionSettings.Builder()
        .usePrivateMode(privateTab)
        .userAgentMode(if (desktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
        .viewportMode(if (desktop) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
        .suspendMediaWhenInactive(true)
        .build()
    val session = GeckoSession(settings)
    var snapshot = GeckoTabSnapshot(id, privateTab)
        private set
    private var attachedView: GeckoView? = null
    private var closed = false

    init {
        checkMain()
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                update(snapshot.copy(url = url, loading = true, progress = 0, loadFailed = false))
            }
            override fun onProgressChange(session: GeckoSession, progress: Int) {
                update(snapshot.copy(progress = progress.coerceIn(0, 100)))
            }
            override fun onPageStop(session: GeckoSession, success: Boolean) {
                update(snapshot.copy(loading = false, progress = if (success) 100 else snapshot.progress, loadFailed = !success))
            }
        }
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                update(snapshot.copy(canGoBack = canGoBack))
            }
            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                update(snapshot.copy(canGoForward = canGoForward))
            }
        }
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                update(snapshot.copy(title = title?.takeIf { it.isNotBlank() } ?: "New tab"))
            }
            override fun onCrash(session: GeckoSession) {
                update(snapshot.copy(loading = false, loadFailed = true))
            }
        }
        session.open(runtime)
    }

    @MainThread fun attach(view: GeckoView) {
        checkMain(); check(!closed)
        if (attachedView === view) return
        attachedView?.releaseSession()
        view.setSession(session)
        attachedView = view
        session.setActive(true)
    }
    @MainThread fun detach() {
        checkMain()
        attachedView?.releaseSession()
        attachedView = null
        if (!closed) session.setActive(false)
    }
    @MainThread fun load(url: String) { checkMain(); check(!closed); session.loadUri(url) }
    @MainThread fun reload() { checkMain(); if (!closed) session.reload() }
    @MainThread fun stop() { checkMain(); if (!closed) session.stop() }
    @MainThread fun back() { checkMain(); if (!closed && snapshot.canGoBack) session.goBack() }
    @MainThread fun forward() { checkMain(); if (!closed && snapshot.canGoForward) session.goForward() }
    @MainThread fun setDesktop(enabled: Boolean) {
        checkMain(); if (closed) return
        session.settings.userAgentMode = if (enabled) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        session.settings.viewportMode = if (enabled) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
    }
    @MainThread override fun close() {
        checkMain(); if (closed) return
        detach(); closed = true; session.close()
    }
    private fun update(value: GeckoTabSnapshot) { if (!closed) { snapshot = value; onChanged(value) } }
    private fun checkMain() = check(Looper.myLooper() == Looper.getMainLooper())
}
