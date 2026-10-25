package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.LocalHiPalette

/**
 * Hi Browser's single top bar: home, address pill, tab counter, and menu.
 * The keyboard's Go/Search/Enter key submits the typed text.
 */
@Composable
internal fun HiBrowserToolbar(
    address: String,
    onAddressChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    isPrivate: Boolean,
    isSecure: Boolean,
    isLoading: Boolean,
    showReload: Boolean,
    tabCount: Int,
    onHome: () -> Unit,
    onVoiceSearch: () -> Unit,
    onReloadOrStop: () -> Unit,
    onShowTabs: () -> Unit,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    menuContent: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalHiPalette.current
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf(TextFieldValue(address)) }

    // Like a real browser: show just the site while browsing, the full address while editing.
    LaunchedEffect(address, focused) {
        if (!focused) field = TextFieldValue(hiBrowserDisplayAddress(address))
    }
    LaunchedEffect(focused) {
        if (focused) field = TextFieldValue(address, selection = TextRange(0, address.length))
    }

    fun submit() {
        val text = field.text
        focusManager.clearFocus()
        if (text.isNotBlank()) onSubmit(text)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.surface)
            .padding(horizontal = 2.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onHome) {
            Icon(Icons.Default.Home, contentDescription = "Home", tint = palette.textPrimary)
        }
        val pillShape = RoundedCornerShape(22.dp)
        Row(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clip(pillShape)
                .background(palette.surfaceElevated)
                .border(1.dp, if (focused) palette.primary else Color.Transparent, pillShape)
                .padding(start = 12.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val leading = when {
                focused || address.isBlank() -> Icons.Default.Search
                isPrivate -> Icons.Default.VisibilityOff
                isSecure -> Icons.Default.Lock
                else -> Icons.Default.Info
            }
            Icon(leading, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (field.text.isEmpty()) {
                    Text(
                        if (isPrivate) "Search privately or type URL" else "Search or type web address",
                        color = palette.textTertiary,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = field,
                    onValueChange = { value ->
                        field = value
                        onAddressChange(value.text)
                    },
                    singleLine = true,
                    textStyle = TextStyle(color = palette.textPrimary, fontSize = 14.sp),
                    cursorBrush = SolidColor(palette.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(
                        onGo = { submit() },
                        onSearch = { submit() },
                        onDone = { submit() },
                        onSend = { submit() },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focused = it.isFocused },
                )
            }
            when {
                focused && field.text.isNotEmpty() -> IconButton(onClick = {
                    field = TextFieldValue("")
                    onAddressChange("")
                }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = palette.textSecondary)
                }
                focused || !showReload -> IconButton(onClick = onVoiceSearch) {
                    Icon(Icons.Default.Mic, contentDescription = "Voice search", tint = palette.primary)
                }
                else -> IconButton(onClick = onReloadOrStop) {
                    Icon(
                        if (isLoading) Icons.Default.Close else Icons.Default.Refresh,
                        contentDescription = if (isLoading) "Stop" else "Reload",
                        tint = palette.textSecondary,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .padding(start = 4.dp)
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onShowTabs),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .border(2.dp, palette.textPrimary, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(tabCount.toString(), color = palette.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Box {
            IconButton(onClick = { onMenuExpandedChange(true) }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Browser menu", tint = palette.textPrimary)
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { onMenuExpandedChange(false) },
                content = menuContent,
            )
        }
    }
}

internal fun hiBrowserDisplayAddress(url: String): String {
    if (!url.startsWith("http://") && !url.startsWith("https://")) return url
    val host = runCatching { Uri.parse(url).host }.getOrNull()
    return host?.removePrefix("www.")?.takeIf { it.isNotBlank() } ?: url
}

internal data class HiBrowserTabCard(
    val id: Int,
    val title: String,
    val url: String,
    val privateTab: Boolean,
)

/** Full-screen tab switcher with a grid of tab cards. */
@Composable
internal fun HiBrowserTabSwitcher(
    tabs: List<HiBrowserTabCard>,
    activeTabId: Int,
    onSelect: (Int) -> Unit,
    onClose: (Int) -> Unit,
    onNewTab: () -> Unit,
    onNewPrivateTab: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalHiPalette.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(modifier = Modifier.fillMaxSize().background(palette.background)) {
            Row(
                modifier = Modifier.fillMaxWidth().background(palette.surface).padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back to page", tint = palette.textPrimary)
                }
                Text(
                    "${tabs.size} ${if (tabs.size == 1) "tab" else "tabs"}",
                    color = palette.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onNewPrivateTab) {
                    Icon(Icons.Default.VisibilityOff, contentDescription = "New private tab", tint = palette.textPrimary)
                }
                IconButton(onClick = onNewTab) {
                    Icon(Icons.Default.Add, contentDescription = "New tab", tint = palette.textPrimary)
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(tabs, key = { it.id }) { tab ->
                    HiBrowserTabCardView(
                        tab = tab,
                        active = tab.id == activeTabId,
                        onSelect = { onSelect(tab.id) },
                        onClose = { onClose(tab.id) },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().background(palette.surface).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onNewPrivateTab, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.VisibilityOff, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Private tab")
                }
                Button(onClick = onNewTab, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("New tab")
                }
            }
        }
    }
}

@Composable
private fun HiBrowserTabCardView(
    tab: HiBrowserTabCard,
    active: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    val palette = LocalHiPalette.current
    val host = runCatching { Uri.parse(tab.url).host.orEmpty() }.getOrDefault("").removePrefix("www.")
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .clip(shape)
            .background(palette.surface)
            .border(if (active) 2.dp else 1.dp, if (active) palette.primary else palette.surfaceBorder, shape)
            .clickable(onClick = onSelect),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                tab.title.ifBlank { "New tab" },
                color = palette.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close ${tab.title}", tint = palette.textSecondary, modifier = Modifier.size(18.dp))
            }
        }
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f).background(palette.surfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(palette.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        tab.privateTab -> Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = palette.primary)
                        host.isBlank() -> Icon(Icons.Default.Home, contentDescription = null, tint = palette.primary)
                        else -> Text(host.take(1).uppercase(), color = palette.primary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    host.ifBlank { if (tab.privateTab) "Private tab" else "New tab" },
                    color = palette.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}

private data class HiBrowserShortcut(val label: String, val url: String, val tint: Color)

private val hiBrowserShortcuts = listOf(
    HiBrowserShortcut("Google", "https://www.google.com", Color(0xFF4285F4)),
    HiBrowserShortcut("YouTube", "https://m.youtube.com", Color(0xFFE53935)),
    HiBrowserShortcut("Wikipedia", "https://www.wikipedia.org", Color(0xFF757575)),
    HiBrowserShortcut("DuckDuckGo", "https://duckduckgo.com", Color(0xFFDE5833)),
)

/** New-tab page shown under the toolbar. Searching happens in the toolbar's address bar. */
@Composable
internal fun HiBrowserHome(
    isPrivate: Boolean,
    providers: List<String>,
    selectedProvider: String,
    onProviderSelect: (Int) -> Unit,
    recent: List<Pair<String, String>>,
    adBlockEnabled: Boolean,
    onVisit: (String) -> Unit,
    onVoiceSearch: () -> Unit,
    onScanQr: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDownloads: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalHiPalette.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(72.dp).clip(CircleShape).background(palette.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (isPrivate) Icons.Default.VisibilityOff else Icons.Default.Language,
                contentDescription = null,
                tint = palette.primary,
                modifier = Modifier.size(38.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            if (isPrivate) "Private tab" else "Hi Browser",
            color = palette.textPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
        )
        if (isPrivate) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Pages you open in this tab aren't saved to history.",
                color = palette.textSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            HiBrowserHomeTile("Voice", onVoiceSearch, Modifier.weight(1f)) {
                Icon(Icons.Default.Mic, contentDescription = null, tint = palette.primary)
            }
            HiBrowserHomeTile("Scan QR", onScanQr, Modifier.weight(1f)) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = palette.primary)
            }
            HiBrowserHomeTile("Bookmarks", onOpenBookmarks, Modifier.weight(1f)) {
                Icon(Icons.Default.Bookmark, contentDescription = null, tint = palette.primary)
            }
            HiBrowserHomeTile("History", onOpenHistory, Modifier.weight(1f)) {
                Icon(Icons.Default.History, contentDescription = null, tint = palette.primary)
            }
            HiBrowserHomeTile("Downloads", onOpenDownloads, Modifier.weight(1f)) {
                Icon(Icons.Default.Download, contentDescription = null, tint = palette.primary)
            }
        }

        Spacer(Modifier.height(24.dp))
        HiBrowserSectionTitle("Shortcuts")
        Row(modifier = Modifier.fillMaxWidth()) {
            hiBrowserShortcuts.forEach { shortcut ->
                HiBrowserHomeTile(
                    shortcut.label,
                    { onVisit(shortcut.url) },
                    Modifier.weight(1f),
                    circleColor = shortcut.tint.copy(alpha = 0.14f),
                ) {
                    Text(shortcut.label.take(1), color = shortcut.tint, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (recent.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            HiBrowserSectionTitle("Recent")
            recent.forEach { (title, url) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.surface)
                        .clickable { onVisit(url) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Language, contentDescription = null, tint = palette.textTertiary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, color = palette.textPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault(""),
                            color = palette.textTertiary,
                            fontSize = 11.sp,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        HiBrowserSectionTitle("Search engine")
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            providers.forEachIndexed { index, name ->
                val selected = name == selectedProvider
                Text(
                    name,
                    color = if (selected) Color.White else palette.textPrimary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) palette.primary else palette.surfaceElevated)
                        .clickable { onProviderSelect(index) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Shield,
                contentDescription = null,
                tint = if (adBlockEnabled) Color(0xFF22A06B) else palette.textTertiary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (adBlockEnabled) "Ad blocking is on" else "Ad blocking is off",
                color = palette.textTertiary,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun HiBrowserSectionTitle(text: String) {
    val palette = LocalHiPalette.current
    Text(
        text,
        color = palette.textPrimary,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    )
}

@Composable
private fun HiBrowserHomeTile(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    circleColor: Color? = null,
    icon: @Composable () -> Unit,
) {
    val palette = LocalHiPalette.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape).background(circleColor ?: palette.surfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = palette.textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}