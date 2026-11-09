from prepare_gecko_build import replace_once


def augment(result):
    result = dict(result)
    key = 'main/compatibility/Patched_GeckoTabEngine.kt'
    t = result[key]
    t = replace_once(t, '    private var closed = false', '    private var closed = false\n    private var loadGeneration = 0', 'engine load generation')
    t = replace_once(t, '        if (openWhenCreated) session.open(runtime)', '''        if (openWhenCreated) session.open(runtime)
        GeckoFeatureBridge.bind(context, runtime, session, { snapshot.url },
            { url, mime, title -> update(snapshot.copy(mediaUrl = url, mediaMime = mime, mediaTitle = title)) },
            { text -> update(snapshot.copy(readerText = text)) })''', 'feature binding')
    t = t.replace('GeckoLoadingState.start(snapshot, url)', 'GeckoLoadingState.start(snapshot, url).copy(mediaUrl = null, mediaMime = null, mediaTitle = null, readerText = null)')
    old = '''        try { session.loadUri(url) } catch (error: Exception) {
            update(GeckoLoadingState.error(snapshot, GeckoPageError.CONTENT))
            throw error
        }'''
    new = '''        val generation = ++loadGeneration
        GeckoFeatureBridge.afterReady(session) { ready ->
            if (!closed && generation == loadGeneration) {
                if (!ready) update(GeckoLoadingState.error(snapshot, GeckoPageError.PROTECTION))
                else try {
                    if (!session.isOpen) session.open(runtime)
                    session.loadUri(url)
                } catch (_: Exception) { update(GeckoLoadingState.error(snapshot, GeckoPageError.CONTENT)) }
            }
        }'''
    t = replace_once(t, old, new, 'protected load')
    t = replace_once(t, '@MainThread fun reload() { checkMain(); if (!closed) session.reload() }', '@MainThread fun reload() { checkMain(); if (!closed) { if (snapshot.error != null || !session.isOpen) load(snapshot.url) else session.reload() } }', 'recoverable reload')
    t = replace_once(t, '@MainThread fun stop() { checkMain(); if (!closed) session.stop() }', '@MainThread fun stop() { checkMain(); if (!closed) { loadGeneration++; GeckoFeatureBridge.cancel(session); if (session.isOpen) session.stop(); update(GeckoLoadingState.cancel(snapshot)) } }', 'cancel pending load')
    t = replace_once(t, '        detach(); closed = true; popupGate.clear(); session.close()', '        loadGeneration++; GeckoFeatureBridge.unbind(session)\n        detach(); closed = true; popupGate.clear(); session.close()', 'bridge lifetime')
    t = replace_once(t, '                // Prefer a linked address; only use image sources for image elements.', '''                if (element.type == GeckoSession.ContentDelegate.ContextElement.TYPE_VIDEO || element.type == GeckoSession.ContentDelegate.ContextElement.TYPE_AUDIO) {
                    element.srcUri?.takeIf(GeckoFeatureBridge::validWebUrl)?.let { update(snapshot.copy(mediaUrl = it, mediaMime = null, mediaTitle = element.title)) }
                }
                // Prefer a linked address; only use image sources for image elements.''', 'native media context action')
    t = replace_once(t, '    @MainThread fun load(url: String)', '    @MainThread fun clearReader() { update(snapshot.copy(readerText = null)) }\n    @MainThread fun load(url: String)', 'reader state reset')
    result[key] = t
    key = 'main/compatibility/Patched_GeckoTabController.kt'
    result[key] = replace_once(result[key], '    fun load(url: String) { check(!closed);', '    fun activeSession(): GeckoManagedSession<View>? = if (closed) null else sessions[activeId]\n    fun load(url: String) { check(!closed);', 'active session access')
    key = 'main/engine/GeckoBrowserShell.kt'
    result[key] = result[key].replace('adBlockEnabled = false,', 'adBlockEnabled = GeckoFeatureBridge.ready && store.adBlock,').replace('// Filtering is not connected to Gecko yet; do not claim it is enabled.', '// Advertise protection only after the local bridge is actually ready.')
    result[key] = replace_once(result[key], '"Check the address or connection and try again."', 'if (snapshot.error == GeckoPageError.PROTECTION) "Local browser protection could not start. Retry or use the original browser." else "Check the address or connection and try again."', 'protection recovery message')
    key = 'main/integration/GeckoBrowserEntry.kt'
    t = result[key]
    t = replace_once(t, '            GeckoBrowserChrome(', '            if (arguments.isInPictureInPicture) GeckoBrowserSurface(coordinator.controller, Modifier.fillMaxSize()) else GeckoBrowserChrome(', 'PiP content-only rendering')
    t = t.replace('coordinator.controller.setHostActive(latest.isInPictureInPicture)', 'coordinator.controller.setHostActive(activity.isInPictureInPictureMode || latest.isInPictureInPicture)')
    result[key] = t
    return result
