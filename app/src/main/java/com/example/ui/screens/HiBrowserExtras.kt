package com.example.ui.screens

import com.example.ui.theme.hiContainerBorder

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebViewDatabase
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.MainActivity
import com.example.R
import com.example.ui.theme.LocalHiPalette
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.net.URLEncoder
import java.util.Locale

/** Intent action used by Hi Browser home-screen shortcuts to reopen a page. */
const val HI_BROWSER_ACTION_OPEN_URL = "com.example.hiplayer.OPEN_BROWSER_URL"
const val HI_BROWSER_EXTRA_URL = "hi_browser_url"

/**
 * Pins the page's largest (preferably playing) video so it fills the page,
 * which makes it fill the picture-in-picture window. Returns "ok" or "none".
 */
internal val HI_BROWSER_PIP_PIN_SCRIPT = "(function(){" +
    "var vs=Array.prototype.slice.call(document.querySelectorAll('video'));if(!vs.length)return 'none';" +
    "var best=null,score=-1;vs.forEach(function(v){var r=v.getBoundingClientRect();" +
    "var s=(v.paused?0:1e9)+r.width*r.height;if(s>score){score=s;best=v;}});" +
    "Array.prototype.forEach.call(document.querySelectorAll('[data-hi-pip]'),function(e){e.removeAttribute('data-hi-pip');});" +
    "best.setAttribute('data-hi-pip','1');" +
    "var st=document.getElementById('__hi_pip_css');if(!st){st=document.createElement('style');st.id='__hi_pip_css';" +
    "(document.head||document.documentElement).appendChild(st);}" +
    "st.textContent='video[data-hi-pip]{position:fixed!important;left:0!important;top:0!important;" +
    "width:100vw!important;height:100vh!important;max-width:none!important;max-height:none!important;" +
    "margin:0!important;z-index:2147483647!important;background:#000!important;object-fit:contain!important;" +
    "transform:none!important}html,body{overflow:hidden!important}';" +
    "if(best.paused){var p=best.play();if(p&&p.catch)p.catch(function(){});}" +
    "return 'ok';})()"

/** Restores the page layout after picture-in-picture closes. */
internal val HI_BROWSER_PIP_UNPIN_SCRIPT = "(function(){" +
    "var st=document.getElementById('__hi_pip_css');if(st)st.parentNode.removeChild(st);" +
    "Array.prototype.forEach.call(document.querySelectorAll('[data-hi-pip]'),function(e){e.removeAttribute('data-hi-pip');});" +
    "})()"

internal object HiBrowserTools {

    /** Google Translate language code for the device language. */
    fun translateTargetLanguage(locale: Locale): String = when (locale.language) {
        "" -> "en"
        "zh" -> if (locale.country in setOf("TW", "HK", "MO")) "zh-TW" else "zh-CN"
        "in" -> "id"
        "ji" -> "yi"
        else -> locale.language
    }

    fun translateUrl(pageUrl: String, targetLanguage: String): String =
        "https://translate.google.com/translate?sl=auto&tl=$targetLanguage&hl=$targetLanguage&u=" +
            URLEncoder.encode(pageUrl, "UTF-8")

    fun isTranslatedPage(url: String): Boolean = runCatching {
        val host = Uri.parse(url).host?.lowercase().orEmpty()
        host == "translate.google.com" || host.endsWith(".translate.goog")
    }.getOrDefault(false)

    /** Asks the launcher to pin a shortcut that reopens [url] in Hi Browser. */
    fun pinPageShortcut(context: Context, url: String, title: String, favicon: Bitmap?): Boolean = runCatching {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return@runCatching false
        val label = title.trim()
            .takeIf { it.isNotBlank() && it != url }
            ?: Uri.parse(url).host?.removePrefix("www.").orEmpty().ifBlank { "Web page" }
        val intent = Intent(context, MainActivity::class.java)
            .setAction(HI_BROWSER_ACTION_OPEN_URL)
            .putExtra(HI_BROWSER_EXTRA_URL, url)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val icon = favicon
            ?.takeIf { it.width > 0 && it.height > 0 }
            ?.let { IconCompat.createWithBitmap(Bitmap.createScaledBitmap(it, 128, 128, true)) }
            ?: IconCompat.createWithResource(context, R.mipmap.ic_launcher)
        val shortcut = ShortcutInfoCompat.Builder(context, "hi_browser_" + Integer.toHexString(url.hashCode()))
            .setShortLabel(label.take(25))
            .setLongLabel(label.take(80))
            .setIcon(icon)
            .setIntent(intent)
            .build()
        ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }.getOrDefault(false)

    /** Opens Google Play services' QR scanner; no camera permission is needed. */
    fun scanQrCode(context: Context, onResult: (String) -> Unit, onError: (String) -> Unit) {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        runCatching {
            GmsBarcodeScanning.getClient(context, options)
                .startScan()
                .addOnSuccessListener { barcode ->
                    val value = barcode.rawValue?.trim()
                    if (value.isNullOrBlank()) onError("That QR code is empty") else onResult(value)
                }
                .addOnFailureListener { onError("Couldn't start the QR scanner. Check Google Play services.") }
        }.onFailure { onError("The QR scanner isn't available on this device") }
    }

    fun clearBrowsingData(
        context: Context,
        webViews: List<WebView>,
        history: Boolean,
        cookies: Boolean,
        cache: Boolean,
    ) {
        if (history) webViews.forEach { runCatching { it.clearHistory() } }
        if (cookies) {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                runCatching { WebViewDatabase.getInstance(context).clearHttpAuthUsernamePassword() }
            }
        }
        if (cache) {
            val view = webViews.firstOrNull()
            if (view != null) {
                view.clearCache(true)
            } else {
                runCatching { WebView(context).apply { clearCache(true); destroy() } }
            }
        }
    }
}

/**
 * Receives a window a page opens (target=_blank link or window.open) in a
 * throwaway WebView, reports its URL once, and then discards the WebView.
 */
internal object HiBrowserPopupCatcher {
    @SuppressLint("ViewConstructor")
    fun create(context: Context, onUrl: (String) -> Unit): WebView {
        val popup = WebView(context)
        var handled = false
        fun finish() {
            popup.post { runCatching { popup.stopLoading(); popup.destroy() } }
        }
        fun deliver(url: String?) {
            if (handled) return
            val value = url?.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: return
            handled = true
            finish()
            onUrl(value)
        }
        popup.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                deliver(request.url.toString())
                return true
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                deliver(url)
            }
        }
        popup.postDelayed({
            if (!handled) {
                handled = true
                finish()
            }
        }, 10_000L)
        return popup
    }
}

@Composable
internal fun BrowserFindBar(
    query: String,
    matchLabel: String,
    onQueryChange: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    val palette = LocalHiPalette.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    Row(
        modifier = Modifier.fillMaxWidth().background(palette.surface).padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            placeholder = { Text("Find in page", fontSize = 12.sp) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = palette.textPrimary, unfocusedTextColor = palette.textPrimary,
                focusedBorderColor = palette.primary, unfocusedBorderColor = palette.surfaceBorder,
                focusedContainerColor = palette.surfaceElevated, unfocusedContainerColor = palette.surfaceElevated,
            ),
            shape = RoundedCornerShape(15.dp),
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onNext() }),
        )
        Text(
            text = matchLabel,
            color = palette.textSecondary,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        IconButton(onClick = onPrevious) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous match", tint = palette.textPrimary)
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next match", tint = palette.textPrimary)
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = "Close find in page", tint = palette.textPrimary)
        }
    }
}

@Composable
internal fun BrowserClearDataDialog(
    onConfirm: (history: Boolean, cookies: Boolean, cache: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var history by remember { mutableStateOf(true) }
    var cookies by remember { mutableStateOf(true) }
    var cache by remember { mutableStateOf(true) }
    AlertDialog(
        modifier = androidx.compose.ui.Modifier.hiContainerBorder(androidx.compose.foundation.shape.RoundedCornerShape(28.dp)),
        onDismissRequest = onDismiss,
        title = { Text("Clear browsing data") },
        text = {
            Column {
                BrowserClearDataRow("Browsing history", "Pages you visited and back/forward lists", history) { history = it }
                BrowserClearDataRow("Cookies and site data", "Clears embedded-site sessions and browser-routing choices. Device-browser sign-ins are unchanged.", cookies) { cookies = it }
                BrowserClearDataRow("Cached images and files", "Pages may load slower next time", cache) { cache = it }
            }
        },
        confirmButton = {
            TextButton(enabled = history || cookies || cache, onClick = { onConfirm(history, cookies, cache) }) {
                Text("Clear")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun BrowserClearDataRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val palette = LocalHiPalette.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(description, color = palette.textSecondary, fontSize = 10.sp)
        }
    }
}