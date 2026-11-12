from prepare_gecko_build import replace_once


def augment_regressions(result):
    result = dict(result)
    # The shared HiBrowserHome now owns its persistent logo/title and saved-bookmarks state.
    # Avoid layering a second transient welcome banner into the Gecko start page.
    k = 'main/engine/GeckoBrowserShell.kt'
    result[k] = replace_once(result[k], 'val home = snapshot.url.isBlank() && secureUrl == null', 'val home = (snapshot.url.isBlank() || snapshot.url.substringBefore(\'#\') == "about:blank") && secureUrl == null', 'native blank page Home')
    k = 'main/engine/GeckoFeatureBridge.kt'
    result[k] = replace_once(result[k], '    private val clients = WeakHashMap<GeckoSession, Client>()', '''    private val clients = WeakHashMap<GeckoSession, Client>()
    private val userNavigation = WeakHashMap<GeckoSession, (String) -> Unit>()
    fun setUserNavigation(session: GeckoSession, navigate: (String) -> Unit) { userNavigation[session] = navigate }''', 'trusted navigation handler')
    result[k] = replace_once(result[k], '    fun unbind(session: GeckoSession) {', '    fun unbind(session: GeckoSession) {\n        userNavigation.remove(session)', 'release link handler')
    result[k] = replace_once(result[k], '.put("skipSpeed", site?.skipSpeed ?: 30)', '.put("popupBlock", prefs.popupBlock)\n                            .put("skipSpeed", site?.skipSpeed ?: 30)', 'popup preference')
    result[k] = replace_once(result[k], '                "reader" -> {', '''                "user-link" -> {
                    if (message.optString("page").substringBefore('#') != client.page().substringBefore('#')) return null
                    val url = message.optString("url")
                    if (prefs.popupBlock && validWebUrl(url)) sender.session?.let { userNavigation[it]?.invoke(url) }
                    return reply(JSONObject().put("ok", true))
                }
                "reader" -> {''', 'trusted same-tab link')
    k = 'main/compatibility/Patched_GeckoTabEngine.kt'
    result[k] = replace_once(result[k], '            { text -> update(snapshot.copy(readerText = text)) })', '            { text -> update(snapshot.copy(readerText = text)) })\n        GeckoFeatureBridge.setUserNavigation(session) { target -> load(target) }', 'normal-policy link loader')
    k = 'main/compatibility/Patched_GeckoBrowserChrome.kt'
    result[k] = replace_once(result[k], '    onPageTools: (() -> Unit)? = null,', '    onPageTools: (() -> Unit)? = null,\n    onPlayDownload: ((String, String?, String?) -> Unit)? = null,', 'download play callback')
    result[k] = replace_once(result[k], '        GeckoDownloadUi(services.downloads)', '        GeckoDownloadUi(services.downloads, onPlay = onPlayDownload)', 'download play action')
    k = 'main/integration/GeckoBrowserEntry.kt'
    result[k] = replace_once(result[k], '                onPageTools = { showPageTools = true },', '''                onPageTools = { showPageTools = true },
                onPlayDownload = { localUri, _, _ ->
                    // Always the app-owned local document; the host resolves partial-download state.
                    latest.onPlayDirectMedia(localUri)
                },''', 'real player action')
    # Download playback uses the local file Gecko writes; never re-fetch the response URL without the site's session.
    return result


def patch_page_skipper(script):
    script = replace_once(script, '  var _st = window.setTimeout,', '  var earlyTimers = window.__hiEarlyTimers;\n  if (earlyTimers) earlyTimers.adopt();\n  var _st = window.setTimeout,', 'adopt early timers')
    script = replace_once(script, '  function accelerateExisting() {', '  function accelerateExisting() {\n    if (earlyTimers) earlyTimers.scale(SPEED);', 'recover early countdowns')
    return replace_once(script, '  function restoreTiming() {', '  function restoreTiming() {\n    if (earlyTimers) earlyTimers.scale(1);', 'restore original timing')
