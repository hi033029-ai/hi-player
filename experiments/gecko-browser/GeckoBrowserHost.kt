package com.example.browser.gecko

import android.content.Context
import com.example.ui.screens.OrbitBrowserStore
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

/**
 * Creates the engine-backed coordinator. The host must provide consent, popup, download and
 * navigation handling; ownership of returned downloads and sessions is never silently transferred.
 * Keep this owner across UI reattachments, and close it once the browser really exits.
 */
internal fun createGeckoBrowserCoordinator(
    context: Context,
    store: OrbitBrowserStore,
    navigationPolicy: (Boolean) -> GeckoNavigationPolicy,
    onNavigationDecision: (Int, GeckoNavigationDecision) -> Unit,
    promptDelegate: GeckoSession.PromptDelegate,
    permissionDelegate: GeckoSession.PermissionDelegate,
    onPopupRequested: (Int, String) -> GeckoSession?,
    onCloseRequested: (Int) -> Unit,
    onExternalResponse: (Int, GeckoDownloadOffer) -> Unit,
): GeckoBrowserCoordinator<GeckoView> = GeckoBrowserCoordinator(GeckoOrbitTabStore(store)) { tab, changed ->
    GeckoManagedTab(
        context = context.applicationContext,
        tab = tab,
        desktop = false,
        onChanged = changed,
        navigationPolicy = navigationPolicy(tab.privateTab),
        onNavigationDecision = { decision -> onNavigationDecision(tab.id, decision) },
        promptDelegate = promptDelegate,
        onPopupRequested = { url -> onPopupRequested(tab.id, url) },
        onCloseRequested = { onCloseRequested(tab.id) },
        onExternalResponse = { offer -> onExternalResponse(tab.id, offer) },
        permissionDelegate = permissionDelegate,
    )
}
