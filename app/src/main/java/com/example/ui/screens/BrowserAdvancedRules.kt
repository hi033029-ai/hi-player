package com.example.ui.screens

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.net.IDN
import java.util.Locale

internal data class BrowserSiteTools(val pauseBlocking: Boolean = false, val skipSpeed: Int = 30, val autoContinue: Boolean = true)
internal data class BrowserRuleSnapshot(val blocked: Set<String> = emptySet(), val allowed: Set<String> = emptySet(), val sites: Map<String, BrowserSiteTools> = emptyMap())

/** Immutable publication: WebView interception threads never parse JSON or touch Compose state. */
internal object BrowserAdvancedRules {
    @Volatile private var state = BrowserRuleSnapshot()
    @Volatile private var initialized = false
    fun host(input: String): String? = runCatching {
        val raw = input.trim().trimEnd('.').lowercase(Locale.ROOT)
        if (raw.isBlank() || raw.any { it.isWhitespace() || it in "/:@?#\\*" }) return null
        val normalized = IDN.toASCII(raw)
        normalized.takeIf { it.length <= 253 && '.' in it && it.split('.').all { label ->
            label.length in 1..63 && label.first() != '-' && label.last() != '-' && label.all { c -> c.isLetterOrDigit() || c == '-' }
        } }
    }.getOrNull()
    @Synchronized fun initialize(context: Context) {
        if (initialized) return
        val prefs = context.applicationContext.getSharedPreferences("browser_advanced_rules_v1", Context.MODE_PRIVATE)
        state = runCatching {
            val json = JSONObject(prefs.getString("rules", "{}") ?: "{}")
            fun hosts(key: String): Set<String> {
                val array = json.optJSONArray(key) ?: return emptySet()
                return (0 until minOf(array.length(), 200)).mapNotNull { host(array.optString(it)) }.toSet()
            }
            val sites = json.optJSONObject("sites") ?: JSONObject()
            BrowserRuleSnapshot(hosts("block"), hosts("allow"), sites.keys().asSequence().take(100).mapNotNull { key ->
                val normalized = host(key) ?: return@mapNotNull null
                val item = sites.optJSONObject(key) ?: return@mapNotNull null
                normalized to BrowserSiteTools(item.optBoolean("pause"), item.optInt("speed", 30).takeIf { it in listOf(1, 10, 30, 60) } ?: 30, item.optBoolean("continue", true))
            }.toMap())
        }.getOrDefault(BrowserRuleSnapshot())
        initialized = true
    }
    fun snapshot() = state
    private fun matches(host: String, rule: String) = host == rule || host.endsWith(".$rule")
    fun paused(pageHost: String?) = state.sites[pageHost?.lowercase(Locale.ROOT)?.trimEnd('.')]?.pauseBlocking == true
    fun decision(requestHost: String?, pageHost: String?): Boolean? {
        val target = requestHost?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return null
        val rules = state
        // Explicit blocks win conflicts; pause and allow never bypass TLS or site authentication.
        if (rules.blocked.any { matches(target, it) }) return true
        if (rules.allowed.any { matches(target, it) } || paused(pageHost ?: target)) return false
        return null
    }
    private fun json(rules: BrowserRuleSnapshot): JSONObject {
        val sites = JSONObject()
        rules.sites.forEach { (host, policy) -> sites.put(host, JSONObject().put("pause", policy.pauseBlocking).put("speed", policy.skipSpeed).put("continue", policy.autoContinue)) }
        return JSONObject().put("block", org.json.JSONArray(rules.blocked.toList())).put("allow", org.json.JSONArray(rules.allowed.toList())).put("sites", sites)
    }
    @Synchronized fun save(context: Context, rules: BrowserRuleSnapshot) {
        require(rules.blocked.size <= 200 && rules.allowed.size <= 200 && rules.sites.size <= 100)
        require((rules.blocked + rules.allowed + rules.sites.keys).all { host(it) == it })
        require(rules.sites.values.all { it.skipSpeed in listOf(1, 10, 30, 60) })
        check(context.applicationContext.getSharedPreferences("browser_advanced_rules_v1", Context.MODE_PRIVATE).edit().putString("rules", json(rules).toString()).commit())
        state = rules
    }
    /** Same wrapper at document start and legacy injection. Decisions are made in the actual origin. */
    fun wrap(name: String, script: String): String {
        if (name !in setOf("orbit_adblock.js", "orbit_youtube.js", "orbit_page_skipper.js")) return script
        val rules = json(state).getJSONObject("sites").toString()
        val adScript = name != "orbit_page_skipper.js"
        return "(function(){var rules=$rules;var h=location.hostname;try{h=top.location.hostname||h;}catch(e){try{h=new URL(document.referrer).hostname||h;}catch(ignore){}}var p=rules[h.toLowerCase().replace(/\\.$/, '')]||{};" +
            (if (adScript) "if(p.pause)return;" else "window.__hiSkipOptions={speed:p.speed||30,autoContinue:p.continue!==false};") + script + "\n})();"
    }
}
