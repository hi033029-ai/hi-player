package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Site icons for shortcut tiles, history rows and search engines: bundled logos
 * for well-known sites, then favicons the browser saved while visiting a site,
 * then a coloured letter.
 */
internal object HiSiteIcons {
    private val bundledData = mapOf(
        "google" to HiSiteIconData.GOOGLE,
        "bing" to HiSiteIconData.BING,
        "duckduckgo" to HiSiteIconData.DUCKDUCKGO,
        "youtube" to HiSiteIconData.YOUTUBE,
        "wikipedia" to HiSiteIconData.WIKIPEDIA,
        "x" to HiSiteIconData.X,
    )
    private val memory = LruCache<String, ImageBitmap>(96)
    private val missing = ConcurrentHashMap.newKeySet<String>()
    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "hi-favicon-writer").apply { priority = Thread.MIN_PRIORITY }
    }
    private val _revision = MutableStateFlow(0)
    /** Bumps whenever a new favicon is saved so visible icons can refresh. */
    val revision = _revision.asStateFlow()

    fun hostOf(url: String): String? = runCatching { Uri.parse(url.trim()).host }
        .getOrNull()
        ?.lowercase()
        ?.trimEnd('.')
        ?.removePrefix("www.")
        ?.takeIf { it.isNotBlank() }

    private fun bundledKeyForHost(rawHost: String): String? {
        val host = rawHost.removePrefix("m.").removePrefix("mobile.")
        return when {
            host == "google.com" || host.endsWith(".google.com") || Regex("^google\\.[a-z.]{2,6}$").matches(host) -> "google"
            host == "bing.com" || host.endsWith(".bing.com") -> "bing"
            host == "duckduckgo.com" || host.endsWith(".duckduckgo.com") || host == "duck.com" -> "duckduckgo"
            host == "youtube.com" || host.endsWith(".youtube.com") || host == "youtu.be" -> "youtube"
            host == "wikipedia.org" || host.endsWith(".wikipedia.org") -> "wikipedia"
            host == "x.com" || host.endsWith(".x.com") || host == "twitter.com" || host.endsWith(".twitter.com") -> "x"
            else -> null
        }
    }

    /** A bundled logo by key ("google", "bing", "duckduckgo", …); cheap after the first call. */
    fun bundled(key: String): ImageBitmap? {
        val cacheKey = "@$key"
        memory.get(cacheKey)?.let { return it }
        val data = bundledData[key] ?: return null
        val bitmap = runCatching {
            val bytes = Base64.decode(data, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull() ?: return null
        return bitmap.asImageBitmap().also { memory.put(cacheKey, it) }
    }

    /** Icon already in memory (or bundled) for this address, without touching disk. */
    fun peek(url: String): ImageBitmap? {
        val host = hostOf(url) ?: return null
        bundledKeyForHost(host)?.let { return bundled(it) }
        return memory.get(host)
    }

    /** Full lookup including saved favicons. Call off the main thread. */
    fun load(context: Context, url: String): ImageBitmap? {
        val host = hostOf(url) ?: return null
        bundledKeyForHost(host)?.let { return bundled(it) }
        memory.get(host)?.let { return it }
        if (host in missing) return null
        val file = File(directory(context), fileName(host))
        val bitmap = if (file.isFile) runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull() else null
        if (bitmap == null) {
            missing += host
            return null
        }
        return bitmap.asImageBitmap().also { memory.put(host, it) }
    }

    /** Remembers the favicon a page reported so tiles and history rows can show it. */
    fun saveFavicon(context: Context, pageUrl: String, icon: Bitmap?) {
        if (icon == null || icon.isRecycled || icon.width < 16 || icon.height < 16) return
        val host = hostOf(pageUrl) ?: return
        if (bundledKeyForHost(host) != null) return
        val existing = memory.get(host)
        if (existing != null && existing.width >= icon.width) return
        val maxSide = 96
        val copy = runCatching {
            if (icon.width > maxSide || icon.height > maxSide) {
                val scale = maxSide.toFloat() / maxOf(icon.width, icon.height)
                Bitmap.createScaledBitmap(icon, (icon.width * scale).toInt().coerceAtLeast(1), (icon.height * scale).toInt().coerceAtLeast(1), true)
            } else {
                icon.copy(Bitmap.Config.ARGB_8888, false)
            }
        }.getOrNull() ?: return
        memory.put(host, copy.asImageBitmap())
        missing.remove(host)
        _revision.value = _revision.value + 1
        val appContext = context.applicationContext
        writer.execute {
            runCatching {
                val dir = directory(appContext)
                dir.mkdirs()
                File(dir, fileName(host)).outputStream().use { out -> copy.compress(Bitmap.CompressFormat.PNG, 100, out) }
            }
        }
    }

    fun clearSaved(context: Context) {
        memory.evictAll()
        missing.clear()
        writer.execute { runCatching { directory(context.applicationContext).deleteRecursively() } }
        _revision.value = _revision.value + 1
    }

    private fun directory(context: Context) = File(context.applicationContext.filesDir, "hi_favicons")

    private fun fileName(host: String): String =
        host.filter { it.isLetterOrDigit() || it == '.' || it == '-' }.take(120) + ".png"
}

private val hiSiteLetterColors = listOf(
    0xFF4285F4, 0xFFE53935, 0xFF43A047, 0xFFFB8C00, 0xFF8E24AA, 0xFF00897B, 0xFF3949AB, 0xFF6D4C41,
)

internal fun hiSiteLetterColor(seed: String): Color =
    Color(hiSiteLetterColors[(seed.lowercase().hashCode() and 0x7fffffff) % hiSiteLetterColors.size])

/**
 * A site's logo for [url] (bundled logo → saved favicon), or a coloured first
 * letter of [label] when the site has no icon yet.
 */
@Composable
internal fun HiSiteIcon(
    url: String,
    label: String,
    size: Dp,
    modifier: Modifier = Modifier,
    rounded: Boolean = false,
) {
    val context = LocalContext.current
    val revision by HiSiteIcons.revision.collectAsState()
    val icon by produceState(initialValue = HiSiteIcons.peek(url), url, revision) {
        if (value == null) value = withContext(Dispatchers.IO) { HiSiteIcons.load(context, url) }
    }
    val shape = if (rounded) RoundedCornerShape(size * 0.28f) else CircleShape
    val image = icon
    if (image != null) {
        Box(
            modifier = modifier.size(size).clip(shape).background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.Medium,
                modifier = Modifier.size(size).padding(size * 0.16f),
            )
        }
    } else {
        val color = hiSiteLetterColor(label.ifBlank { url })
        Box(
            modifier = modifier.size(size).clip(shape).background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label.trim().take(1).uppercase(),
                color = color,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** A search engine's logo, by its display name ("Google", "Bing", "DuckDuckGo"). */
@Composable
internal fun HiProviderIcon(name: String, size: Dp, modifier: Modifier = Modifier) {
    val bitmap = HiSiteIcons.bundled(name.lowercase().replace(" ", ""))
    if (bitmap != null) {
        Box(
            modifier = modifier.size(size).clip(CircleShape).background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = bitmap,
                contentDescription = name,
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.Medium,
                modifier = Modifier.size(size).padding(size * 0.14f),
            )
        }
    } else {
        val color = hiSiteLetterColor(name)
        Box(
            modifier = modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(name.take(1), color = color, fontSize = (size.value * 0.45f).sp, fontWeight = FontWeight.Bold)
        }
    }
}
