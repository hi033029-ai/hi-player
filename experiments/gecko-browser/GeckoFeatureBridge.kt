package com.example.browser.gecko

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.BuildConfig
import com.example.ui.screens.BrowserAdvancedRules
import com.example.ui.screens.OrbitBrowserFilterEngine
import com.example.ui.screens.OrbitBrowserStore
import java.util.WeakHashMap
import java.util.concurrent.Executors
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension

/** First-party, APK-bundled bridge. No cookies, headers, history or URLs leave the process. */
internal object GeckoFeatureBridge {
    const val EXTENSION_ID = "hi-browser@hi-player.local"
    private const val APP = "hi_player"
    private data class Client(val page: () -> String, val media: (String, String?, String?) -> Unit, val reader: (String) -> Unit)
    private val clients = WeakHashMap<GeckoSession, Client>()
    private val ports = WeakHashMap<GeckoSession, WebExtension.Port>()
    private val waiting = WeakHashMap<GeckoSession, (Boolean) -> Unit>()
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "gecko-filters").apply { isDaemon = true } }
    private var runtime: GeckoRuntime? = null
    private var context: Context? = null
    private var store: OrbitBrowserStore? = null
    private var extension: WebExtension? = null
    private var installing = false
    private var generation = 0
    var ready by mutableStateOf(false)
        private set

    fun validWebUrl(value: String): Boolean = runCatching {
        if (value.length !in 1..8192) return@runCatching false
        val uri = java.net.URI(value)
        uri.scheme?.lowercase() in setOf("https", "http") && !uri.host.isNullOrBlank() &&
            uri.userInfo == null && (uri.port == -1 || uri.port in 1..65535)
    }.getOrDefault(false)

    fun bind(context: Context, runtime: GeckoRuntime, session: GeckoSession,
             page: () -> String, media: (String, String?, String?) -> Unit, reader: (String) -> Unit) {
        check(Looper.myLooper() == Looper.getMainLooper())
        this.context = context.applicationContext
        this.runtime = runtime
        if (store == null) store = OrbitBrowserStore(context.applicationContext)
        clients[session] = Client(page, media, reader)
        extension?.let { session.webExtensionController.setMessageDelegate(it, delegate, APP) }
        ensure()
    }
    fun unbind(session: GeckoSession) {
        ports.remove(session)?.disconnect()
        clients.remove(session)
        waiting.remove(session)
        extension?.let { session.webExtensionController.setMessageDelegate(it, null, APP) }
    }
    fun requestReader(session: GeckoSession): Boolean {
        val port = ports[session] ?: return false
        return runCatching { port.postMessage(JSONObject().put("command", "reader")); true }.getOrDefault(false)
    }
    fun cancel(session: GeckoSession) { waiting.remove(session) }
    fun afterReady(session: GeckoSession, complete: (Boolean) -> Unit) {
        if (ready) { complete(true); return }
        waiting[session] = complete
        ensure()
    }
    private fun ensure() {
        if (ready || installing) return
        val app = context ?: return
        val engine = runtime ?: return
        installing = true
        val token = ++generation
        main.postDelayed({ if (token == generation && installing) finish(token, null) }, 15_000)
        worker.execute {
            val loaded = runCatching { OrbitBrowserFilterEngine.load(app); true }.getOrDefault(false)
            main.post {
                if (token != generation || !installing) return@post
                if (!loaded) { finish(token, null); return@post }
                engine.webExtensionController.ensureBuiltIn("resource://android/assets/hi-gecko/", EXTENSION_ID)
                    .accept({ installed ->
                        if (installed == null) finish(token, null)
                        else engine.webExtensionController.setAllowedInPrivateBrowsing(installed, true)
                            .accept({ enabled -> finish(token, enabled) }, { finish(token, null) })
                    }, { finish(token, null) })
            }
        }
    }
    private fun finish(token: Int, value: WebExtension?) {
        if (token != generation || !installing) return
        installing = false
        ready = value != null
        extension = value
        if (value != null) {
            value.setMessageDelegate(delegate, APP)
            clients.keys.toList().forEach { it.webExtensionController.setMessageDelegate(value, delegate, APP) }
        }
        val callbacks = waiting.values.toList()
        waiting.clear()
        callbacks.forEach { it(ready) }
    }
    // GeckoView's native-message callback accepts primitive replies; returning a JSONObject
    // produces "Invalid event data for callback" and can leave blocking webRequest calls pending.
    private fun reply(value: JSONObject): GeckoResult<Any> = GeckoResult.fromValue<Any>(value.toString())
    private val delegate = object : WebExtension.MessageDelegate {
        override fun onConnect(port: WebExtension.Port) {
            val sender = port.sender
            val session = sender.session ?: return
            if (sender.webExtension.id != EXTENSION_ID || sender.environmentType != WebExtension.MessageSender.ENV_TYPE_CONTENT_SCRIPT || !clients.containsKey(session)) { port.disconnect(); return }
            ports.put(session, port)?.disconnect()
            port.setDelegate(object : WebExtension.PortDelegate {
                override fun onPortMessage(message: Any, source: WebExtension.Port) {
                    if (ports[session] === source) onMessage(APP, message, source.sender)
                }
                override fun onDisconnect(source: WebExtension.Port) {
                    if (ports[session] === source) { ports.remove(session); clients[session]?.reader?.invoke("") }
                }
            })
        }
        override fun onMessage(nativeApp: String, message: Any, sender: WebExtension.MessageSender): GeckoResult<Any>? {
            if (nativeApp != APP || sender.webExtension.id != EXTENSION_ID || message !is JSONObject) return null
            val prefs = store ?: return null
            if (sender.environmentType == WebExtension.MessageSender.ENV_TYPE_EXTENSION) {
                if (message.optString("kind") != "request") return null
                val url = message.optString("url")
                val page = message.optString("page").takeIf(::validWebUrl)
                val blocked = validWebUrl(url) && OrbitBrowserFilterEngine.shouldBlock(
                    Uri.parse(url), prefs.adBlock, page?.let { Uri.parse(it).host }, message.optBoolean("main", false))
                return reply(JSONObject().put("blocked", blocked))
            }
            if (sender.environmentType != WebExtension.MessageSender.ENV_TYPE_CONTENT_SCRIPT) return null
            val client = sender.session?.let { clients[it] } ?: return null
            when (message.optString("kind")) {
                "bootstrap" -> {
                    val pageHost = Uri.parse(sender.url).host
                    val siteTools = BrowserAdvancedRules.siteTools(pageHost)
                    val protectionEnabled = prefs.adBlock && !BrowserAdvancedRules.paused(pageHost)
                    val css = OrbitBrowserFilterEngine.cosmeticCss(pageHost, protectionEnabled)
                    return reply(JSONObject().put("css", css.takeIf { it.length <= 300_000 } ?: "")
                        .put("adBlock", protectionEnabled && !BuildConfig.PLAY_STORE_BUILD)
                        .put("pageSkipper", prefs.pageSkipper && !BuildConfig.PLAY_STORE_BUILD)
                        .put("skipSpeed", siteTools.skipSpeed)
                        .put("autoContinue", siteTools.autoContinue))
                }
                "reader" -> {
                    if (sender.url.substringBefore('#') != client.page().substringBefore('#')) return null
                    client.reader(message.optString("text").take(150_000))
                    return reply(JSONObject().put("ok", true))
                }
                "media" -> {
                    if (sender.url.substringBefore('#') != client.page().substringBefore('#')) return null
                    val url = message.optString("url")
                    if (prefs.videoHandoff && validWebUrl(url)) {
                        val mime = message.optString("mime").substringBefore(';').take(120)
                            .takeIf { it.startsWith("video/") || it.startsWith("audio/") }
                        client.media(url, mime, message.optString("title").take(500).takeIf { it.isNotBlank() })
                    }
                    return reply(JSONObject().put("ok", true))
                }
            }
            return null
        }
    }
}
