package com.example.ui.screens

import com.example.ui.theme.hiContainerBorder

import androidx.compose.foundation.horizontalScroll
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
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
import com.example.ui.components.HiPlayerLogoBadge
import com.example.ui.components.rememberHiPlayerLogoPainter
import androidx.compose.ui.graphics.FilterQuality

/** True while the on-screen keyboard is showing. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun hiImeVisible(): Boolean = WindowInsets.isImeVisible

/** Shows just the site ("m.youtube.com") for a web address, like a real browser. */
internal fun hiBrowserDisplayAddress(url: String): String {
    if (!url.startsWith("http://") && !url.startsWith("https://")) return url
    val host = runCatching { Uri.parse(url).host }.getOrNull()
    return host?.removePrefix("www.")?.takeIf { it.isNotBlank() } ?: url
}

private val hiLetterColors = listOf(
    0xFF4285F4, 0xFFE53935, 0xFF43A047, 0xFFFB8C00, 0xFF8E24AA, 0xFF00897B, 0xFF3949AB, 0xFF6D4C41,
)

private fun hiLetterColor(seed: String): Color =
    Color(hiLetterColors[(seed.lowercase().hashCode() and 0x7fffffff) % hiLetterColors.size])

private fun hiHost(url: String): String =
    runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("").removePrefix("www.")

@Composable
private fun HiLetterIcon(label: String, size: Int, fontSize: Int) {
    val color = hiLetterColor(label)
    Box(
        modifier = Modifier.size(size.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(label.take(1).uppercase(), color = color, fontSize = fontSize.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HiTabCountBox(count: Int, color: Color) {
    Box(
        modifier = Modifier.size(24.dp).border(2.dp, color, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (count > 99) ":)" else count.toString(), color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * Hi Browser's address bar, shown at the bottom of the screen:
 * home, search engine, address field, tab counter and menu.
 * The keyboard's Go/Search/Enter key opens what was typed.
 */
@Composable
internal fun HiBrowserToolbar(
    address: String,
    onAddressChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit = {},
    onSubmit: (String) -> Unit,
    isPrivate: Boolean,
    isSecure: Boolean,
    isLoading: Boolean,
    showReload: Boolean,
    tabCount: Int,
    providers: List<String>,
    selectedProvider: String,
    onProviderSelect: (Int) -> Unit,
    onHome: () -> Unit,
    onVoiceSearch: () -> Unit,
    onReloadOrStop: () -> Unit,
    onShowTabs: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalHiPalette.current
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    var providerMenu by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf(TextFieldValue(hiBrowserDisplayAddress(address))) }
    val imeVisible = hiImeVisible()
    var imeSeen by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible) {
        if (imeVisible) {
            imeSeen = true
        } else if (imeSeen) {
            // Back closed the keyboard: leave typing mode so Home, tabs and the menu come back.
            imeSeen = false
            focusManager.clearFocus()
        }
    }

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

    // Transparent container: only the outlined, see-through pill is drawn, so the
    // page (or start page) stays visible around and through the bar.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        val shape = RoundedCornerShape(28.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(shape)
                .background(palette.surface.copy(alpha = if (focused) 0.97f else 0.84f))
                .border(1.dp, if (focused) palette.primary else palette.surfaceBorder, shape)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!focused) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onHome),
                    contentAlignment = Alignment.Center,
                ) {
                    HiPlayerLogoBadge(size = 32.dp)
                }
            } else {
                Spacer(Modifier.width(8.dp))
            }
            Box {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable { providerMenu = true },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isPrivate) {
                        Box(
                            modifier = Modifier.size(34.dp).clip(CircleShape).background(palette.surfaceElevated),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.VisibilityOff, contentDescription = "Private tab", tint = palette.textSecondary, modifier = Modifier.size(18.dp))
                        }
                    } else {
                        HiProviderIcon(selectedProvider, 30.dp)
                    }
                }
                DropdownMenu(expanded = providerMenu, onDismissRequest = { providerMenu = false }) {
                    // Search engines are shown by their logos.
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        providers.forEachIndexed { index, name ->
                            val selected = name == selectedProvider
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        providerMenu = false
                                        onProviderSelect(index)
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                HiProviderIcon(name, 34.dp)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            if (!focused && isSecure && address.isNotBlank()) {
                Icon(Icons.Default.Lock, contentDescription = "Secure", tint = palette.textTertiary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (field.text.isEmpty()) {
                    Text(
                        if (isPrivate) "Search privately or type URL" else "Search or type URL",
                        color = palette.textTertiary,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = field,
                    onValueChange = { value ->
                        field = value
                        if (focused) onAddressChange(value.text)
                    },
                    singleLine = true,
                    textStyle = TextStyle(color = palette.textPrimary, fontSize = 15.sp),
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
                        .onFocusChanged { focused = it.isFocused; onFocusChange(it.isFocused) },
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onShowTabs),
                    contentAlignment = Alignment.Center,
                ) {
                    HiTabCountBox(tabCount, palette.textPrimary)
                }
                IconButton(onClick = onMenu) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Browser menu", tint = palette.textPrimary)
                }
            }
        }
    }
}

/** One action in the browser menu sheet. */
internal data class HiMenuAction(
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val checked: Boolean? = null,
    val onClick: () -> Unit,
)

/** Browser menu as a bottom sheet: two left-to-right scrollable rows of tiles and a quick-action row. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HiBrowserMenuSheet(
    tiles: List<HiMenuAction>,
    onSetDefaultBrowser: (() -> Unit)?,
    tools: List<HiMenuAction>,
    rows: List<HiMenuAction> = emptyList(),
    bottomRow: List<HiMenuAction>,
    onDismiss: () -> Unit,
) {
    val palette = LocalHiPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    fun run(action: HiMenuAction) {
        if (!action.enabled) return
        onDismiss()
        action.onClick()
    }
    ModalBottomSheet(
        modifier = androidx.compose.ui.Modifier.hiContainerBorder(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
        onDismissRequest = onDismiss, sheetState = sheetState, containerColor = palette.background) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HiMenuTileRow(items = tiles + rows, onClick = { run(it) })
            if (onSetDefaultBrowser != null) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp)).hiContainerBorder(RoundedCornerShape(16.dp))
                        .background(palette.primary.copy(alpha = 0.12f))
                        .clickable {
                            onDismiss()
                            onSetDefaultBrowser()
                        }
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Language, contentDescription = null, tint = palette.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Set Hi Player as default browser", color = palette.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (tools.isNotEmpty()) {
                HiMenuTileRow(items = tools, onClick = { run(it) })
            }
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp)).hiContainerBorder(RoundedCornerShape(28.dp))
                    .background(palette.surface)
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                bottomRow.forEach { action ->
                    IconButton(onClick = { run(action) }, enabled = action.enabled) {
                        Icon(
                            action.icon,
                            contentDescription = action.label,
                            tint = when {
                                !action.enabled -> palette.textTertiary
                                action.checked == true -> palette.primary
                                else -> palette.textPrimary
                            },
                        )
                    }
                }
            }
        }
    }
}

/** A row of same-size menu tiles that scrolls left to right when it doesn't fit. */
@Composable
private fun HiMenuTileRow(items: List<HiMenuAction>, onClick: (HiMenuAction) -> Unit) {
    val palette = LocalHiPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            Column(
                modifier = Modifier
                    .width(84.dp)
                    .clip(RoundedCornerShape(18.dp)).hiContainerBorder(RoundedCornerShape(18.dp))
                    .background(if (item.checked == true) palette.primary.copy(alpha = 0.16f) else palette.surface)
                    .clickable(enabled = item.enabled) { onClick(item) }
                    .padding(top = 14.dp, bottom = 10.dp, start = 4.dp, end = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    item.icon,
                    contentDescription = null,
                    tint = when {
                        !item.enabled -> palette.textTertiary
                        else -> palette.primary
                    },
                    modifier = Modifier.size(26.dp),
                )
                Spacer(Modifier.height(6.dp))
                Box(modifier = Modifier.height(30.dp), contentAlignment = Alignment.TopCenter) {
                    Text(
                        item.label,
                        color = if (item.enabled) palette.textPrimary else palette.textTertiary,
                        fontSize = 12.sp,
                        lineHeight = 14.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun HiSegment(selected: Boolean, onClick: () -> Unit, content: @Composable (Color) -> Unit) {
    val palette = LocalHiPalette.current
    Box(
        modifier = Modifier
            .height(40.dp)
            .width(60.dp)
            .clip(RoundedCornerShape(20.dp)).hiContainerBorder(RoundedCornerShape(20.dp))
            .background(if (selected) palette.primary else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content(if (selected) Color.White else palette.textPrimary)
    }
}

internal data class HiBrowserTabCard(
    val id: Int,
    val title: String,
    val url: String,
    val privateTab: Boolean,
    val thumbnail: ImageBitmap? = null,
    val favicon: ImageBitmap? = null,
)

/** Full-screen tab switcher: normal/private switch, page previews, new tab and close all. */
@Composable
internal fun HiBrowserTabSwitcher(
    tabs: List<HiBrowserTabCard>,
    activeTabId: Int,
    startPrivate: Boolean,
    onSelect: (Int) -> Unit,
    onClose: (Int) -> Unit,
    onNewTab: (Boolean) -> Unit,
    onCloseAll: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalHiPalette.current
    var privateMode by remember { mutableStateOf(startPrivate) }
    val shown = tabs.filter { it.privateTab == privateMode }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BrowserNavigationHost(navigation = appNavigation) {
            Column(modifier = Modifier.fillMaxSize().background(palette.background)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back to page", tint = palette.textPrimary)
                }
                Spacer(Modifier.weight(1f))
                Row(modifier = Modifier.clip(RoundedCornerShape(24.dp)).hiContainerBorder(RoundedCornerShape(24.dp)).background(palette.surface).padding(4.dp)) {
                    HiSegment(selected = !privateMode, onClick = { privateMode = false }) { color ->
                        HiTabCountBox(tabs.count { !it.privateTab }, color)
                    }
                    HiSegment(selected = privateMode, onClick = { privateMode = true }) { color ->
                        Icon(Icons.Default.VisibilityOff, contentDescription = "Private tabs", tint = color)
                    }
                }
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(48.dp))
            }
            if (shown.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        if (privateMode) "No private tabs. Pages in private tabs aren't saved to history." else "No open tabs",
                        color = palette.textSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp),
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(shown, key = { it.id }) { tab ->
                        HiBrowserTabCardView(
                            tab = tab,
                            active = tab.id == activeTabId,
                            onSelect = { onSelect(tab.id) },
                            onClose = { onClose(tab.id) },
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().background(palette.surface).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onCloseAll(privateMode) }, enabled = shown.isNotEmpty()) {
                    Icon(Icons.Default.ClearAll, contentDescription = "Close all tabs", tint = if (shown.isEmpty()) palette.textTertiary else palette.textPrimary)
                }
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .width(72.dp)
                        .clip(RoundedCornerShape(16.dp)).hiContainerBorder(RoundedCornerShape(16.dp))
                        .background(palette.primary)
                        .clickable { onNewTab(privateMode) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Add, contentDescription = if (privateMode) "New private tab" else "New tab", tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Done") }
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
    val host = hiHost(tab.url)
    val shape = RoundedCornerShape(18.dp)
    val favicon = tab.favicon
    val thumbnail = tab.thumbnail
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(shape)
            .background(palette.surface)
            .border(if (active) 2.dp else 1.dp, if (active) palette.primary else palette.surfaceBorder, shape)
            .clickable(onClick = onSelect),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                favicon != null -> Image(favicon, contentDescription = null, modifier = Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).hiContainerBorder(RoundedCornerShape(4.dp)))
                tab.privateTab -> Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(18.dp))
                tab.url.isBlank() -> Image(
                    painter = rememberHiPlayerLogoPainter(),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp).clip(RoundedCornerShape(5.dp)).hiContainerBorder(RoundedCornerShape(5.dp)),
                )
                else -> Icon(Icons.Default.Language, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (tab.url.isBlank()) "Start page" else tab.title.ifBlank { host },
                color = palette.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close tab", tint = palette.textSecondary, modifier = Modifier.size(18.dp))
            }
        }
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f).background(palette.surfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbnail != null && tab.url.isNotBlank()) {
                Image(
                    thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (tab.url.isBlank()) {
                HiPlayerLogoBadge(size = 64.dp)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    HiSiteIcon(tab.url, host.ifBlank { tab.title }, 48.dp)
                    Spacer(Modifier.height(6.dp))
                    Text(host, color = palette.textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Start page: logo and editable shortcut tiles. Searching happens in the bottom address bar. */
@Composable
internal fun HiBrowserHome(
    isPrivate: Boolean,
    shortcuts: List<Pair<String, String>>,
    adBlockEnabled: Boolean,
    onVisit: (String) -> Unit,
    onAddShortcut: (String, String) -> Unit,
    onRemoveShortcut: (String) -> Unit,
    modifier: Modifier = Modifier,
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val palette = LocalHiPalette.current
    var editing by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
            ) {},
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(96.dp))
            HiPlayerLogoBadge(size = 104.dp)
            Spacer(Modifier.height(14.dp))
            Text(
                if (isPrivate) "Private browsing" else "Hi Browser",
                color = palette.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            if (isPrivate) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Pages you open in private tabs aren't saved to history.",
                    color = palette.textSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(40.dp))
            val cells: List<Pair<String, String>?> = buildList {
                addAll(shortcuts)
                if (shortcuts.size < 12) add(null)
            }
            cells.chunked(4).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    row.forEach { cell ->
                        if (cell == null) {
                            HiShortcutTile("Add", null, false, Modifier.weight(1f)) { adding = true }
                        } else {
                            HiShortcutTile(cell.first, cell.second, editing, Modifier.weight(1f)) {
                                if (editing) onRemoveShortcut(cell.second) else onVisit(cell.second)
                            }
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(14.dp))
            }
            if (editing) {
                Text("Tap a shortcut to remove it", color = palette.textTertiary, fontSize = 12.sp)
            }
            Spacer(Modifier.height(32.dp))
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
            Spacer(Modifier.height(bottomInset + 24.dp))
        }
        IconButton(
            onClick = { editing = !editing },
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
        ) {
            Icon(
                if (editing) Icons.Default.Check else Icons.Default.Edit,
                contentDescription = if (editing) "Done editing" else "Edit shortcuts",
                tint = palette.textPrimary,
            )
        }
    }
    if (adding) {
        HiAddShortcutDialog(
            onAdd = { name, url ->
                adding = false
                onAddShortcut(name, url)
            },
            onDismiss = { adding = false },
        )
    }
}

@Composable
private fun HiShortcutTile(
    label: String,
    url: String?,
    editing: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val palette = LocalHiPalette.current
    Column(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(64.dp)) {
            if (url == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(18.dp))
                        .background(palette.surface)
                        .border(1.dp, palette.surfaceBorder, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = palette.textPrimary, modifier = Modifier.size(28.dp))
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(18.dp))
                        .background(palette.surface)
                        .border(1.dp, palette.surfaceBorder, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    HiSiteIcon(url, label, 44.dp, rounded = true)
                }
            }
            if (editing && url != null) {
                Box(
                    modifier = Modifier.align(Alignment.TopEnd).size(22.dp).clip(CircleShape).background(Color(0xFFE53935)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = palette.textPrimary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun HiAddShortcutDialog(onAdd: (String, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    AlertDialog(
        modifier = androidx.compose.ui.Modifier.hiContainerBorder(androidx.compose.foundation.shape.RoundedCornerShape(28.dp)),
        onDismissRequest = onDismiss,
        title = { Text("Add shortcut") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Web address") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = url.isNotBlank(),
                onClick = {
                    val trimmed = url.trim()
                    val full = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
                    onAdd(name.trim().ifBlank { hiHost(full).ifBlank { full } }, full)
                },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

internal enum class HiLibrarySection(val label: String, val icon: ImageVector) {
    BOOKMARKS("Bookmarks", Icons.Default.Bookmark),
    HISTORY("History", Icons.Default.History),
    DOWNLOADS("Downloads", Icons.Default.Download),
}

/** Full-screen library with Bookmarks, History and Downloads, each searchable. */
@Composable
internal fun HiBrowserLibrary(
    initialSection: HiLibrarySection,
    appNavigation: @Composable () -> Unit = {},
    bookmarks: List<Pair<String, String>>,
    history: List<Pair<String, String>>,
    onOpen: (String) -> Unit,
    onRemove: (HiLibrarySection, String) -> Unit,
    onClearHistory: () -> Unit,
    downloadsContent: @Composable (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalHiPalette.current
    var section by remember { mutableStateOf(initialSection) }
    var query by remember(section) { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(modifier = Modifier.fillMaxSize().background(palette.background)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Close", tint = palette.textPrimary)
                }
                Spacer(Modifier.weight(1f))
                Row(modifier = Modifier.clip(RoundedCornerShape(24.dp)).hiContainerBorder(RoundedCornerShape(24.dp)).background(palette.surface).padding(4.dp)) {
                    HiLibrarySection.entries.forEach { entry ->
                        HiSegment(selected = section == entry, onClick = { section = entry }) { color ->
                            Icon(entry.icon, contentDescription = entry.label, tint = color)
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                if (section == HiLibrarySection.HISTORY && history.isNotEmpty()) {
                    TextButton(onClick = onClearHistory) { Text("Clear") }
                } else {
                    Spacer(Modifier.width(48.dp))
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .height(50.dp)
                    .clip(RoundedCornerShape(25.dp)).hiContainerBorder(RoundedCornerShape(25.dp))
                    .background(palette.surfaceElevated)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = palette.textTertiary)
                Spacer(Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text("Search ${section.label}", color = palette.textTertiary, fontSize = 15.sp)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = palette.textPrimary, fontSize = 15.sp),
                        cursorBrush = SolidColor(palette.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search", tint = palette.textTertiary)
                    }
                }
            }
            Text(
                section.label.uppercase(),
                color = palette.textTertiary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 28.dp, top = 14.dp, bottom = 8.dp),
            )
            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
                if (section == HiLibrarySection.DOWNLOADS) {
                    downloadsContent(query)
                } else {
                    val source = if (section == HiLibrarySection.BOOKMARKS) bookmarks else history
                    val items = source.filter { (title, url) ->
                        query.isBlank() || title.contains(query, ignoreCase = true) || url.contains(query, ignoreCase = true)
                    }
                    if (items.isEmpty()) {
                        Text(
                            when {
                                query.isNotBlank() -> "Nothing matches \"$query\""
                                section == HiLibrarySection.BOOKMARKS -> "No bookmarks yet. Tap the star in the menu to add one."
                                else -> "No browsing history"
                            },
                            color = palette.textSecondary,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(12.dp),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).hiContainerBorder(RoundedCornerShape(20.dp)).background(palette.surface),
                        ) {
                            itemsIndexed(items) { _, entry ->
                                val title = entry.first
                                val url = entry.second
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onDismiss()
                                            onOpen(url)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    HiSiteIcon(url, hiHost(url).ifBlank { title }, 40.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(title, color = palette.textPrimary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(hiHost(url), color = palette.textTertiary, fontSize = 12.sp, maxLines = 1)
                                    }
                                    IconButton(onClick = { onRemove(section, url) }) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = palette.textTertiary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }
}
