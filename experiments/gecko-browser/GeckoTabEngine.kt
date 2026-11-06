package com.example.browser.gecko

import android.content.Context
import android.os.Looper
import androidx.annotation.MainThread
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.WebRequestError
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
    val error: GeckoPageError? = null,
) {
    val loadFailed: Boolean get() = error != null
}

/** One session per tab. Callers own tab ordering and persisted non-private metadata. */
internal class GeckoTabEngine(
    context: Context,
    val id: Int,
    privateTab: Boolean,
    desktop: Boolean,
    private val onChanged: (GeckoTabSnapshot) -> Unit,
    private val navigationPolicy: GeckoNavigationPolicy,
    private val onNavigationDecision: (GeckoNavigationDecision) -> Unit,
    private val promptDelegate: GeckoSession.PromptDelegate? = null,
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
        session.promptDelegate = promptDelegate
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                update(GeckoLoadingState.start(snapshot, url))
            }
            override fun onProgressChange(session: GeckoSession, progress: Int) {
                update(GeckoLoadingState.progress(snapshot, progress))
            }
            override fun onPageStop(session: GeckoSession, success: Boolean) {
                update(GeckoLoadingState.stop(snapshot, success))
            }
        }
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLoadRequest(session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny>? {
                val decision = navigationPolicy.decide(request.uri, snapshot.url, request.hasUserGesture)
                if (decision == GeckoNavigationDecision.Allow) return GeckoResult.allow()
                update(GeckoLoadingState.cancel(snapshot))
                if (decision != GeckoNavigationDecision.Block) onNavigationDecision(decision)
                return GeckoResult.deny()
            }
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                update(GeckoLoadingState.location(snapshot, url))
            }
            override fun onLoadError(session: GeckoSession, uri: String?, error: WebRequestError): GeckoResult<String>? {
                val kind = when (error.category) {
                    WebRequestError.ERROR_CATEGORY_NETWORK -> GeckoPageError.NETWORK
                    WebRequestError.ERROR_CATEGORY_SECURITY, WebRequestError.ERROR_CATEGORY_SAFEBROWSING -> GeckoPageError.SECURITY
                    else -> GeckoPageError.CONTENT
                }
                update(GeckoLoadingState.error(snapshot, kind))
                // Null halts the failed load; the UI must display a safe recovery state.
                // Never ignore certificate errors.
                return null
            }
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
                update(GeckoLoadingState.error(snapshot, GeckoPageError.RENDERER))
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
    @MainThread fun load(url: String) {
        checkMain(); check(!closed)
        update(GeckoLoadingState.start(snapshot, url))
        try { session.loadUri(url) } catch (error: Exception) {
            update(GeckoLoadingState.error(snapshot, GeckoPageError.CONTENT))
            throw error
        }
    }
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
