package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalHiPalette
import java.net.URI

internal object BrowserHistoryPolicy {
    fun isWebUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme in listOf("https", "http") && !uri.host.isNullOrBlank() && uri.rawUserInfo == null
    }.getOrDefault(false)

    /** Local-only suggestions. Caller suppresses these completely in private tabs. */
    fun suggestions(entries: List<OrbitBrowserEntry>, query: String): List<OrbitBrowserEntry> {
        val term = query.trim()
        return entries.asSequence().filter { isWebUrl(it.url) }
            .distinctBy { it.url }
            .filter { term.isBlank() || it.title.contains(term, true) || it.url.contains(term, true) }
            .take(4).toList()
    }
}

@Composable
internal fun BrowserHistorySuggestions(entries: List<OrbitBrowserEntry>, onOpen: (String) -> Unit) {
    if (entries.isEmpty()) return
    val palette = LocalHiPalette.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
        .clip(RoundedCornerShape(18.dp)).background(palette.surfaceElevated)) {
        Text("Visited websites", color = palette.textTertiary, fontSize = 11.sp,
            modifier = Modifier.padding(start = 14.dp, top = 8.dp))
        entries.forEach { entry ->
            Row(Modifier.fillMaxWidth().clickable { onOpen(entry.url) }.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                HiSiteIcon(entry.url, entry.title, 28.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(entry.title, color = palette.textPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(entry.url, color = palette.textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
