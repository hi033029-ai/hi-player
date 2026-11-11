package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/** Uses the same navigation as user-entered URLs and existing shortcuts. */
@Composable
internal fun PopularWebsiteShortcuts(onVisit: (String) -> Unit) {
    val palette = LocalHiPalette.current
    Column(Modifier.fillMaxWidth()) {
        Text("Popular websites", color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            POPULAR_WEBSITES.forEach { (name, url) ->
                Column(Modifier.width(82.dp).clip(RoundedCornerShape(16.dp))
                    .clickable { onVisit(url) }.padding(vertical = 6.dp)
                    .testTag("popular_site_${name.lowercase()}"),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(58.dp).clip(RoundedCornerShape(18.dp))
                        .background(palette.surfaceElevated)
                        .border(1.dp, palette.surfaceBorder, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                        HiSiteIcon(url, name, 40.dp, rounded = true)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(name, color = palette.textPrimary, fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
