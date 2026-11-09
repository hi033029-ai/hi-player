package com.example.browser.gecko

import android.content.Context
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.GeckoSession

/** The caller owns snapshots and must not persist private-tab data. */
internal class GeckoManagedTab(
    context: Context,
    val tab: GeckoTabRegistry.Tab,
    desktop: Boolean,
    onChanged: (GeckoTabSnapshot) -> Unit,
    navigationPolicy: GeckoNavigationPolicy,
    onNavigationDecision: (GeckoNavigationDecision) -> Unit,
    promptDelegate: GeckoSession.PromptDelegate? = null,
    openWhenCreated: Boolean = true,
    onPopupRequested: ((String) -> GeckoSession?)? = null,
    popupAllowed: (String, Boolean) -> Boolean = { _, gesture -> gesture },
    onCloseRequested: (() -> Unit)? = null,
    onExternalResponse: ((GeckoDownloadOffer) -> Unit)? = null,
    permissionDelegate: GeckoSession.PermissionDelegate? = null,
    onContextLink: ((String?, String?) -> Unit)? = null,
) : GeckoManagedSession<GeckoView>, GeckoFindable {
    val engine = GeckoTabEngine(context, tab.id, tab.privateTab, desktop, onChanged, navigationPolicy, onNavigationDecision, promptDelegate, openWhenCreated, onPopupRequested, popupAllowed, onCloseRequested, onExternalResponse, permissionDelegate, onContextLink)
    override fun attach(view: GeckoView) = engine.attach(view)
    override fun detach() = engine.detach()
    override fun load(url: String) = engine.load(url)
    override fun reload() = engine.reload()
    override fun stop() = engine.stop()
    override fun back() = engine.back()
    override fun forward() = engine.forward()
    override fun exitFullScreen() = engine.exitFullScreen()
    override fun purgeHistory() = engine.purgeHistory()
    override fun find(query: String?, backwards: Boolean, completion: (GeckoFindResult?) -> Unit) = engine.find(query, backwards, completion)
    override fun clearFind() = engine.clearFind()
    override fun setDesktop(enabled: Boolean) = engine.setDesktop(enabled)
    override fun close() = engine.close()
}
