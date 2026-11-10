"""Source-only responsiveness and helper-delivery enhancements for Gecko."""
from prepare_gecko_build import replace_once


def augment_advanced(result):
    result = dict(result)
    key = 'main/compatibility/Patched_GeckoTabController.kt'
    result[key] = replace_once(result[key], '    private val sessions = mutableMapOf<Int, GeckoManagedSession<View>>()', '    private val sessions = mutableMapOf<Int, GeckoManagedSession<View>>()\n    private val deferredUrls = mutableMapOf<Int, String>()', 'deferred restored-page requests')
    result[key] = replace_once(result[key], '                    if (state.url.isNotBlank()) session.load(state.url)', '                    if (state.url.isNotBlank()) {\n                        if (tab.id == activeId) session.load(state.url) else deferredUrls[tab.id] = state.url\n                    }', 'load active restored page only')
    result[key] = replace_once(result[key], '        registry.select(id)', '        registry.select(id)\n        deferredUrls.remove(id)?.let { sessions.getValue(id).load(it) }', 'load deferred page on selection')
    result[key] = replace_once(result[key], '        val session = sessions[id] ?: return false', '        val session = sessions[id] ?: return false\n        deferredUrls.remove(id)', 'discard closed restored page')
    result[key] = replace_once(result[key], '        if (activeId !in sessions) sessions[activeId] = newSession(tabs.single { it.id == activeId })', '        if (activeId !in sessions) sessions[activeId] = newSession(tabs.single { it.id == activeId })\n        if (wasActive) deferredUrls.remove(activeId)?.let { sessions.getValue(activeId).load(it) }', 'load selected successor only')
    result[key] = replace_once(result[key], '        val id = activeId\n        val tab = tabs.single', '        val id = activeId\n        deferredUrls.remove(id)\n        val tab = tabs.single', 'discard deferred page on Home')
    result[key] = replace_once(result[key], '        sessions.clear()\n        closed = true', '        sessions.clear()\n        deferredUrls.clear()\n        closed = true', 'clear deferred URLs on exit')
    key = 'main/engine/GeckoFeatureBridge.kt'
    result[key] = replace_once(result[key], '    private val ports = WeakHashMap<GeckoSession, WebExtension.Port>()', '    private val ports = WeakHashMap<GeckoSession, WebExtension.Port>()\n    private val helperStates = androidx.compose.runtime.mutableStateMapOf<GeckoSession, Boolean>()\n    fun helperStatus(session: GeckoSession): Boolean? = helperStates[session]\n    fun pageStarted(session: GeckoSession) { helperStates.remove(session) }', 'per-document helper delivery state')
    result[key] = replace_once(result[key], '    fun unbind(session: GeckoSession) {', '    fun unbind(session: GeckoSession) {\n        helperStates.remove(session)', 'clear helper state on tab close')
    result[key] = replace_once(result[key], '            when (message.optString("kind")) {', '            when (message.optString("kind")) {\n                "helper-status" -> {\n                    if (message.optString("page").substringBefore(\'#\') != client.page().substringBefore(\'#\')) return null\n                    sender.session?.let { helperStates[it] = message.optBoolean("ok", false) }\n                    return reply(JSONObject().put("ok", true))\n                }', 'current-page helper delivery result')
    key = 'main/compatibility/Patched_GeckoTabEngine.kt'
    result[key] = replace_once(result[key], '            override fun onPageStart(session: GeckoSession, url: String) {', '            override fun onPageStart(session: GeckoSession, url: String) {\n                GeckoFeatureBridge.pageStarted(session)', 'reset helper feedback for each document')
    key = 'main/engine/GeckoPageActions.kt'
    result[key] = replace_once(result[key], 'import androidx.compose.ui.unit.dp', 'import androidx.compose.ui.unit.dp\nimport kotlinx.coroutines.CancellationException\nimport kotlinx.coroutines.Dispatchers\nimport kotlinx.coroutines.launch\nimport kotlinx.coroutines.withContext', 'asynchronous site-setting imports')
    result[key] = replace_once(result[key], '    val context = LocalContext.current', '    val context = LocalContext.current\n    val scope = rememberCoroutineScope()\n    var savingSite by remember { mutableStateOf(false) }', 'site-setting pending state')
    result[key] = replace_once(result[key], '            Text("Page tools", style = MaterialTheme.typography.titleLarge)', '            Text("Page tools", style = MaterialTheme.typography.titleLarge)\n            if (!BuildConfig.PLAY_STORE_BUILD && (blocking || helper || youtubeAds)) {\n                val delivered = tab?.engine?.session?.let { GeckoFeatureBridge.helperStatus(it) }\n                Text(when (delivered) {\n                    true -> "Helper scripts delivered. Compatibility varies by page."\n                    false -> "Page helpers could not be applied. Reload or turn them off for this page."\n                    null -> "Helper delivery has not been confirmed for this document."\n                }, style = MaterialTheme.typography.bodySmall)\n            }', 'truthful helper delivery feedback')
    result[key] = replace_once(result[key], 'Switch(checked = sitePaused, enabled = pageHost != null, onCheckedChange = { paused ->', 'Switch(checked = sitePaused, enabled = pageHost != null && !savingSite, onCheckedChange = { paused ->', 'prevent duplicate site-setting writes')
    result[key] = replace_once(result[key], '                    if (host != null) {', '                    if (host != null && !savingSite) {', 'guard site-setting submissions')
    result[key] = replace_once(result[key], '''                        runCatching { BrowserAdvancedRules.save(context, rules.copy(sites = sites)) }
                            .onSuccess { sitePaused = paused }
                            .onFailure { notice = "Could not save this site's protection setting." }''', '''                        savingSite = true
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { BrowserAdvancedRules.save(context.applicationContext, rules.copy(sites = sites)) }
                                sitePaused = paused
                            } catch (e: CancellationException) { throw e
                            } catch (_: Exception) { notice = "Could not save this site's protection setting."
                            } finally { savingSite = false }
                        }''', 'save site protection off the UI thread')
    return result
