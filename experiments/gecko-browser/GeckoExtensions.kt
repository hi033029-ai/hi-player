package com.example.browser.gecko

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import java.net.URI

private fun addonPackage(input: String): String? {
    val value = input.trim()
    val slug = Regex("[a-zA-Z0-9_-]{1,150}")
    if (slug.matches(value)) return "https://addons.mozilla.org/firefox/downloads/latest/$value/latest.xpi"
    val uri = runCatching { URI(value) }.getOrNull() ?: return null
    if (uri.scheme != "https" || uri.host != "addons.mozilla.org" || uri.userInfo != null || uri.port != -1) return null
    if (uri.path.startsWith("/firefox/downloads/") && uri.path.endsWith(".xpi")) return uri.toASCIIString()
    val match = Regex("/(?:[A-Za-z-]+/)?(?:android|firefox)/addon/([a-zA-Z0-9_-]+)/?").matchEntire(uri.path)
    return match?.groupValues?.get(1)?.let { addonPackage(it) }
}

@Composable
internal fun GeckoExtensions() {
    val context = LocalContext.current.applicationContext
    val manager = remember(context) { GeckoRuntimeHolder.get(context).webExtensionController }
    var entries by remember { mutableStateOf(emptyList<WebExtension>()) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var installing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var request by remember { mutableStateOf<GeckoResult<WebExtension.PermissionPromptResponse>?>(null) }
    var description by remember { mutableStateOf("") }
    var removal by remember { mutableStateOf<WebExtension?>(null) }
    fun respond(grant: Boolean) {
        val pending = request
        request = null
        pending?.complete(WebExtension.PermissionPromptResponse(grant, false, false))
    }
    fun refresh() {
        busy = true
        manager.list().accept({ list -> entries = list.orEmpty().filterNot { it.isBuiltIn }; busy = false },
            { status = "Could not load extensions. Try again."; busy = false })
    }
    fun failed() { installing = false; busy = false; status = "Extension change was cancelled or could not be completed." }
    DisposableEffect(manager) {
        val previous = manager.promptDelegate
        val delegate = object : WebExtensionController.PromptDelegate {
            override fun onInstallPromptRequest(extension: WebExtension, permissions: Array<String>, origins: Array<String>,
                dataCollectionPermissions: Array<String>): GeckoResult<WebExtension.PermissionPromptResponse> {
                if (!installing || request != null) return GeckoResult.fromValue(WebExtension.PermissionPromptResponse(false, false, false))
                description = buildString {
                    appendLine(extension.metaData?.name ?: extension.id)
                    if (permissions.isNotEmpty()) appendLine("Permissions\n" + permissions.joinToString("\n"))
                    if (origins.isNotEmpty()) appendLine("Website access\n" + origins.joinToString("\n"))
                    if (dataCollectionPermissions.isNotEmpty()) appendLine("Data collection\n" + dataCollectionPermissions.joinToString("\n"))
                }
                return GeckoResult<WebExtension.PermissionPromptResponse>().also { request = it }
            }
        }
        manager.promptDelegate = delegate
        refresh()
        onDispose { installing = false; respond(false); if (manager.promptDelegate === delegate) manager.promptDelegate = previous }
    }
    OutlinedTextField(input, { input = it }, singleLine = true, enabled = !busy,
        label = { Text("Mozilla add-on link or slug") }, placeholder = { Text("ublock-origin") }, modifier = Modifier.fillMaxWidth())
    Row {
        TextButton(enabled = !busy && addonPackage(input) != null, onClick = {
            val url = addonPackage(input) ?: return@TextButton
            busy = true; installing = true; status = "Checking extension…"
            manager.install(url, WebExtensionController.INSTALLATION_METHOD_MANAGER).accept({
                installing = false; input = ""; status = "Extension installed."; refresh()
            }, { failed() })
        }) { Text("Install") }
        TextButton(enabled = !busy, onClick = { refresh() }) { Text("Refresh list") }
    }
    if (status.isNotEmpty()) Text(status)
    for (extension in entries) {
        val enabled = extension.metaData?.enabled == true
        Text(extension.metaData?.name ?: extension.id)
        Row {
            TextButton(enabled = !busy, onClick = {
                busy = true
                val action = if (enabled) manager.disable(extension, WebExtensionController.EnableSource.USER)
                    else manager.enable(extension, WebExtensionController.EnableSource.USER)
                action.accept({ status = ""; refresh() }, { failed() })
            }) { Text(if (enabled) "Disable" else "Enable") }
            TextButton(enabled = !busy, onClick = { removal = extension }) { Text("Remove") }
        }
    }
    if (request != null) AlertDialog(onDismissRequest = { respond(false) }, title = { Text("Allow this extension?") },
        text = { Text(description, Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { respond(true) }) { Text("Allow and install") } },
        dismissButton = { TextButton(onClick = { respond(false) }) { Text("Cancel") } })
    removal?.let { extension ->
        AlertDialog(onDismissRequest = { removal = null }, title = { Text("Remove extension?") },
            text = { Text(extension.metaData?.name ?: extension.id) },
            confirmButton = { TextButton(onClick = {
                removal = null; busy = true
                manager.uninstall(extension).accept({ status = "Extension removed."; refresh() }, { failed() })
            }) { Text("Remove") } }, dismissButton = { TextButton(onClick = { removal = null }) { Text("Cancel") } })
    }
}
