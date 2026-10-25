package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.text.TextUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

internal data class OrbitBrowserEntry(val title: String, val url: String)

/** Small private-app-storage store for Hi Browser's user-owned browser data. */
internal class OrbitBrowserStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("orbit_browser", Context.MODE_PRIVATE)

    var darkMode: Boolean
        get() = prefs.getBoolean("dark", false)
        set(value) { prefs.edit().putBoolean("dark", value).apply() }

    var adBlock: Boolean
        get() = prefs.getBoolean("adblock", true)
        set(value) { prefs.edit().putBoolean("adblock", value).apply() }

    var pageSkipper: Boolean
        get() = prefs.getBoolean("page_skipper", false)
        set(value) { prefs.edit().putBoolean("page_skipper", value).apply() }

    var youtubeAdSkip: Boolean
        get() = prefs.getBoolean("youtube_ad_skip", false)
        set(value) { prefs.edit().putBoolean("youtube_ad_skip", value).apply() }

    /** Page text size as a percentage of the site's own size (50–200). */
    var textZoom: Int
        get() = prefs.getInt("text_zoom", 100).coerceIn(50, 200)
        set(value) { prefs.edit().putInt("text_zoom", value.coerceIn(50, 200)).apply() }

    /** Blocks windows a page tries to open without the user tapping anything. */
    var popupBlock: Boolean
        get() = prefs.getBoolean("popup_block", true)
        set(value) { prefs.edit().putBoolean("popup_block", value).apply() }

    fun bookmarks(): List<OrbitBrowserEntry> = read("bookmarks")
    fun history(): List<OrbitBrowserEntry> = read("history")
    fun isBookmarked(url: String): Boolean = bookmarks().any { it.url == url }

    fun addBookmark(entry: OrbitBrowserEntry) = write("bookmarks", listOf(entry) + bookmarks().filterNot { it.url == entry.url })
    fun removeBookmark(url: String) = write("bookmarks", bookmarks().filterNot { it.url == url })

    fun addHistory(entry: OrbitBrowserEntry) {
        if (entry.url.startsWith("http://") || entry.url.startsWith("https://")) {
            write("history", listOf(entry) + history().filterNot { it.url == entry.url }.take(499))
        }
    }
    fun removeHistory(url: String) = write("history", history().filterNot { it.url == url })
    fun clearHistory() = write("history", emptyList())

    private fun read(key: String): List<OrbitBrowserEntry> = runCatching {
        val array = JSONArray(prefs.getString(key, "[]"))
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val url = item.optString("url").takeIf(String::isNotBlank) ?: return@mapNotNull null
            OrbitBrowserEntry(item.optString("title", url).take(500), url.take(4096))
        }
    }.getOrDefault(emptyList())

    private fun write(key: String, entries: List<OrbitBrowserEntry>) {
        val array = JSONArray()
        entries.take(500).forEach { entry ->
            array.put(JSONObject().put("title", entry.title.take(500)).put("url", entry.url.take(4096)))
        }
        prefs.edit().putString(key, array.toString()).apply()
    }
}

/** Bounded EasyList-subset rules; remote filter data is never interpreted as script. */
internal object OrbitBrowserFilterEngine {
    private data class Rule(val host: String, val pathPrefix: String)
    @Volatile private var blockRules: List<Rule> = emptyList()
    @Volatile private var allowRules: List<Rule> = emptyList()
    @Volatile private var genericSelectors: List<String> = emptyList()
    @Volatile private var hostSelectors: Map<String, List<String>> = emptyMap()
    private val builtInHosts = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
        "google-analytics.com", "googletagmanager.com", "googletagservices.com", "adnxs.com",
        "adsrvr.org", "taboola.com", "outbrain.com", "criteo.com", "criteo.net", "scorecardresearch.com",
        "quantserve.com", "moatads.com", "rubiconproject.com", "pubmatic.com", "openx.net",
        "advertising.com", "amazon-adsystem.com", "facebook.net", "hotjar.com", "mixpanel.com",
        "segment.io", "popads.net", "propellerads.com", "adsterra.com", "mgid.com", "yieldmo.com",
        "smartadserver.com", "casalemedia.com", "zedo.com", "2mdn.net", "serving-sys.com",
        "adform.net", "adcolony.com", "adsafeprotected.com", "demdex.net", "teads.tv",
    )
    private val safeSelector = Regex("^[A-Za-z0-9_#.:* >+,~^$|=\\[\\]()-]{1,180}$")
    private val filterUrls = listOf(
        "https://easylist.to/easylist/easylist.txt",
        "https://easylist.to/easylist/easyprivacy.txt",
    )

    fun load(context: Context) {
        val stored = File(context.filesDir, "orbit-filters.txt")
        val text = runCatching {
            if (stored.isFile) stored.readText().take(2_000_000)
            else context.assets.open("orbit_filters.txt").bufferedReader().use { it.readText().take(2_000_000) }
        }.getOrDefault("")
        parse(text.lineSequence().take(50_000).toList())
    }

    fun ruleCount(): Int = blockRules.size + allowRules.size + genericSelectors.size + hostSelectors.values.sumOf { it.size }

    fun shouldBlock(uri: Uri, enabled: Boolean): Boolean {
        if (!enabled) return false
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return false
        if (builtInHosts.any { host == it || host.endsWith(".$it") }) return true
        val path = (uri.encodedPath ?: "/") + (uri.encodedQuery?.let { "?$it" } ?: "")
        val sameHostAllow = allowRules.any { (host == it.host || host.endsWith(".${it.host}")) && path.startsWith(it.pathPrefix) }
        if (sameHostAllow) return false
        return blockRules.any { (host == it.host || host.endsWith(".${it.host}")) && path.startsWith(it.pathPrefix) }
    }

    fun cosmeticCss(host: String?, enabled: Boolean): String {
        if (!enabled) return ""
        val data = hostSelectors
        val selectors = (genericSelectors + generateSequence(host?.lowercase()) { value ->
            val dot = value.indexOf('.')
            if (dot < 0) null else value.substring(dot + 1)
        }.flatMap { data[it].orEmpty().asSequence() }).distinct().take(800)
        return selectors.joinToString("\n") { "$it{display:none!important}" }.take(100_000)
    }

    /** Downloads trusted HTTPS lists with strict byte/time caps, and atomically replaces stored rules. */
    fun update(context: Context): Boolean = runCatching {
        val combined = StringBuilder()
        for (source in filterUrls) {
            val connection = (URL(source).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "HiPlayer-OrbitBrowser/1.0")
            }
            try {
                require(connection.responseCode in 200..299) { "Filter download failed" }
                connection.inputStream.bufferedReader().use { reader ->
                    val buffer = CharArray(8192)
                    var total = 0
                    while (true) {
                        val count = reader.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 2_000_000) { "Filter list too large" }
                        combined.append(buffer, 0, count).append('\n')
                        require(combined.length <= 3_500_000) { "Combined lists too large" }
                    }
                }
            } finally {
                connection.disconnect()
            }
        }
        val file = File(context.filesDir, "orbit-filters.txt")
        val temporary = File(context.filesDir, "orbit-filters.tmp")
        temporary.writeText(combined.toString())
        check(temporary.renameTo(file)) { "Unable to install updated filters" }
        load(context)
        true
    }.getOrDefault(false)

    private fun parse(lines: List<String>) {
        val blocks = ArrayList<Rule>()
        val allows = ArrayList<Rule>()
        val generic = ArrayList<String>()
        val perHost = LinkedHashMap<String, MutableList<String>>()
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith('!') || line.startsWith('[')) continue
            val cosmetic = line.indexOf("##")
            if (cosmetic >= 0) {
                val hostList = line.substring(0, cosmetic)
                val selector = line.substring(cosmetic + 2)
                if (!safeSelector.matches(selector)) continue
                if (hostList.isBlank()) {
                    if (generic.size < 300) generic.add(selector)
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
            val exception = line.startsWith("@@")
            val ruleBody = if (exception) line.drop(2) else line
            if (ruleBody.contains('*') || ruleBody.contains(' ') || ruleBody.contains('$')) continue
            if (!ruleBody.startsWith("||")) continue
            val remainder = ruleBody.drop(2)
            val hostEnd = remainder.indexOfFirst { !(it.isLetterOrDigit() || it == '.' || it == '-') }.let { if (it < 0) remainder.length else it }
            val host = remainder.take(hostEnd).lowercase().removePrefix("www.")
            if (host.length > 253 || !host.contains('.') || host.any { !(it.isLetterOrDigit() || it == '.' || it == '-') }) continue
            var path = remainder.drop(hostEnd).trimEnd('^')
            if (path.isNotEmpty() && !path.startsWith('/')) continue
            if (path.contains('^') || path.length > 512) continue
            val target = if (exception) allows else blocks
            if (target.size < 40_000) target.add(Rule(host, path))
        }
        blockRules = blocks.toList()
        allowRules = allows.toList()
        genericSelectors = generic.toList()
        hostSelectors = perHost.mapValues { it.value.toList() }
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
