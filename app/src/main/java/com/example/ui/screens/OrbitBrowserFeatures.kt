package com.example.ui.screens

import android.content.Context
import android.util.AtomicFile
import android.net.Uri
import android.text.TextUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal data class OrbitBrowserEntry(val title: String, val url: String)

/** A regular (non-private) tab persisted so the browser reopens where the user left it. */
internal data class OrbitSavedTab(
    val id: Int,
    val url: String,
    val title: String,
    val desktop: Boolean = false,
    val openerTabId: Int? = null,
)

/** Small private-app-storage store for Hi Browser's user-owned browser data. */
internal class OrbitBrowserStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("orbit_browser", Context.MODE_PRIVATE)

    init {
        // One-time switch: every protection and helper starts on (v1.0.60).
        if (!prefs.getBoolean("defaults_v2_applied", false)) {
            prefs.edit()
                .putBoolean("adblock", true)
                .putBoolean("page_skipper", true)
                .putBoolean("youtube_ad_skip", true)
                .putBoolean("popup_block", true)
                .putBoolean("video_handoff", true)
                .putBoolean("defaults_v2_applied", true)
                .apply()
        }
    }

    var searchProviderName: String
        get() = prefs.getString("search_provider", "GOOGLE") ?: "GOOGLE"
        set(value) { prefs.edit().putString("search_provider", value).apply() }

    var darkMode: Boolean
        get() = prefs.getBoolean("dark", false)
        set(value) { prefs.edit().putBoolean("dark", value).apply() }

    var adBlock: Boolean
        get() = prefs.getBoolean("adblock", true)
        set(value) { prefs.edit().putBoolean("adblock", value).apply() }

    var pageSkipper: Boolean
        get() = prefs.getBoolean("page_skipper", true)
        set(value) { prefs.edit().putBoolean("page_skipper", value).apply() }

    var youtubeAdSkip: Boolean
        get() = prefs.getBoolean("youtube_ad_skip", true)
        set(value) { prefs.edit().putBoolean("youtube_ad_skip", value).apply() }

    /** Detect website video candidates for explicit user-selected playback only. */
    var videoHandoff: Boolean
        get() = prefs.getBoolean("video_handoff", true)
        set(value) { prefs.edit().putBoolean("video_handoff", value).apply() }

    /**
     * Opens a website video in Hi Player as soon as the user starts it on the page.
     * Only user-started, full-length, non-ad videos are handed off.
     */
    var autoPlayInPlayer: Boolean
        get() = prefs.getBoolean("auto_play_in_player", true)
        set(value) { prefs.edit().putBoolean("auto_play_in_player", value).apply() }

    /** Page text size as a percentage of the site's own size (80–200). */
    var textZoom: Int
        get() = prefs.getInt("text_zoom", 100).coerceIn(80, 200)
        set(value) { prefs.edit().putInt("text_zoom", value.coerceIn(80, 200)).apply() }

    /** Blocks windows a page tries to open without the user tapping anything. */
    var popupBlock: Boolean
        get() = prefs.getBoolean("popup_block", true)
        set(value) { prefs.edit().putBoolean("popup_block", value).apply() }

    fun bookmarks(): List<OrbitBrowserEntry> = read("bookmarks")

    /** History is read on every keystroke for suggestions, so keep one parsed copy in memory. */
    fun history(): List<OrbitBrowserEntry> {
        historyCache?.let { return it }
        synchronized(historyLock) {
            historyCache?.let { return it }
            return read("history").also { historyCache = it }
        }
    }

    /** Tabs from the last session (regular tabs only; private tabs are never written to disk). */
    fun savedTabs(): List<OrbitSavedTab> = runCatching {
        val array = JSONArray(prefs.getString("open_tabs", "[]"))
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optInt("id", -1).takeIf { it > 0 } ?: return@mapNotNull null
            val url = item.optString("url").take(4096)
            if (url.isNotBlank() && !url.startsWith("http://") && !url.startsWith("https://")) return@mapNotNull null
            OrbitSavedTab(
                id = id,
                url = url,
                title = item.optString("title", "New tab").take(500).ifBlank { "New tab" },
                desktop = item.optBoolean("desktop", false),
                openerTabId = item.optInt("opener", -1).takeIf { it > 0 },
            )
        }.distinctBy { it.id }.take(12)
    }.getOrDefault(emptyList())

    val savedActiveTabId: Int
        get() = prefs.getInt("open_tabs_active", -1)

    fun saveTabs(tabs: List<OrbitSavedTab>, activeTabId: Int) {
        val array = JSONArray()
        tabs.take(12).forEach { tab ->
            val item = JSONObject()
                .put("id", tab.id)
                .put("url", tab.url.take(4096))
                .put("title", tab.title.take(500))
                .put("desktop", tab.desktop)
            tab.openerTabId?.let { item.put("opener", it) }
            array.put(item)
        }
        prefs.edit().putString("open_tabs", array.toString()).putInt("open_tabs_active", activeTabId).apply()
    }

    fun isBookmarked(url: String): Boolean = bookmarks().any { it.url == url }

    fun addBookmark(entry: OrbitBrowserEntry) = write("bookmarks", listOf(entry) + bookmarks().filterNot { it.url == entry.url })
    fun removeBookmark(url: String) = write("bookmarks", bookmarks().filterNot { it.url == url })

    fun addHistory(entry: OrbitBrowserEntry) {
        if (entry.url.startsWith("http://") || entry.url.startsWith("https://")) synchronized(historyLock) {
            val current = history()
            if (current.firstOrNull() == entry) return
            write("history", listOf(entry) + current.filterNot { it.url == entry.url }.take(499))
        }
    }
    // Update suggestions only from a redirect actually observed by this WebView.
    // Never guess a replacement domain or rewrite bookmarks to a different owner.
    fun replaceRedirectedHistory(oldUrl: String, newUrl: String) {
        if (oldUrl != newUrl && BrowserHistoryPolicy.isWebUrl(newUrl) && newUrl.startsWith("https://")) synchronized(historyLock) {
            write("history", history().filterNot { it.url == oldUrl })
        }
    }
    fun removeHistory(url: String) = synchronized(historyLock) { write("history", history().filterNot { it.url == url }) }
    fun clearHistory() = synchronized(historyLock) { write("history", emptyList()) }

    fun shortcuts(): List<OrbitBrowserEntry> =
        if (prefs.contains("shortcuts")) read("shortcuts") else ORBIT_DEFAULT_SHORTCUTS

    fun saveShortcuts(entries: List<OrbitBrowserEntry>) = write("shortcuts", entries.distinctBy { it.url }.take(12))

    private fun read(key: String): List<OrbitBrowserEntry> = runCatching {
        val array = JSONArray(prefs.getString(key, "[]"))
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val url = item.optString("url").takeIf(String::isNotBlank) ?: return@mapNotNull null
            OrbitBrowserEntry(item.optString("title", url).take(500), url.take(4096))
        }
    }.getOrDefault(emptyList())

    private fun write(key: String, entries: List<OrbitBrowserEntry>) {
        if (key == "history") historyCache = entries.take(500)
        val array = JSONArray()
        entries.take(500).forEach { entry ->
            array.put(JSONObject().put("title", entry.title.take(500)).put("url", entry.url.take(4096)))
        }
        prefs.edit().putString(key, array.toString()).apply()
    }

    private companion object {
        val historyLock = Any()
        @Volatile var historyCache: List<OrbitBrowserEntry>? = null
    }
}

/** Bounded EasyList-subset rules; remote filter data is never interpreted as script. */
internal object OrbitBrowserFilterEngine {
    private class Rule(val pathPrefix: String, val thirdPartyOnly: Boolean)
    /** EasyList's generic URL-fragment rules (e.g. "/adserver/", "-banner-ad."), matched on sub-resources only. */
    private class SubstringRule(val pattern: String, val separatorEnd: Boolean, val thirdPartyOnly: Boolean)
    // Rules are indexed by host so each request checks a handful of entries
    // (one per domain level) instead of scanning every rule.
    @Volatile private var blockIndex: Map<String, List<Rule>> = emptyMap()
    @Volatile private var allowIndex: Map<String, List<Rule>> = emptyMap()
    @Volatile private var genericSelectors: List<String> = emptyList()
    @Volatile private var hostSelectors: Map<String, List<String>> = emptyMap()
    @Volatile private var substringIndex: Map<String, List<SubstringRule>> = emptyMap()
    @Volatile private var genericCss: String = ""
    @Volatile private var ruleTotal = 0
    @Volatile private var loaded = false
    private val loadLock = Any()
    private val updateLock = Any()
    private val builtInHosts = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
        "google-analytics.com", "googletagmanager.com", "googletagservices.com", "adnxs.com",
        "adsrvr.org", "taboola.com", "outbrain.com", "criteo.com", "criteo.net", "scorecardresearch.com",
        "quantserve.com", "moatads.com", "rubiconproject.com", "pubmatic.com", "openx.net",
        "advertising.com", "amazon-adsystem.com", "facebook.net", "hotjar.com", "mixpanel.com",
        "segment.io", "popads.net", "propellerads.com", "adsterra.com", "mgid.com", "yieldmo.com",
        "smartadserver.com", "casalemedia.com", "zedo.com", "2mdn.net", "serving-sys.com",
        "adform.net", "adcolony.com", "adsafeprotected.com", "demdex.net", "teads.tv",
        "exoclick.com", "exosrv.com", "juicyads.com", "trafficjunky.net", "adcash.com", "hilltopads.net",
        "a-ads.com", "popcash.net", "onclickads.net", "clickadu.com", "adskeeper.com", "revcontent.com",
        "inmobi.com", "applovin.com", "unityads.unity3d.com", "media.net", "33across.com", "sharethrough.com",
        "bidswitch.net", "contextweb.com", "lijit.com", "sovrn.com", "spotxchange.com", "springserve.com",
        "imasdk.googleapis.com", "pagead2.googlesyndication.com", "popmyads.com", "propellerclick.com",
        "admaven.com", "ad-maven.com", "adsco.re", "monetag.com", "galaksion.com", "richads.com",
        // Video, pop-under and redirect ad networks common on streaming/download sites.
        "realsrv.com", "tsyndicate.com", "trafficstars.com", "adspyglass.com", "plugrush.com",
        "ero-advertising.com", "adnium.com", "bidvertiser.com", "yllix.com", "clickadilla.com",
        "adxpansion.com", "trafficfactory.biz", "zergnet.com", "adblade.com", "lockerdome.com",
        "vidoomy.com", "aniview.com", "primis.tech", "vdo.ai", "unrulymedia.com", "innovid.com",
        "tremorhub.com", "adition.com", "doubleverify.com", "popunder.net", "popcash.com",
        "adtelligent.com", "vidazoo.com", "connatix.com", "adhigh.net", "adzerk.net",
    )
    private val ignoredTypeOptions = setOf(
        "script", "image", "stylesheet", "css", "subdocument", "frame", "xmlhttprequest", "xhr", "media",
        "object", "ping", "other", "font", "websocket", "important", "all",
    )
    private val safeSelector = Regex("^[A-Za-z0-9_#.:* >+,~^$|=\\[\\]()-]{1,180}$")
    private val filterUrls = listOf(
        "https://easylist.to/easylist/easylist.txt",
        "https://easylist.to/easylist/easyprivacy.txt",
        // Peter Lowe's ad/tracking server list (small, host-only, AdBlock Plus format).
        "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=adblockplus&showintro=0&mimetype=plaintext",
    )
    /** Bump when sources or installed format changes, forcing a fresh combined file. */
    private const val FILTER_SOURCES_VERSION = "4"
    private const val MAX_FILTER_LINES = 300_000
    private const val MAX_GENERIC_SELECTORS = 1_500
    private const val MAX_SUBSTRING_RULES = 8_000
    private const val AUTO_UPDATE_AGE_MS = 24L * 60 * 60 * 1000

    /** Loads the stored (or bundled) lists. Safe to call repeatedly; parses once. */
    fun load(context: Context) {
        if (loaded) return
        synchronized(loadLock) {
            if (loaded) return
            reload(context)
        }
    }

    private fun reload(context: Context) {
        val stored = File(context.filesDir, "orbit-filters.txt")
        runCatching {
            // openRead recovers AtomicFile's backup after an interrupted install.
            val reader = runCatching { AtomicFile(stored).openRead().bufferedReader() }
                .getOrElse { context.assets.open("orbit_filters.txt").bufferedReader() }
            reader.useLines { lines -> parse(lines.take(MAX_FILTER_LINES)) }
        }
        loaded = true
    }

    /** Called by durable background work; failed updates remain eligible for retry. */
    fun autoUpdateIfStale(context: Context, cancelled: () -> Boolean = { false }): Boolean = synchronized(updateLock) {
        val stored = File(context.filesDir, "orbit-filters.txt")
        val version = runCatching { File(context.filesDir, "orbit-filters.version").readText().trim() }.getOrNull()
        val age = System.currentTimeMillis() - stored.lastModified()
        val stale = !stored.isFile || version != FILTER_SOURCES_VERSION || age < 0 || age >= AUTO_UPDATE_AGE_MS
        !stale || update(context, cancelled)
    }

    fun ruleCount(): Int = ruleTotal

    /**
     * [mainFrame] is true for page navigations: those only use host rules, while
     * sub-resources (scripts, frames, images, XHR, media) also use URL-fragment rules.
     */
    fun shouldBlock(uri: Uri, enabled: Boolean, pageHost: String? = null, mainFrame: Boolean = true): Boolean {
        if (!enabled) return false
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return false
        val blocks = blockIndex
        var path: String? = null
        var thirdParty: Boolean? = null
        var blocked = false
        var suffix: String? = host
        while (suffix != null) {
            if (suffix in builtInHosts) return true
            if (!blocked) {
                val rules = blocks[suffix]
                if (rules != null) {
                    val requestPath = path ?: requestPath(uri).also { path = it }
                    blocked = rules.any { rule ->
                        requestPath.startsWith(rule.pathPrefix) && (!rule.thirdPartyOnly ||
                            (thirdParty ?: isThirdParty(host, pageHost).also { thirdParty = it }))
                    }
                }
            }
            val dot = suffix.indexOf('.')
            suffix = if (dot < 0) null else suffix.substring(dot + 1)
        }
        if (!blocked && !mainFrame) {
            blocked = matchesSubstringRule(uri.toString(), host, pageHost)
        }
        if (!blocked) return false
        val allows = allowIndex
        val requestPath = path ?: requestPath(uri)
        suffix = host
        while (suffix != null) {
            val rules = allows[suffix]
            if (rules != null && rules.any { rule ->
                    requestPath.startsWith(rule.pathPrefix) && (!rule.thirdPartyOnly ||
                        (thirdParty ?: isThirdParty(host, pageHost).also { thirdParty = it }))
                }
            ) return false
            val dot = suffix.indexOf('.')
            suffix = if (dot < 0) null else suffix.substring(dot + 1)
        }
        return true
    }

    private fun matchesSubstringRule(url: String, host: String, pageHost: String?): Boolean {
        val index = substringIndex
        if (index.isEmpty()) return false
        val value = url.take(2_048).lowercase()
        var thirdParty: Boolean? = null
        for (position in 0..value.length - 3) {
            val candidates = index[value.substring(position, position + 3)] ?: continue
            for (rule in candidates) {
                if (!value.startsWith(rule.pattern, position)) continue
                if (rule.separatorEnd) {
                    val next = position + rule.pattern.length
                    if (next < value.length) {
                        val c = value[next]
                        if (c.isLetterOrDigit() || c == '_' || c == '-' || c == '.' || c == '%') continue
                    }
                }
                if (rule.thirdPartyOnly && !(thirdParty ?: isThirdParty(host, pageHost).also { thirdParty = it })) continue
                return true
            }
        }
        return false
    }

    private fun requestPath(uri: Uri): String = (uri.encodedPath ?: "/") + (uri.encodedQuery?.let { "?$it" } ?: "")

    private fun siteOf(host: String): String {
        val labels = host.split('.')
        if (labels.size <= 2) return host
        val last = labels[labels.size - 1]
        val second = labels[labels.size - 2]
        val keep = if (last.length == 2 && second.length <= 3) 3 else 2
        return labels.takeLast(keep).joinToString(".")
    }

    private fun isThirdParty(host: String, pageHost: String?): Boolean {
        val page = pageHost?.lowercase()?.trimEnd('.')?.takeIf { it.isNotBlank() } ?: return false
        return siteOf(host) != siteOf(page)
    }

    fun cosmeticCss(host: String?, enabled: Boolean): String {
        if (!enabled) return ""
        val data = hostSelectors
        val hostCss = generateSequence(host?.lowercase()?.removePrefix("www.")) { value ->
            val dot = value.indexOf('.')
            if (dot < 0) null else value.substring(dot + 1)
        }.flatMap { data[it].orEmpty().asSequence() }.distinct().take(600)
            .joinToString("\n") { "$it{display:none!important}" }
        return (genericCss + "\n" + hostCss).take(160_000)
    }

    /** Serialized install retains bundled YouTube rules and the last valid downloaded file. */
    fun update(context: Context, cancelled: () -> Boolean = { false }): Boolean = synchronized(updateLock) {
        BrowserFilterFileUpdater.install(context, filterUrls, FILTER_SOURCES_VERSION, cancelled) { commit ->
            synchronized(loadLock) { commit(); reload(context) }
        }
    }

    private fun parse(lines: Sequence<String>) {
        val blocks = HashMap<String, MutableList<Rule>>()
        val allows = HashMap<String, MutableList<Rule>>()
        var blockCount = 0
        var allowCount = 0
        val generic = ArrayList<String>()
        val perHost = LinkedHashMap<String, MutableList<String>>()
        val substrings = HashMap<String, MutableList<SubstringRule>>()
        var substringCount = 0
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith('!') || line.startsWith('[')) continue
            val cosmetic = line.indexOf("##")
            if (cosmetic >= 0) {
                val hostList = line.substring(0, cosmetic)
                val selector = line.substring(cosmetic + 2)
                if (!safeSelector.matches(selector)) continue
                if (hostList.isBlank()) {
                    if (generic.size < MAX_GENERIC_SELECTORS) generic.add(selector)
                } else {
                    hostList.split(',').filter { it.isNotBlank() && !it.startsWith('~') }.take(20).forEach { host ->
                        val cleanHost = host.lowercase().removePrefix("www.")
                        if (cleanHost.length <= 253 && cleanHost.contains('.') && cleanHost.all { it.isLetterOrDigit() || it == '.' || it == '-' }) {
                            val list = perHost.getOrPut(cleanHost) { ArrayList() }
                            if (list.size < 100) list.add(selector)
                        }
                    }
                }
                continue
            }
            if (line.contains("#@#") || line.contains("#?#") || line.contains("#$#")) continue
            val exception = line.startsWith("@@")
            var ruleBody = if (exception) line.drop(2) else line
            var thirdPartyOnly = false
            val dollar = ruleBody.indexOf('$')
            if (dollar >= 0) {
                var supported = true
                for (option in ruleBody.substring(dollar + 1).split(',')) {
                    when (val value = option.trim().lowercase()) {
                        "third-party", "3p" -> thirdPartyOnly = true
                        in ignoredTypeOptions -> Unit
                        else -> supported = false
                    }
                    if (!supported) break
                }
                if (!supported) continue
                ruleBody = ruleBody.substring(0, dollar)
            }
            if (ruleBody.contains('*') || ruleBody.contains(' ')) continue
            if (!ruleBody.startsWith("||")) {
                // Generic URL fragments ("/adserver/", "-ad-banner."); anchored or regex rules are skipped.
                if (exception || substringCount >= MAX_SUBSTRING_RULES) continue
                if (ruleBody.startsWith('|') || (ruleBody.startsWith('/') && ruleBody.endsWith('/') && ruleBody.length > 2)) continue
                val separatorEnd = ruleBody.endsWith('^')
                val pattern = ruleBody.trimEnd('^').lowercase()
                if (pattern.length < 5 || pattern.length > 200 || pattern.contains('^') || pattern.contains('|')) continue
                substrings.getOrPut(pattern.substring(0, 3)) { ArrayList(1) }.add(SubstringRule(pattern, separatorEnd, thirdPartyOnly))
                substringCount++
                continue
            }
            val remainder = ruleBody.drop(2)
            val hostEnd = remainder.indexOfFirst { !(it.isLetterOrDigit() || it == '.' || it == '-') }.let { if (it < 0) remainder.length else it }
            val host = remainder.take(hostEnd).lowercase().removePrefix("www.").trimEnd('.')
            if (host.length > 253 || !host.contains('.') || host.any { !(it.isLetterOrDigit() || it == '.' || it == '-') }) continue
            var path = remainder.drop(hostEnd).trimEnd('^', '|')
            if (path.isNotEmpty() && !path.startsWith('/')) continue
            if (path.contains('^') || path.contains('|') || path.length > 512) continue
            if (exception) {
                if (allowCount >= 60_000) continue
                allows.getOrPut(host) { ArrayList(1) }.add(Rule(path, thirdPartyOnly))
                allowCount++
            } else {
                if (blockCount >= 150_000) continue
                blocks.getOrPut(host) { ArrayList(1) }.add(Rule(path, thirdPartyOnly))
                blockCount++
            }
        }
        blockIndex = blocks
        allowIndex = allows
        genericSelectors = generic.toList()
        genericCss = generic.distinct().joinToString("\n") { "$it{display:none!important}" }.take(120_000)
        hostSelectors = perHost.mapValues { it.value.toList() }
        substringIndex = substrings
        ruleTotal = blockCount + allowCount + substringCount + generic.size + perHost.values.sumOf { it.size }
    }
}

/** Reader page only contains extracted text; all page-controlled markup is escaped. */
internal object OrbitReaderMode {
    val extractionScript = """
        (function(){var candidates=document.querySelectorAll('article,main,section,div'),best=null,max=0;
        for(var i=0;i<candidates.length;i++){var n=candidates[i],len=0;
        for(var j=0;j<n.children.length;j++){var c=n.children[j];if(c.tagName==='P')len+=(c.innerText||'').length;}
        if(len>max){max=len;best=n;}}if(!best||max<200)return null;var out=[];
        for(var k=0;k<best.children.length;k++){var e=best.children[k];if(e.tagName==='P'||/^H[1-4]$/.test(e.tagName)){var t=(e.innerText||'').trim();if(t)out.push((e.tagName==='P'?'p:':'h:')+t);}}
        return JSON.stringify({title:document.title,items:out});})()
    """.trimIndent()

    fun html(title: String, items: JSONArray): String = buildString {
        append("<!doctype html><html><head><meta name=viewport content='width=device-width,initial-scale=1'><style>")
        append("body{max-width:680px;margin:0 auto;padding:24px;font:19px/1.75 Georgia,serif;background:#fff;color:#222}h1,h2{font-family:sans-serif}h1{font-size:28px}h2{font-size:21px;margin-top:1.6em}@media(prefers-color-scheme:dark){body{background:#121212;color:#eee}}</style></head><body>")
        append("<h1>").append(TextUtils.htmlEncode(title)).append("</h1>")
        for (index in 0 until items.length()) {
            val value = items.optString(index)
            val heading = value.startsWith("h:")
            val tag = if (heading) "h2" else "p"
            append('<').append(tag).append('>').append(TextUtils.htmlEncode(value.drop(2))).append("</").append(tag).append('>')
        }
        append("</body></html>")
    }
}

internal fun isOrbitYoutube(url: String): Boolean = runCatching {
    val host = Uri.parse(url).host?.lowercase().orEmpty()
    host == "youtu.be" ||
        host == "youtube.com" || host.endsWith(".youtube.com") ||
        host == "youtube-nocookie.com" || host.endsWith(".youtube-nocookie.com")
}.getOrDefault(false)

internal val ORBIT_DEFAULT_SHORTCUTS = listOf(
    OrbitBrowserEntry("Google", "https://www.google.com"),
    OrbitBrowserEntry("YouTube", "https://m.youtube.com"),
    OrbitBrowserEntry("Wikipedia", "https://www.wikipedia.org"),
    OrbitBrowserEntry("X", "https://x.com"),
)
