package com.example.lyrics

import android.content.Context
import com.example.model.AudioItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

internal data class LyricsResult(val text: String? = null, val message: String = "Lyrics not found for this track", val retryable: Boolean = true)

/** Real provider results only; no generated lyrics or first-result-without-matching fallback. */
internal class LyricsRepository(context: Context) {
    private val cache = File(context.cacheDir, "audio_lyrics_v1")
    private val client = OkHttpClient.Builder().callTimeout(8, TimeUnit.SECONDS).connectTimeout(5, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS).build()
    private val limit = 512 * 1024

    suspend fun find(track: AudioItem): LyricsResult = withContext(Dispatchers.IO) {
        val key = MessageDigest.getInstance("SHA-256").digest("${track.uri}|${track.sizeBytes}|${track.title}|${track.artist}|${track.durationMs}".toByteArray())
            .joinToString("") { "%02x".format(it) }
        val stored = File(cache, "$key.lrc")
        val localPath = track.path.takeIf { it.startsWith('/') } ?: track.uri.takeIf { it.scheme == "file" }?.path
        val sidecar = localPath?.let { File(File(it).parentFile, File(it).nameWithoutExtension + ".lrc") }
        for (file in listOfNotNull(sidecar, stored)) {
            if (file.isFile && file.canRead() && file.length() in 1L..limit.toLong()) {
                val text = runCatching { file.readText() }.getOrNull()
                if (!text.isNullOrBlank() && LyricsText.parse(text).displayLines.isNotEmpty()) return@withContext LyricsResult(text, "", false)
            }
        }
        val query = LyricsQuery.from(track.title, track.artist)
        if (query.title.isBlank()) return@withContext LyricsResult(message = "This file has no song title to search")
        var networkError = false
        suspend fun json(url: String): String? = try { request(url) } catch (e: CancellationException) { throw e } catch (_: Exception) { networkError = true; null }
        fun result(obj: JSONObject?): LyricsResult? {
            if (obj == null) return null
            if (obj.optBoolean("instrumental")) return LyricsResult(message = "Instrumental track — no lyrics", retryable = false)
            val synced = obj.optString("syncedLyrics").takeUnless { it == "null" }.orEmpty()
            val plain = obj.optString("plainLyrics").takeUnless { it == "null" }.orEmpty()
            val text = synced.takeIf { LyricsText.parse(it).timed.isNotEmpty() } ?: plain.takeIf { it.isNotBlank() } ?: return null
            runCatching {
                cache.mkdirs()
                stored.writeText(text)
                cache.listFiles()?.sortedByDescending { it.lastModified() }?.drop(64)?.forEach { it.delete() }
            }
            return LyricsResult(text, "", false)
        }
        if (query.artist.isNotBlank()) {
            val url = "https://lrclib.net/api/get".toHttpUrl().newBuilder().addQueryParameter("track_name", query.title).addQueryParameter("artist_name", query.artist)
            if (track.durationMs > 0) url.addQueryParameter("duration", (track.durationMs / 1000).toString())
            val obj = json(url.build().toString())?.let { runCatching { JSONObject(it) }.getOrNull() }
            result(obj)?.let { return@withContext it }
        }
        val searches = listOf("${query.artist} ${query.title}".trim(), query.title).distinct()
        for (search in searches) {
            val url = "https://lrclib.net/api/search".toHttpUrl().newBuilder().addQueryParameter("q", search).build()
            val array = json(url.toString())?.let { runCatching { JSONArray(it) }.getOrNull() } ?: continue
            val best = (0 until array.length()).mapNotNull { i -> array.optJSONObject(i) }
                .map { obj -> obj to query.score(obj.optString("trackName"), obj.optString("artistName"), track.durationMs, obj.optDouble("duration", 0.0)) }
                .filter { it.second >= 0.6 }.sortedByDescending { it.second }
            for ((obj, _) in best) result(obj)?.let { return@withContext it }
        }
        if (query.artist.isNotBlank()) {
            val url = "https://api.lyrics.ovh/v1".toHttpUrl().newBuilder().addPathSegment(query.artist).addPathSegment(query.title).build()
            val text = json(url.toString())?.let { runCatching { JSONObject(it).optString("lyrics").takeUnless { it == "null" } }.getOrNull() }
            if (!text.isNullOrBlank()) return@withContext result(JSONObject().put("plainLyrics", text))!!
        }
        LyricsResult(message = if (networkError) "Could not load lyrics. Check your connection and retry." else "Lyrics not found for this track")
    }

    private suspend fun request(url: String): String? {
        val call = client.newCall(Request.Builder().url(url).header("User-Agent", "HiPlayer (https://github.com/hi033029-ai/hi-player)").build())
        val response = suspendCancellableCoroutine<Response> { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response, onCancellation = { _, value, _ -> value.close() })
                }
            })
        }
        return response.use {
            if (it.code == 404) return@use null
            if (!it.isSuccessful) throw IOException("Lyrics service HTTP ${it.code}")
            val body = it.body ?: return@use null
            if (body.contentLength() > limit) throw IOException("Lyrics response too large")
            body.charStream().use { reader ->
                val result = StringBuilder(); val buffer = CharArray(4096)
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    if (result.length + count > limit) throw IOException("Lyrics response too large")
                    result.append(buffer, 0, count)
                }
                result.toString()
            }
        }
    }
}

internal data class LyricsQuery(val title: String, val artist: String) {
    fun score(otherTitle: String, otherArtist: String, durationMs: Long, seconds: Double): Double {
        fun overlap(a: String, b: String): Double {
            val left = normalize(a).split(' ').filter(String::isNotBlank).toSet()
            val right = normalize(b).split(' ').filter(String::isNotBlank).toSet()
            return if (left.isEmpty() || right.isEmpty()) 0.0 else left.intersect(right).size.toDouble() / maxOf(left.size, right.size)
        }
        val titleScore = overlap(title, otherTitle)
        val artistScore = if (artist.isBlank()) 1.0 else overlap(artist, otherArtist)
        if (titleScore < .6 || artistScore < .35) return 0.0
        if (durationMs > 0 && seconds > 0 && kotlin.math.abs(durationMs / 1000.0 - seconds) > 20) return 0.0
        return titleScore * .8 + artistScore * .2
    }
    companion object {
        private fun clean(text: String) = text.replace(Regex("""\.(?:mp3|flac|m4a|aac|wav|ogg|opus)$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""[\[(](?:official|lyrics?|audio|video|hd|hq|\d+\s*kbps).*?[\])]""", RegexOption.IGNORE_CASE), "").trim()
        fun normalize(text: String) = text.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{M}\\p{N}]+"), " ").trim()
        fun from(rawTitle: String, rawArtist: String): LyricsQuery {
            var title = clean(rawTitle)
            var artist = clean(rawArtist).takeUnless { normalize(it) in setOf("", "unknown", "unknown artist", "whatsapp") || it == "<unknown>" }.orEmpty()
            if (artist.isBlank()) {
                val parts = title.split(Regex("\\s+[-–—]\\s+"), limit = 2)
                if (parts.size == 2) { artist = parts[0].trim(); title = parts[1].trim() }
            }
            return LyricsQuery(title, artist)
        }
    }
}
