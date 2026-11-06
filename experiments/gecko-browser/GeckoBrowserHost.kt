package com.example.browser.gecko

import android.content.Context
import com.example.ui.screens.OrbitBrowserStore
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

/** The host owns the coordinator across UI reattachments and closes it when the browser exits. */
internal fun createGeckoBrowserCoordinator(
    context: Context,
    store: OrbitBrowserStore,
    navigationPolicy: (Boolean) -> GeckoNavigationPolicy,
    onNavigationDecision: (Int, GeckoNavigationDecision) -> Unit,
    promptDelegate: GeckoSession.PromptDelegate,
    permissionDelegate: GeckoSession.PermissionDelegate,
    onCloseRequested: (Int) -> Unit,
    onExternalResponse: (Int, GeckoDownloadOffer) -> Unit,
): GeckoBrowserCoordinator<GeckoView> {
    lateinit var coordinator: GeckoBrowserCoordinator<GeckoView>
    fun createTab(tab: GeckoTabRegistry.Tab, changed: (GeckoTabSnapshot) -> Unit, open: Boolean): GeckoManagedTab =
        GeckoManagedTab(
            context = context.applicationContext,
            tab = tab,
            desktop = false,
            onChanged = changed,
            navigationPolicy = navigationPolicy(tab.privateTab),
            onNavigationDecision = { decision -> onNavigationDecision(tab.id, decision) },
            promptDelegate = promptDelegate,
            openWhenCreated = open,
            popupAllowed = { userGesture -> !store.popupBlock || userGesture },
            onPopupRequested = {
                // Gecko itself opens the newly returned session. Keep it owned by the
                // coordinator but do not attach or call loadUri before Gecko opens it.
                val child = coordinator.reservePopup(tab.id) { popup, callback -> createTab(popup, callback, false) }
                (child as? GeckoManagedTab)?.engine?.session
            },
            onCloseRequested = { onCloseRequested(tab.id) },
            onExternalResponse = { offer -> onExternalResponse(tab.id, offer) },
            permissionDelegate = permissionDelegate,
        )
    coordinator = GeckoBrowserCoordinator(GeckoOrbitTabStore(store)) { tab, changed -> createTab(tab, changed, true) }
    return coordinator
}
