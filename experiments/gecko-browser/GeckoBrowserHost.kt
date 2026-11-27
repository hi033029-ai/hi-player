package com.example.browser.gecko

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.ui.screens.OrbitBrowserStore
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

/** The host owns the coordinator across UI reattachments and closes it when the browser exits. */
internal fun createGeckoBrowserCoordinator(
    context: Context,
    store: OrbitBrowserStore,
    navigationPolicy: (Int, Boolean) -> GeckoNavigationPolicy,
    onNavigationDecision: (Int, GeckoNavigationDecision) -> Unit,
    promptDelegate: GeckoSession.PromptDelegate,
    permissionDelegate: GeckoSession.PermissionDelegate,
    onCloseRequested: (Int) -> Unit,
    onExternalResponse: (Int, GeckoDownloadOffer) -> Unit,
    onContextLink: (Int, String?, String?) -> Unit,
    requestPopupReview: (Int, String, String, (Boolean) -> Unit) -> Unit = { _, _, _, finish -> finish(false) },
): GeckoBrowserCoordinator<GeckoView> {
    lateinit var coordinator: GeckoBrowserCoordinator<GeckoView>
    fun advertising(url: String, openerPage: String): Boolean = GeckoFeatureBridge.validWebUrl(url) &&
        !com.example.auth.SecureWebSessions.isGoogleAuthentication(url) &&
        com.example.ui.screens.OrbitBrowserFilterEngine.shouldBlock(
            android.net.Uri.parse(url), store.adBlock, android.net.Uri.parse(openerPage).host, true)
    fun createTab(tab: GeckoTabRegistry.Tab, changed: (GeckoTabSnapshot) -> Unit, open: Boolean,
                  openerPage: String = "", approvedInitialTarget: String? = null): GeckoManagedTab {
        lateinit var owned: GeckoManagedTab
        val approvedPrompts = GeckoPopupGate { android.os.SystemClock.elapsedRealtime() }
        var initialApproval = approvedInitialTarget
        owned = GeckoManagedTab(
            context = context.applicationContext,
            tab = tab,
            desktop = false,
            onChanged = changed,
            navigationPolicy = navigationPolicy(tab.id, tab.privateTab).withPopupOpener(openerPage),
            onNavigationDecision = { decision ->
                // Defer until construction finishes and reject callbacks from replaced/closed sessions.
                Handler(Looper.getMainLooper()).post {
                    if (coordinator.controller.isSession(tab.id, owned)) {
                        val active = coordinator.activeId
                        if (tab.id == active) {
                            onNavigationDecision(tab.id, decision)
                        } else if (decision is GeckoNavigationDecision.SecureSignIn &&
                            coordinator.pendingPopupOpener(tab.id) == active
                        ) {
                            onNavigationDecision(active, decision.copy(userGesture = false))
                            coordinator.closeTab(tab.id)
                        }
                    }
                }
            },
            promptDelegate = promptDelegate,
            openWhenCreated = open,
            allowPopupPrompt = { target, finish ->
                val page = coordinator.snapshots[tab.id]?.url.orEmpty()
                val flagged = advertising(target, page)
                val tapped = !flagged && GeckoFeatureBridge.consumeDownloadClick(owned.engine.session, page)
                if (!GeckoPopupPolicy.requiresReview(store.popupBlock, tapped, flagged)) {
                    finish(true)
                } else {
                    requestPopupReview(tab.id, target, if (flagged) "The ad filter flagged this destination." else "The website requested a new tab without a confirmed tap.") { approved ->
                        val current = coordinator.controller.isSession(tab.id, owned) && coordinator.snapshots[tab.id]?.url == page
                        if (approved && current && flagged) approvedPrompts.permit(target)
                        finish(approved && current)
                    }
                }
            },
            onPopupRequested = { target ->
                val page = coordinator.snapshots[tab.id]?.url.orEmpty()
                val result = GeckoResult<GeckoSession>()
                fun finish(approved: Boolean, approvedTarget: String?) {
                    if (!approved || !coordinator.controller.isSession(tab.id, owned) ||
                        coordinator.snapshots[tab.id]?.url != page) {
                        result.complete(null)
                        return
                    }
                    val child = coordinator.reservePopup(tab.id) { popup, callback ->
                        createTab(popup, callback, false, page, approvedTarget)
                    } as? GeckoManagedTab
                    if (child != null) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (coordinator.isPopupSession(child.tab.id, child) && !child.engine.session.isOpen) coordinator.closeTab(child.tab.id)
                        }, 30_000)
                    }
                    result.complete(child?.engine?.session?.takeUnless { it === owned.engine.session || it.isOpen })
                }
                val flagged = advertising(target, page)
                if (!flagged) finish(true, null)
                else if (approvedPrompts.consume(target)) finish(true, target)
                else requestPopupReview(tab.id, target, "The ad filter flagged this new-tab destination.") { approved ->
                    finish(approved, target.takeIf { approved })
                }
                result
            },
            approveChildNavigation = review@{ target ->
                val opener = coordinator.pendingPopupOpener(tab.id) ?: return@review null
                if (target == "about:blank" || target.isEmpty()) return@review null
                val alreadyApproved = initialApproval == target
                initialApproval = null
                if (alreadyApproved || !advertising(target, openerPage)) return@review null
                val result = GeckoResult<AllowOrDeny>()
                requestPopupReview(opener, target, "The new tab redirected to a destination flagged by the ad filter.") { approved ->
                    val current = coordinator.controller.isSession(tab.id, owned) && coordinator.pendingPopupOpener(tab.id) == opener
                    result.complete(if (approved && current) AllowOrDeny.ALLOW else AllowOrDeny.DENY)
                    if (!approved && current) Handler(Looper.getMainLooper()).post {
                        if (coordinator.pendingPopupOpener(tab.id) == opener) coordinator.closeTab(tab.id)
                    }
                }
                result
            },
            onCloseRequested = {
                if (coordinator.isPopup(tab.id)) coordinator.closeTab(tab.id) else onCloseRequested(tab.id)
            },
            onExternalResponse = { offer ->
                val opener = coordinator.pendingPopupOpener(tab.id)
                val source = if (opener == coordinator.activeId) coordinator.controller.activeSession() as? GeckoManagedTab else owned
                source?.let { GeckoFeatureBridge.mediaCommand(it.engine.session, "generate-finished") }
                onExternalResponse(tab.id, offer)
            },
            permissionDelegate = permissionDelegate,
            onContextLink = { link, image ->
                Handler(Looper.getMainLooper()).post {
                    if (coordinator.controller.isSession(tab.id, owned)) onContextLink(tab.id, link, image)
                }
            },
        )
        return owned
    }
    coordinator = GeckoBrowserCoordinator(GeckoOrbitTabStore(store)) { tab, changed -> createTab(tab, changed, true) }
    return coordinator
}
