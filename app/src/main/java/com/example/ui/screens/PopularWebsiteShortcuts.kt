package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.auth.SecureWebSessions
import com.example.ui.theme.LocalHiPalette

/** Public site homepages, not credentials, login endpoints or account data. */
internal val POPULAR_WEBSITES = listOf(
    "Facebook" to "https://www.facebook.com/",
    "TikTok" to "https://www.tiktok.com/",
    "Yahoo" to "https://www.yahoo.com/",
    "Instagram" to "https://www.instagram.com/",
    "YouTube" to "https://www.youtube.com/",
    "Reddit" to "https://www.reddit.com/",
    "LinkedIn" to "https://www.linkedin.com/",
    "X" to "https://x.com/",
)

/** Uses a browser-owned session so sites can offer their supported account chooser. */
@Composable
internal fun PopularWebsiteShortcuts(isPrivate: Boolean) {
    val palette = LocalHiPalette.current
    val context = LocalContext.current
    var selected by remember { mutableStateOf<Pair<String, String>?>(null) }
    Column(Modifier.fillMaxWidth()) {
        Text("Popular websites", color = palette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text("Open in your device browser", color = palette.textSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            POPULAR_WEBSITES.forEach { site ->
                Column(Modifier.width(76.dp).clip(RoundedCornerShape(14.dp))
                    .clickable { selected = site }.padding(vertical = 6.dp)
                    .testTag("popular_site_${site.first.lowercase()}"),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.foundation.layout.Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp))
                        .background(palette.surface), contentAlignment = Alignment.Center) {
                        HiSiteIcon(site.second, site.first, 40.dp, rounded = true)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(site.first, color = palette.textPrimary, fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    selected?.let { site ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("Open ${site.first}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Open this website in your device browser. Use its sign-in button to choose an available account or sign in.")
                    Text("The website and browser manage their own accounts and sessions; signing in here does not sign in to Hi Player.")
                    if (isPrivate) Text("This leaves Hi Browser's private tab. Your device browser may use saved accounts and history.")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (SecureWebSessions.open(context, site.second, palette.surface.toArgb()).isFailure) {
                        Toast.makeText(context, "Couldn't open the website. Install or enable a browser with Custom Tabs, then retry.", Toast.LENGTH_LONG).show()
                    }
                    selected = null
                }) { Text("Continue in browser") }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Cancel") } },
        )
    }
}
