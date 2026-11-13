package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun BrowserAdvancedTools(currentHost: String?, onSaved: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var rules by remember { mutableStateOf(BrowserAdvancedRules.snapshot()) }
    var host by rememberSaveable { mutableStateOf(currentHost.orEmpty()) }
    var paused by rememberSaveable { mutableStateOf(false) }
    var speed by rememberSaveable { mutableStateOf(30) }
    var auto by rememberSaveable { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val normalized = BrowserAdvancedRules.host(host)
    LaunchedEffect(normalized) {
        val policy = rules.sites[normalized] ?: BrowserSiteTools()
        paused = policy.pauseBlocking; speed = policy.skipSpeed; auto = policy.autoContinue
    }
    fun save(next: BrowserRuleSnapshot) {
        if (busy) return
        busy = true; message = "Saving…"
        scope.launch {
            try {
                withContext(Dispatchers.IO) { BrowserAdvancedRules.save(context, next) }
                rules = next; onSaved()
                message = "Saved. Reload or navigate to apply page scripts; unsent input is not reloaded automatically."
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { message = "Couldn't save. Use valid hostnames; limits are 200 block/allow rules and 100 sites."
            } finally { busy = false }
        }
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Advanced site tools", style = MaterialTheme.typography.titleMedium)
        Text("Hostnames only, no URLs or passwords. Host rules include subdomains; site settings match the exact host. Explicit blocks win over allows.")
        OutlinedTextField(host, { host = it.take(253) }, label = { Text("Website hostname") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(modifier = Modifier.weight(1f), enabled = !busy && normalized != null,
                onClick = { save(rules.copy(blocked = rules.blocked + normalized!!, allowed = rules.allowed - normalized)) }) {
                Text("Block host", maxLines = 1)
            }
            OutlinedButton(modifier = Modifier.weight(1f), enabled = !busy && normalized != null,
                onClick = { save(rules.copy(allowed = rules.allowed + normalized!!, blocked = rules.blocked - normalized)) }) {
                Text("Allow host", maxLines = 1)
            }
        }
        Text("Blocking follows the main ad-blocking switch. Allowing a host does not override HTTPS, sign-in or other security checks.", style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Pause ad blocking on this site", modifier = Modifier.weight(1f))
            Switch(checked = paused, onCheckedChange = { paused = it }, enabled = !busy)
        }
        Text("Wait-page timer speed · ${speed}×")
        Row(Modifier.horizontalScroll(rememberScrollState())) { listOf(1, 10, 30, 60).forEach { value ->
            TextButton(enabled = !busy, onClick = { speed = value }) { Text(if (speed == value) "✓ $value×" else "$value×") }
        } }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Automatically continue supported wait pages", Modifier.weight(1f))
            Switch(checked = auto, onCheckedChange = { auto = it }, enabled = !busy)
        }
        Text("Page skipper must be on. 1× preserves original timer speed. This cannot skip server waits, sign-in, consent, checkout or security challenges.", style = MaterialTheme.typography.bodySmall)
        Button(enabled = !busy && normalized != null, onClick = {
            save(rules.copy(sites = rules.sites + (normalized!! to BrowserSiteTools(paused, speed, auto))))
        }) { Text("Save site settings") }
        (rules.blocked + rules.allowed + rules.sites.keys).sorted().forEach { savedHost ->
            Text(savedHost + when { savedHost in rules.blocked -> " · blocked"; savedHost in rules.allowed -> " · allowed"; else -> " · site settings" })
            Row {
                TextButton(enabled = !busy, onClick = { host = savedHost }) { Text("Edit") }
                TextButton(enabled = !busy, onClick = { save(rules.copy(blocked = rules.blocked - savedHost, allowed = rules.allowed - savedHost, sites = rules.sites - savedHost)) }) { Text("Reset") }
            }
        }
        message?.let { Text(it) }
    }
}
