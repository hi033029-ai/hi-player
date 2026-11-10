from prepare_gecko_build import replace_once


def augment_regressions(result):
    result = dict(result)
    k = 'main/application/com/example/ui/screens/HiBrowserChrome.kt'
    result[k] = replace_once(result[k], 'import androidx.compose.foundation.layout.Arrangement\nimport androidx.compose.foundation.layout.Box\n', 'import androidx.compose.foundation.layout.Arrangement\nimport androidx.compose.foundation.layout.heightIn\nimport androidx.compose.foundation.layout.Box\n', 'home sizing')
    result[k] = replace_once(result[k], '    bottomInset: androidx.compose.ui.unit.Dp = 0.dp,\n) {', '    bottomInset: androidx.compose.ui.unit.Dp = 0.dp,\n    inlineWelcome: Boolean = false,\n) {', 'inline welcome')
    result[k] = replace_once(result[k], '            Spacer(Modifier.height(96.dp))', '''            if (inlineWelcome) Box(Modifier.fillMaxWidth().heightIn(min = 180.dp).padding(top = 24.dp), contentAlignment = Alignment.Center) {
                if (showWelcome) HiBrowserWelcome(isPrivate = isPrivate)
                else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    HiPlayerLogoBadge(size = 64.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(if (isPrivate) "Private browsing" else "Hi Browser", color = palette.textPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
            } else Spacer(Modifier.height(96.dp))''', 'permanent browser brand')
    result[k] = replace_once(result[k], '            visible = showWelcome,', '            visible = showWelcome && !inlineWelcome,', 'nonblocking welcome')
    k = 'main/engine/GeckoBrowserShell.kt'
    result[k] = replace_once(result[k], 'val home = snapshot.url.isBlank() && secureUrl == null', 'val home = (snapshot.url.isBlank() || snapshot.url.substringBefore(\'#\') == "about:blank") && secureUrl == null', 'native blank page Home')
    result[k] = replace_once(result[k], '                bottomInset = 72.dp,', '                bottomInset = 72.dp,\n                inlineWelcome = true,', 'welcome and shortcuts')
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
                onPlayDownload = { url, mime, title ->
                    val play = latest.onPlayBrowserVideo
                    if (play != null) play(url, mime, title) else latest.onPlayDirectMedia(url)
                },''', 'real player action')
    k = 'main/engine/GeckoDownloadController.kt'
    result[k] = replace_once(result[k], 'val recordInHistory: Boolean = true) : GeckoDownloadState', 'val recordInHistory: Boolean = true, val playUrl: String? = null) : GeckoDownloadState', 'optional sanitized media address')
    result[k] = replace_once(result[k], 'GeckoDownloadState.Offer(name, source.contentLength, source.mimeType, recordInHistory)', 'GeckoDownloadState.Offer(name, source.contentLength, source.mimeType, recordInHistory, if (recordInHistory) source.responseUrl?.let(GeckoTabPersistence::safeUrl)?.takeIf { it.isNotBlank() } else null)', 'private media remains private')
    return result


def patch_page_skipper(script):
    script = replace_once(script, '  var _st = window.setTimeout,', '  var earlyTimers = window.__hiEarlyTimers;\n  if (earlyTimers) earlyTimers.adopt();\n  var _st = window.setTimeout,', 'adopt early timers')
    script = replace_once(script, '  function accelerateExisting() {', '  function accelerateExisting() {\n    if (earlyTimers) earlyTimers.scale(SPEED);', 'recover early countdowns')
    return replace_once(script, '  function restoreTiming() {', '  function restoreTiming() {\n    if (earlyTimers) earlyTimers.scale(1);', 'restore original timing')
