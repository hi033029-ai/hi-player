"""Context-checked imports of useful Hi Browser features into the Gecko source tree."""
from prepare_gecko_build import replace_once


def augment_parity(result):
    result = dict(result)
    key = 'main/integration/GeckoBrowserEntry.kt'
    result[key] = replace_once(result[key], '''    if (showSettings) GeckoAppearanceDialog(
        services.settings,
        canReloadCurrentPage = coordinator.activeSnapshot.url.isNotBlank(),
        onReloadCurrentPage = { coordinator.reload() },
    ) { showSettings = false }''', '''    if (showSettings) GeckoSettingsPanel(
        arguments, services, owner.store,
        onAccount = { showAccount = true },
        onDismiss = { showSettings = false },
    )''', 'full Gecko settings')
    key = 'main/engine/GeckoFeatureBridge.kt'
    result[key] = replace_once(result[key], '    private val delegate = object : WebExtension.MessageDelegate {', '''    private fun policyReply(compute: () -> JSONObject): GeckoResult<Any> {
        val result = GeckoResult<Any>()
        worker.execute {
            try { result.complete(compute().toString()) }
            catch (_: Exception) { result.completeExceptionally(IllegalStateException("Browser policy unavailable")) }
        }
        return result
    }
    private val delegate = object : WebExtension.MessageDelegate {''', 'off-main policy computation')
    result[key] = replace_once(result[key], '''                val blocked = validWebUrl(url) && OrbitBrowserFilterEngine.shouldBlock(
                    Uri.parse(url), prefs.adBlock, page?.let { Uri.parse(it).host }, message.optBoolean("main", false))
                return reply(JSONObject().put("blocked", blocked))''', '''                val enabled = prefs.adBlock
                val mainFrame = message.optBoolean("main", false)
                return policyReply {
                    val blocked = validWebUrl(url) && OrbitBrowserFilterEngine.shouldBlock(
                        Uri.parse(url), enabled, page?.let { Uri.parse(it).host }, mainFrame)
                    JSONObject().put("blocked", blocked)
                }''', 'background request filtering')
    result[key] = replace_once(result[key], '''                    val pageHost = Uri.parse(sender.url).host
                    val siteTools = BrowserAdvancedRules.siteTools(pageHost)
                    val protectionEnabled = prefs.adBlock && !BrowserAdvancedRules.paused(pageHost)
                    val css = OrbitBrowserFilterEngine.cosmeticCss(pageHost, protectionEnabled)
                    return reply(JSONObject().put("css", css.takeIf { it.length <= 300_000 } ?: "")
                        .put("adBlock", protectionEnabled && !BuildConfig.PLAY_STORE_BUILD)
                        .put("pageSkipper", prefs.pageSkipper && !BuildConfig.PLAY_STORE_BUILD)
                        .put("skipSpeed", siteTools.skipSpeed)
                        .put("autoContinue", siteTools.autoContinue))''', '''                    val pageHost = Uri.parse(client.page().takeIf(::validWebUrl) ?: sender.url).host
                    val enabled = prefs.adBlock
                    return policyReply {
                        val site = BrowserAdvancedRules.snapshot().sites[pageHost?.lowercase()?.trimEnd('.')]
                        val protectionEnabled = enabled && !BrowserAdvancedRules.paused(pageHost)
                        val css = OrbitBrowserFilterEngine.cosmeticCss(pageHost, protectionEnabled)
                        JSONObject().put("css", css.takeIf { it.length <= 300_000 } ?: "")
                            .put("adBlock", protectionEnabled && !BuildConfig.PLAY_STORE_BUILD)
                            .put("skipSpeed", site?.skipSpeed ?: 30)
                            .put("autoContinue", site?.autoContinue ?: true)
                            .put("pageSkipper", prefs.pageSkipper && !BuildConfig.PLAY_STORE_BUILD)
                    }''', 'per-site helper configuration')
    key = 'main/compatibility/Patched_GeckoBrowserChrome.kt'
    result[key] = replace_once(result[key], 'import androidx.compose.material.icons.Icons', '''import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.util.Locale
import androidx.compose.material.icons.Icons''', 'shared platform tools imports')
    result[key] = replace_once(result[key], '    var showMenu by remember', '''
    var platformNotice by remember { mutableStateOf<String?>(null) }
    fun openAddress(value: String) {
        val url = normalizeAddress(value) ?: return
        services?.secureRouting?.leaveTab(coordinator.activeId)
        if (onNavigate != null) onNavigate(url) else coordinator.load(url)
    }
    fun requestDefaultBrowser() {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val roles = context.getSystemService(android.app.role.RoleManager::class.java)
                if (roles != null && roles.isRoleAvailable(android.app.role.RoleManager.ROLE_BROWSER)) {
                    if (roles.isRoleHeld(android.app.role.RoleManager.ROLE_BROWSER)) platformNotice = "Hi Player is already your default browser."
                    else context.startActivity(roles.createRequestRoleIntent(android.app.role.RoleManager.ROLE_BROWSER))
                    return
                }
            }
            context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        } catch (_: Exception) { platformNotice = "Open Settings > Apps > Default apps > Browser app." }
    }
    var showMenu by remember''', 'native platform action state')
    result[key] = replace_once(result[key], '            onSetDefaultBrowser = null,', '            onSetDefaultBrowser = { requestDefaultBrowser() },', 'default browser action')
    result[key] = replace_once(result[key], '                HiMenuAction("Find in page", Icons.Default.FindInPage, enabled = onPage)', '''                HiMenuAction("Translate", Icons.Default.Language, enabled = bookmarkUrl.isNotEmpty()) {
                    if (HiBrowserTools.isTranslatedPage(bookmarkUrl)) platformNotice = "This page is already translated."
                    else openAddress(HiBrowserTools.translateUrl(bookmarkUrl, HiBrowserTools.translateTargetLanguage(Locale.getDefault())))
                },
                HiMenuAction("Add to home screen", Icons.Default.Add, enabled = bookmarkUrl.isNotEmpty()) {
                    platformNotice = if (HiBrowserTools.pinPageShortcut(context, bookmarkUrl, current.title, null))
                        "Shortcut request sent to your launcher." else "Your launcher does not support adding shortcuts."
                },
                HiMenuAction("Find in page", Icons.Default.FindInPage, enabled = onPage)''', 'translation and shortcut actions')
    result[key] = replace_once(result[key], '    if (clearFailed) com.example.ui.theme.AppAlertDialog(', '''    platformNotice?.let { message -> com.example.ui.theme.AppAlertDialog(
        onDismissRequest = { platformNotice = null }, title = { androidx.compose.material3.Text("Browser") },
        text = { androidx.compose.material3.Text(message) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { platformNotice = null }) { androidx.compose.material3.Text("OK") } }) }
    if (clearFailed) com.example.ui.theme.AppAlertDialog(''', 'platform action feedback')
    return result
