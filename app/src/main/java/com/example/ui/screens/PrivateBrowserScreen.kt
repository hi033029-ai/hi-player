package com.example.ui.screens

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.StreamUrlClassifier
import com.example.ui.theme.LocalHiPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Normal WebView browsing with a deliberately transparent, bundled host
 * blocklist. It never extracts protected streams or bypasses a website's
 * playback, login, DRM, payment, or access controls.
 */
@Composable
fun PrivateBrowserScreen(
    onBack: () -> Unit,
    onPlayDirectMedia: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val palette = LocalHiPalette.current
    val blockedHosts = remember { BrowserAdBlockList.load(context) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var address by remember { mutableStateOf("") }
    var currentUrl by remember { mutableStateOf("") }
    var pendingUrl by remember { mutableStateOf<String?>(null) }
    var showStartPage by remember { mutableStateOf(true) }
    var searchProvider by remember { mutableStateOf(BrowserSearchProvider.GOOGLE) }
    var pageTitle by remember { mutableStateOf("Private browser") }
    var loadingProgress by remember { mutableIntStateOf(0) }
    var rendererGone by remember { mutableStateOf(false) }
    val downloads by BrowserDownloads.downloads.collectAsState()

    fun loadAddress(raw: String) {
        val destination = normalizeBrowserInput(raw, searchProvider) ?: return
        address = destination
        currentUrl = destination
        pendingUrl = destination
        showStartPage = false
        webView?.loadUrl(destination)
    }

    fun showBrowserHome() {
        webView?.stopLoading()
        webView?.destroy()
        webView = null
        address = ""
        currentUrl = ""
        pendingUrl = null
        pageTitle = "Private browser"
        loadingProgress = 0
        rendererGone = false
        showStartPage = true
    }

    BackHandler {
        val view = webView
        when {
            view?.canGoBack() == true -> view.goBack()
            !showStartPage -> showBrowserHome()
            else -> onBack()
        }
    }

    // Do not key this effect to [webView]. AndroidView assigns the reference
    // during its first composition; a key change would dispose the previous
    // effect and destroy that newly created WebView before it can load.
    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.destroy()
        }
    }

    val hasActiveDownloads = downloads.any { it.isActive }
    LaunchedEffect(hasActiveDownloads) {
        while (hasActiveDownloads) {
            BrowserDownloads.refresh(context)
            delay(650L)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background),
    ) {
        if (showStartPage) {
            BrowserStartPage(
                query = address,
                onQueryChange = { address = it },
                provider = searchProvider,
                onProviderChange = { searchProvider = it },
                onSearch = { loadAddress(address) },
            )
        } else {
            Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.surface)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {
                if (webView?.canGoBack() == true) webView?.goBack() else showBrowserHome()
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = palette.textPrimary)
            }
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = palette.primary) },
                placeholder = { Text("Search the web or enter a link", fontSize = 13.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = palette.textPrimary,
                    unfocusedTextColor = palette.textPrimary,
                    focusedBorderColor = palette.primary,
                    unfocusedBorderColor = palette.surfaceBorder,
                    focusedContainerColor = palette.surfaceElevated,
                    unfocusedContainerColor = palette.surfaceElevated,
                ),
                modifier = Modifier.weight(1f),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { loadAddress(address) }),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                ),
            )
            IconButton(onClick = { webView?.reload() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = palette.textPrimary)
            }
        }

        if (loadingProgress in 1..99) {
            LinearProgressIndicator(
                progress = { loadingProgress / 100f },
                color = palette.primary,
                trackColor = palette.surfaceBorder,
                modifier = Modifier.fillMaxWidth().height(2.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.surface)
                .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = { showBrowserHome() }) {
                Icon(Icons.Default.Home, contentDescription = "Home", tint = palette.textSecondary)
            }
            Text(
                text = pageTitle,
                color = palette.textSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { onPlayDirectMedia(currentUrl) },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
            ) {
                Icon(Icons.Default.PlayCircle, contentDescription = null, modifier = Modifier.height(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Play link", fontSize = 11.sp)
            }
            IconButton(onClick = {
                BrowserDownloads.enqueue(context, currentUrl, webView?.settings?.userAgentString.orEmpty(), "")
            }) {
                Icon(Icons.Default.Download, contentDescription = "Download direct file", tint = palette.textPrimary)
            }
        }

        BrowserDownloadPanel(
            downloads = downloads,
            onClearCompleted = BrowserDownloads::clearCompleted,
        )
        }

        if (rendererGone) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Browser page closed to free memory", color = palette.textPrimary)
                Spacer(Modifier.height(10.dp))
                Button(onClick = { rendererGone = false }) { Text("Reload browser") }
            }
        } else if (!showStartPage) {
            AndroidView(
                factory = { viewContext ->
                    WebView(viewContext).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = false
                            cacheMode = WebSettings.LOAD_DEFAULT
                            allowFileAccess = false
                            allowContentAccess = false
                            setSupportMultipleWindows(false)
                            mediaPlaybackRequiresUserGesture = true
                            builtInZoomControls = true
                            displayZoomControls = false
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                safeBrowsingEnabled = true
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView, progress: Int) {
                                loadingProgress = progress
                            }

                            override fun onReceivedTitle(view: WebView, title: String?) {
                                pageTitle = title?.takeIf(String::isNotBlank) ?: "Private browser"
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val uri = request.url
                                return if (uri.scheme in setOf("http", "https")) {
                                    false
                                } else {
                                    runCatching {
                                        viewContext.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
                                    }.isSuccess
                                }
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                currentUrl = url
                                address = url
                                loadingProgress = 100
                            }

                            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                val host = request.url.host?.lowercase().orEmpty()
                                return if (BrowserAdBlockList.isBlocked(host, blockedHosts)) {
                                    WebResourceResponse(
                                        "text/plain",
                                        "UTF-8",
                                        ByteArrayInputStream(ByteArray(0)),
                                    )
                                } else {
                                    super.shouldInterceptRequest(view, request)
                                }
                            }

                            override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                                view.destroy()
                                rendererGone = true
                                return true
                            }
                        }
                        setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                            BrowserDownloads.enqueue(viewContext, url, userAgent.orEmpty(), contentDisposition.orEmpty(), mimeType)
                        })
                        CookieManager.getInstance().setAcceptCookie(true)
                        pendingUrl?.let(::loadUrl)
                        webView = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun BrowserStartPage(
    query: String,
    onQueryChange: (String) -> Unit,
    provider: BrowserSearchProvider,
    onProviderChange: (BrowserSearchProvider) -> Unit,
    onSearch: () -> Unit,
) {
    val palette = LocalHiPalette.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Language,
            contentDescription = null,
            tint = palette.primary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text("Hi Browser", color = palette.textPrimary, fontSize = 24.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Text(
            "Search the web or enter a website address",
            color = palette.textSecondary,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = palette.primary) },
            trailingIcon = {
                IconButton(onClick = onSearch) {
                    Icon(Icons.Default.ArrowForward, contentDescription = "Search", tint = palette.primary)
                }
            },
            placeholder = { Text("Search with ${provider.displayName} or enter URL", fontSize = 13.sp) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = palette.textPrimary,
                unfocusedTextColor = palette.textPrimary,
                focusedBorderColor = palette.primary,
                unfocusedBorderColor = palette.surfaceBorder,
                focusedContainerColor = palette.surfaceElevated,
                unfocusedContainerColor = palette.surfaceElevated,
            ),
            modifier = Modifier.fillMaxWidth(),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onSearch() }),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Search,
            ),
        )
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
        ) {
            BrowserSearchProvider.entries.forEach { option ->
                FilterChip(
                    selected = option == provider,
                    onClick = { onProviderChange(option) },
                    label = { Text(option.displayName, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = palette.primary,
                        selectedLabelColor = Color.White,
                        containerColor = palette.surfaceElevated,
                        labelColor = palette.textPrimary,
                    ),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onSearch,
            enabled = query.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
        ) {
            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.height(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Search with ${provider.displayName}")
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "Common third-party ad and tracker hosts are blocked. Website sign-in, access, payments, and protected playback remain controlled by each site.",
            color = palette.textTertiary,
            fontSize = 10.5.sp,
            lineHeight = 14.sp,
        )
    }
}

@Composable
private fun BrowserDownloadPanel(
    downloads: List<BrowserDownload>,
    onClearCompleted: () -> Unit,
) {
    if (downloads.isEmpty()) return
    val palette = LocalHiPalette.current
    val active = downloads.any { it.isActive }
    val downloadPulse by rememberInfiniteTransition(label = "downloadPulse").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(700), repeatMode = RepeatMode.Reverse),
        label = "downloadPulseAlpha",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.surfaceElevated)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        downloads.takeLast(3).forEach { download ->
            val fraction = if (download.totalBytes > 0) {
                (download.downloadedBytes.toFloat() / download.totalBytes.toFloat()).coerceIn(0f, 1f)
            } else 0f
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (download.status) {
                        BrowserDownloadStatus.COMPLETED -> Icons.Default.CheckCircle
                        BrowserDownloadStatus.FAILED -> Icons.Default.ErrorOutline
                        else -> Icons.Default.Download
                    },
                    contentDescription = null,
                    tint = when (download.status) {
                        BrowserDownloadStatus.FAILED -> Color(0xFFEF4444)
                        BrowserDownloadStatus.COMPLETED -> Color(0xFF22C55E)
                        else -> palette.primary
                    },
                    modifier = Modifier.graphicsLayer(
                        alpha = if (download.status == BrowserDownloadStatus.RUNNING) downloadPulse else 1f,
                    ),
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = download.fileName,
                        color = palette.textPrimary,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                    Text(
                        text = when (download.status) {
                            BrowserDownloadStatus.RUNNING -> "${formatBytes(download.downloadedBytes)} of ${formatBytes(download.totalBytes)}  •  ${formatBytes(download.speedBytesPerSecond)}/s"
                            BrowserDownloadStatus.PAUSED -> "Paused • ${formatBytes(download.downloadedBytes)} downloaded"
                            BrowserDownloadStatus.COMPLETED -> "Download complete • ${formatBytes(download.downloadedBytes)}"
                            BrowserDownloadStatus.FAILED -> "Download failed"
                        },
                        color = palette.textSecondary,
                        fontSize = 10.5.sp,
                        maxLines = 1,
                    )
                }
                if (download.status == BrowserDownloadStatus.RUNNING) {
                    Text(
                        text = if (download.totalBytes > 0) "${(fraction * 100).toInt()}%" else "LIVE",
                        color = palette.primary,
                        fontSize = 11.sp,
                    )
                }
            }
            if (download.status == BrowserDownloadStatus.RUNNING || download.status == BrowserDownloadStatus.PAUSED) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        color = palette.primary,
                        trackColor = palette.surfaceBorder,
                        modifier = Modifier.fillMaxWidth().height(5.dp),
                    )
                    if (download.status == BrowserDownloadStatus.RUNNING && fraction == 0f) {
                        LinearProgressIndicator(
                            color = palette.primary.copy(alpha = 0.55f),
                            trackColor = Color.Transparent,
                            modifier = Modifier.fillMaxWidth().height(5.dp),
                        )
                    }
                }
            }
        }
        if (!active) {
            Text(
                text = "Clear download status",
                color = palette.primary,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.End).clickable(onClick = onClearCompleted),
            )
        }
    }
}

private enum class BrowserSearchProvider(
    val displayName: String,
    private val searchBaseUrl: String,
) {
    GOOGLE("Google", "https://www.google.com/search?q="),
    BING("Bing", "https://www.bing.com/search?q="),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q="),
    ;

    fun searchUrl(query: String): String =
        searchBaseUrl + URLEncoder.encode(query, StandardCharsets.UTF_8.name())
}

private fun normalizeBrowserInput(raw: String, provider: BrowserSearchProvider): String? {
    val value = raw.trim()
    if (value.isBlank()) return null
    if (value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)) return value
    if (value.contains('.') && !value.contains(' ')) return "https://$value"
    return provider.searchUrl(value)
}

private object BrowserDownloads {
    private val _downloads = MutableStateFlow<List<BrowserDownload>>(emptyList())
    val downloads = _downloads.asStateFlow()
    private val samples = mutableMapOf<Long, DownloadSample>()

    fun enqueue(
        context: Context,
        rawUrl: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String? = null,
    ) {
        val uri = Uri.parse(rawUrl)
        if (uri.scheme !in setOf("http", "https")) {
            Toast.makeText(context, "Downloads require an HTTP or HTTPS file link", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            val filename = URLUtil.guessFileName(rawUrl, contentDisposition, mimeType)
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .take(120)
            val request = DownloadManager.Request(uri)
                .setTitle(filename)
                .setDescription("Downloading from Hi Player browser")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
            if (userAgent.isNotBlank()) request.addRequestHeader("User-Agent", userAgent)
            CookieManager.getInstance().getCookie(rawUrl)
                ?.takeIf(String::isNotBlank)
                ?.let { cookies -> request.addRequestHeader("Cookie", cookies) }
            mimeType?.takeIf(String::isNotBlank)?.let(request::setMimeType)
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val id = manager.enqueue(request)
            _downloads.value = _downloads.value + BrowserDownload(id = id, fileName = filename)
        }.onSuccess {
            Toast.makeText(context, "Download added to Android Downloads", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "Could not start this download", Toast.LENGTH_SHORT).show()
        }
    }

    fun refresh(context: Context) {
        val existing = _downloads.value
        if (existing.isEmpty()) return
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val updates = mutableMapOf<Long, BrowserDownload>()
        runCatching {
            manager.query(DownloadManager.Query().setFilterById(*existing.map { it.id }.toLongArray()))?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
                val statusColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                val bytesColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val totalColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val titleColumn = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
                val now = SystemClock.elapsedRealtime()
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val downloaded = cursor.getLong(bytesColumn).coerceAtLeast(0L)
                    val previous = samples[id]
                    val elapsedMs = (now - (previous?.elapsedMs ?: now)).coerceAtLeast(1L)
                    val speed = if (previous == null) 0L else {
                        ((downloaded - previous.bytes).coerceAtLeast(0L) * 1_000L / elapsedMs)
                    }
                    samples[id] = DownloadSample(downloaded, now)
                    val status = when (cursor.getInt(statusColumn)) {
                        DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING -> BrowserDownloadStatus.RUNNING
                        DownloadManager.STATUS_PAUSED -> BrowserDownloadStatus.PAUSED
                        DownloadManager.STATUS_SUCCESSFUL -> BrowserDownloadStatus.COMPLETED
                        else -> BrowserDownloadStatus.FAILED
                    }
                    val old = existing.firstOrNull { it.id == id }
                    updates[id] = BrowserDownload(
                        id = id,
                        fileName = cursor.getString(titleColumn)?.takeIf(String::isNotBlank) ?: old?.fileName ?: "Download",
                        downloadedBytes = downloaded,
                        totalBytes = cursor.getLong(totalColumn).coerceAtLeast(0L),
                        speedBytesPerSecond = if (status == BrowserDownloadStatus.RUNNING) speed else 0L,
                        status = status,
                    )
                }
            }
        }
        _downloads.value = existing.map { download ->
            updates[download.id] ?: download.copy(status = BrowserDownloadStatus.FAILED, speedBytesPerSecond = 0L)
        }
    }

    fun clearCompleted() {
        _downloads.value = _downloads.value.filter { it.isActive }
        samples.keys.retainAll(_downloads.value.map { it.id }.toSet())
    }
}

private data class DownloadSample(val bytes: Long, val elapsedMs: Long)

private data class BrowserDownload(
    val id: Long,
    val fileName: String,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSecond: Long = 0L,
    val status: BrowserDownloadStatus = BrowserDownloadStatus.RUNNING,
) {
    val isActive: Boolean get() = status == BrowserDownloadStatus.RUNNING || status == BrowserDownloadStatus.PAUSED
}

private enum class BrowserDownloadStatus { RUNNING, PAUSED, COMPLETED, FAILED }

private fun formatBytes(value: Long): String = when {
    value <= 0L -> "0 B"
    value < 1_024L -> "$value B"
    value < 1_024L * 1_024L -> "%.1f KB".format(value / 1_024.0)
    value < 1_024L * 1_024L * 1_024L -> "%.1f MB".format(value / (1_024.0 * 1_024.0))
    else -> "%.2f GB".format(value / (1_024.0 * 1_024.0 * 1_024.0))
}

private object BrowserAdBlockList {
    fun load(context: Context): Set<String> = runCatching {
        context.assets.open("adblock_hosts.txt").bufferedReader().useLines { lines ->
            lines.map(String::trim)
                .filter { host -> host.isNotBlank() && !host.startsWith('#') }
                .toSet()
        }
    }.getOrDefault(emptySet())

    fun isBlocked(host: String, blockedHosts: Set<String>): Boolean =
        host.isNotBlank() && blockedHosts.any { blocked -> host == blocked || host.endsWith(".$blocked") }
}
