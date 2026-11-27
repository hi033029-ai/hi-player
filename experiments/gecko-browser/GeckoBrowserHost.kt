package com.example.browser.gecko

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.ui.screens.OrbitBrowserStore
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
): GeckoBrowserCoordinator<GeckoView> {
    lateinit var coordinator: GeckoBrowserCoordinator<GeckoView>
    fun advertising(url: String, openerPage: String): Boolean = GeckoFeatureBridge.validWebUrl(url) &&
        !com.example.auth.SecureWebSessions.isGoogleAuthentication(url) &&
        com.example.ui.screens.OrbitBrowserFilterEngine.shouldBlock(
            android.net.Uri.parse(url), store.adBlock, android.net.Uri.parse(openerPage).host, true)
    fun createTab(tab: GeckoTabRegistry.Tab, changed: (GeckoTabSnapshot) -> Unit, open: Boolean, openerPage: String = ""): GeckoManagedTab {
        lateinit var owned: GeckoManagedTab
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
            allowPopupPrompt = { target ->
                val page = coordinator.snapshots[tab.id]?.url.orEmpty()
                GeckoPopupPolicy.allowsPrompt(store.popupBlock, advertising(target, page)) {
                    GeckoFeatureBridge.consumeDownloadClick(owned.engine.session, page)
                }
            },
            onPopupRequested = popup@{ target ->
                // Gecko itself opens the newly returned session. Keep it owned by the
                // coordinator but do not attach or call loadUri before Gecko opens it.
                val openerPage = coordinator.snapshots[tab.id]?.url.orEmpty()
                fun retry() {
                    if (coordinator.activeId == tab.id)
                        GeckoFeatureBridge.mediaCommand(owned.engine.session, "retry-generate")
                }
                if (advertising(target, openerPage)) {
                    retry()
                    return@popup null
                }
                val child = coordinator.reservePopup(tab.id) { popup, callback ->
                    var discarded = false
                    createTab(popup, { state ->
                        if (!discarded && coordinator.pendingPopupOpener(popup.id) == tab.id && advertising(state.url, openerPage)) {
                            discarded = true
                            Handler(Looper.getMainLooper()).post {
                                if (coordinator.pendingPopupOpener(popup.id) == tab.id) {
                                    coordinator.closeTab(popup.id)
                                    retry()
                                }
                            }
                        } else if (!discarded) callback(state)
                    }, false, openerPage)
                } as? GeckoManagedTab
                if (child != null) {
                    // Gecko opens the session after the delegate returns; discard abandoned
                    // unopened reservations instead of consuming one of the tab slots forever.
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (coordinator.isPopupSession(child.tab.id, child) && !child.engine.session.isOpen) coordinator.closeTab(child.tab.id)
                    }, 30_000)
                }
                child?.engine?.session
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
