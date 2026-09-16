package com.example.recognition

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/**
 * Free tier: search.list sits in its own dedicated quota bucket of 100 calls/day
 * (separate from the general 10,000-unit pool covering other endpoints) — so this
 * gives roughly 100 song-to-video lookups per day at zero cost. Get a key from
 * Google Cloud Console: enable "YouTube Data API v3" on a project, create an API
 * key under Credentials.
 */
object YouTubeSearchApi {

    private val client = OkHttpClient()

    /** Returns the first matching video ID, or null if nothing was found. */
    suspend fun findVideoId(artist: String, title: String): Result<String?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val apiKey = BuildConfig.YOUTUBE_DATA_API_KEY.trim()
                if (apiKey.isBlank()) throw IOException("YouTube Data API key is not configured")
                val query = URLEncoder.encode("$artist $title official audio", "UTF-8")
                val url = "https://www.googleapis.com/youtube/v3/search" +
                    "?part=snippet&type=video&maxResults=1&q=$query&key=$apiKey"

                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("YouTube search failed: HTTP ${response.code}")
                    val body = response.body?.string() ?: throw IOException("Empty response")
                    parseFirstVideoId(body)
                }
            }
        }

    private fun parseFirstVideoId(json: String): String? {
        val items = JSONObject(json).optJSONArray("items") ?: return null
        if (items.length() == 0) return null
        return items.getJSONObject(0).optJSONObject("id")?.optString("videoId")?.ifBlank { null }
    }
}
