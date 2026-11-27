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
import org.mozilla.geckoview.WebResponse

/** Experimental only: not wired to the production browser or included in its build. */
internal object GeckoRuntimeHolder {
    private var runtime: GeckoRuntime? = null

    @MainThread
    fun get(context: Context): GeckoRuntime {
        check(Looper.myLooper() == Looper.getMainLooper())
        return runtime ?: GeckoRuntime.create(context.applicationContext).also { runtime = it }
    }
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
    private val openWhenCreated: Boolean = true,
    private val onPopupRequested: ((String) -> org.mozilla.geckoview.GeckoResult<GeckoSession>?)? = null,
    private val onCloseRequested: (() -> Unit)? = null,
    private val onExternalResponse: ((GeckoDownloadOffer) -> Unit)? = null,
    private val permissionDelegate: GeckoSession.PermissionDelegate? = null,
    private val onContextLink: ((String?, String?) -> Unit)? = null,
    private val allowPopupPrompt: (String, (Boolean) -> Unit) -> Unit = { _, finish -> finish(false) },
    private val approveChildNavigation: (String) -> org.mozilla.geckoview.GeckoResult<org.mozilla.geckoview.AllowOrDeny>? = { null },
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
    private val autofillPreferences = GeckoPasswordAutofill.preferences(context)
    private val autofillPreferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == GeckoPasswordAutofill.ENABLED) updateAutofill()
    }
    private var closed = false
    private var userRequestedStop = false

    init {
        checkMain()
        session.promptDelegate = object : GeckoSession.PromptDelegate by (promptDelegate ?: object : GeckoSession.PromptDelegate {}) {
            override fun onPopupPrompt(session: GeckoSession, prompt: GeckoSession.PromptDelegate.PopupPrompt): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
                val target = prompt.targetUri.orEmpty().ifEmpty { "about:blank" }
                val web = target == "about:blank" || GeckoFeatureBridge.validWebUrl(target)
                // Native user-initiated windows normally bypass this prompt.
                // Apply the live preference here; do not re-gate onNewSession
                // using a guessed gesture or an exact-URI ticket.
                if (!web || isAppPromotionLink(target, snapshot.url))
                    return GeckoResult.fromValue(prompt.confirm(AllowOrDeny.DENY))
                val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                var finished = false
                prompt.setDelegate(object : GeckoSession.PromptDelegate.PromptInstanceDelegate {
                    override fun onPromptDismiss(prompt: GeckoSession.PromptDelegate.BasePrompt) {
                        if (!finished) { finished = true; result.complete(null) }
                    }
                })
                allowPopupPrompt(target) { allow ->
                    if (!finished) {
                        finished = true
                        result.complete(if (prompt.isComplete) null else prompt.confirm(if (allow) AllowOrDeny.ALLOW else AllowOrDeny.DENY))
                    }
                }
                return result
            }
        }
        session.permissionDelegate = permissionDelegate
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                if (isAppPromotionLink(url, snapshot.url)) { userRequestedStop = true; return }
                GeckoFeatureBridge.clearDownloadClick(session)
                userRequestedStop = false
                update(GeckoLoadingState.start(snapshot, url))
            }
            override fun onProgressChange(session: GeckoSession, progress: Int) {
                update(GeckoLoadingState.progress(snapshot, progress))
            }
            override fun onSecurityChange(session: GeckoSession, securityInfo: GeckoSession.ProgressDelegate.SecurityInformation) {
                update(snapshot.copy(secure = securityInfo.isSecure && !securityInfo.isException))
            }
            override fun onPageStop(session: GeckoSession, success: Boolean) {
                update(GeckoLoadingState.stop(snapshot, success, userCancelled = userRequestedStop))
            }
        }
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLoadRequest(session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny>? {
                val newWindow = request.target == GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW
                if (isAppPromotionLink(request.uri, request.triggerUri ?: snapshot.url)) {
                    if (!newWindow) {
                        userRequestedStop = true
                        update(GeckoLoadingState.cancel(snapshot))
                    }
                    return GeckoResult.deny()
                }
                // A new blank child is needed by websites that create a window first and
                // navigate it afterwards. Its later navigations still use normal policy.
                val blankPopup = newWindow && (request.uri.isEmpty() || request.uri == "about:blank")
                val decision = if (blankPopup) GeckoNavigationDecision.Allow
                    else navigationPolicy.decide(request.uri, snapshot.url, request.hasUserGesture)
                if (decision == GeckoNavigationDecision.Allow) {
                    return if (newWindow) GeckoResult.allow() else approveChildNavigation(request.uri) ?: GeckoResult.allow()
                }
                if (!newWindow) {
                    userRequestedStop = true
                    update(GeckoLoadingState.cancel(snapshot))
                }
                if (decision != GeckoNavigationDecision.Block && decision != GeckoNavigationDecision.Ignore) onNavigationDecision(decision)
                return GeckoResult.deny()
            }
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                if (!isAppPromotionLink(url, snapshot.url)) update(GeckoLoadingState.location(snapshot, url))
            }
            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                // The host must apply popup and secure-origin policy first. Gecko, not
                // this delegate, opens the returned new session after the callback.
                if (uri.isNotEmpty() && uri != "about:blank" &&
                    (!GeckoFeatureBridge.validWebUrl(uri) || isAppPromotionLink(uri, snapshot.url))) return null
                return runCatching { onPopupRequested?.invoke(uri.ifEmpty { "about:blank" }) }.getOrNull()
            }
            override fun onLoadError(session: GeckoSession, uri: String?, error: WebRequestError): GeckoResult<String>? {
                if (isAppPromotionLink(uri, snapshot.url)) {
                    userRequestedStop = true
                    update(GeckoLoadingState.cancel(snapshot))
                    return null
                }
                val kind = when (error.category) {
                    WebRequestError.ERROR_CATEGORY_NETWORK -> GeckoPageError.NETWORK
                    WebRequestError.ERROR_CATEGORY_SECURITY, WebRequestError.ERROR_CATEGORY_SAFEBROWSING -> GeckoPageError.SECURITY
                    else -> GeckoPageError.CONTENT
                }
                update(if (userRequestedStop && kind != GeckoPageError.SECURITY) GeckoLoadingState.cancel(snapshot) else GeckoLoadingState.error(snapshot, kind))
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
            override fun onFirstContentfulPaint(session: GeckoSession) {
                if (snapshot.awaitingFirstPaint) update(snapshot.copy(awaitingFirstPaint = false))
            }
            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                update(snapshot.copy(fullscreen = fullScreen))
            }
            override fun onTitleChange(session: GeckoSession, title: String?) {
                update(snapshot.copy(title = title?.takeIf { it.isNotBlank() } ?: "New tab"))
            }
            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                // Gecko may report an unsuccessful document stop for an attachment.
                // The download controller owns the result and any HTTP error message.
                userRequestedStop = true
                if (snapshot.error != GeckoPageError.SECURITY) {
                    update(GeckoLoadingState.cancel(snapshot.copy(error = null)))
                }
                val offer = GeckoDownloadOffer(response)
                if (onExternalResponse == null) runCatching { offer.close() } else {
                    try { onExternalResponse.invoke(offer) } catch (_: Exception) { runCatching { offer.close() } }
                }
            }
            override fun onContextMenu(session: GeckoSession, screenX: Int, screenY: Int, element: GeckoSession.ContentDelegate.ContextElement) {
                // Prefer a linked address; only use image sources for image elements.
                onContextLink?.invoke(element.linkUri, element.srcUri.takeIf { element.type == GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE })
            }
            override fun onCloseRequest(session: GeckoSession) { onCloseRequested?.invoke() }
            override fun onCrash(session: GeckoSession) {
                update(GeckoLoadingState.error(snapshot, GeckoPageError.RENDERER))
            }
        }
        if (openWhenCreated) session.open(runtime)
    }

    @MainThread fun attach(view: GeckoView) {
        checkMain(); check(!closed)
        if (attachedView === view) return
        if (attachedView != null) detach()
        setAutofill(view, false)
        view.setSession(session)
        attachedView = view
        autofillPreferences.registerOnSharedPreferenceChangeListener(autofillPreferenceListener)
        updateAutofill()
        session.setActive(true)
    }
    @MainThread fun detach() {
        checkMain()
        attachedView?.let { view ->
            autofillPreferences.unregisterOnSharedPreferenceChangeListener(autofillPreferenceListener)
            setAutofill(view, false)
            view.releaseSession()
        }
        attachedView = null
        if (!closed) session.setActive(false)
    }
    @MainThread fun load(url: String) {
        checkMain(); check(!closed)
        if (isAppPromotionLink(url, snapshot.url)) return
        userRequestedStop = false
        update(GeckoLoadingState.start(snapshot, url))
        try { session.loadUri(url) } catch (error: Exception) {
            update(GeckoLoadingState.error(snapshot, GeckoPageError.CONTENT))
            throw error
        }
    }
    @MainThread fun reload() {
        checkMain()
        if (!closed) {
            if (snapshot.url.isBlank()) return
            if (snapshot.error != null || !session.isOpen) {
                load(snapshot.url)
                return
            }
            userRequestedStop = false
            update(GeckoLoadingState.start(snapshot, snapshot.url))
            session.reload()
        }
    }
    @MainThread fun stop() {
        checkMain()
        if (!closed) {
            if (snapshot.loading) {
                userRequestedStop = true
                update(GeckoLoadingState.cancel(snapshot))
            }
            session.stop()
        }
    }
    @MainThread fun back() { checkMain(); if (!closed && snapshot.canGoBack) session.goBack() }
    @MainThread fun forward() { checkMain(); if (!closed && snapshot.canGoForward) session.goForward() }
    @MainThread fun exitFullScreen() { checkMain(); if (!closed) session.exitFullScreen() }
    @MainThread fun find(query: String?, backwards: Boolean, completion: (GeckoFindResult?) -> Unit) {
        checkMain()
        if (closed || !session.isOpen) { completion(null); return }
        val finder = session.finder
        finder.displayFlags = GeckoSession.FINDER_DISPLAY_HIGHLIGHT_ALL
        finder.find(query, if (backwards) GeckoSession.FINDER_FIND_BACKWARDS else GeckoSession.FINDER_FIND_FORWARD)
            .accept({ result -> completion(result?.let { GeckoFindResult(it.found, it.current, it.total) }) }, { completion(null) })
    }
    @MainThread fun clearFind() { checkMain(); if (!closed && session.isOpen) session.finder.clear() }
    @MainThread fun purgeHistory() { checkMain(); check(!closed); if (session.isOpen) session.purgeHistory() }
    @MainThread fun setDesktop(enabled: Boolean) {
        checkMain(); if (closed) return
        session.settings.userAgentMode = if (enabled) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        session.settings.viewportMode = if (enabled) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
    }
    @MainThread override fun close() {
        checkMain(); if (closed) return
        detach(); closed = true; session.close()
    }
    private fun updateAutofill() {
        val view = attachedView ?: return
        setAutofill(view, GeckoPasswordAutofill.shouldEnable(
            autofillPreferences.getBoolean(GeckoPasswordAutofill.ENABLED, false), snapshot,
        ))
    }

    private fun setAutofill(view: GeckoView, enabled: Boolean) {
        if (!GeckoPasswordAutofill.available()) return
        if (view.autofillEnabled != enabled) {
            if (!enabled) view.context.getSystemService(android.view.autofill.AutofillManager::class.java)?.cancel()
            view.setAutofillEnabled(enabled)
        }
        view.importantForAutofill = if (enabled) android.view.View.IMPORTANT_FOR_AUTOFILL_YES
            else android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
    }

    private fun update(value: GeckoTabSnapshot) {
        if (closed) return
        val eligibilityChanged = snapshot.url != value.url || snapshot.loadFailed != value.loadFailed
        snapshot = value
        if (eligibilityChanged) updateAutofill()
        onChanged(value)
    }
    private fun checkMain() = check(Looper.myLooper() == Looper.getMainLooper())
}
