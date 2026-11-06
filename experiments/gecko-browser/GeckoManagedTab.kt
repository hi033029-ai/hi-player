package com.example.browser.gecko

import android.content.Context
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.GeckoSession

/** The caller owns snapshots and must not persist private-tab data. */
internal class GeckoManagedTab(
    context: Context,
    tab: GeckoTabRegistry.Tab,
    desktop: Boolean,
    onChanged: (GeckoTabSnapshot) -> Unit,
    navigationPolicy: GeckoNavigationPolicy,
    onNavigationDecision: (GeckoNavigationDecision) -> Unit,
    promptDelegate: GeckoSession.PromptDelegate? = null,
    openWhenCreated: Boolean = true,
    onPopupRequested: ((String) -> GeckoSession?)? = null,
    onCloseRequested: (() -> Unit)? = null,
    onExternalResponse: ((GeckoDownloadOffer) -> Unit)? = null,
) : GeckoManagedSession<GeckoView> {
    val engine = GeckoTabEngine(context, tab.id, tab.privateTab, desktop, onChanged, navigationPolicy, onNavigationDecision, promptDelegate, openWhenCreated, onPopupRequested, onCloseRequested, onExternalResponse)
    override fun attach(view: GeckoView) = engine.attach(view)
    override fun detach() = engine.detach()
    override fun load(url: String) = engine.load(url)
    override fun close() = engine.close()
}
