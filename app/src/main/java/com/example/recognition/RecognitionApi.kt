package com.example.recognition

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class RecognizedSong(
    val title: String,
    val artist: String,
    val score: Double,
)

object AcoustIdApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun lookup(fingerprint: String, durationSeconds: Int): Result<RecognizedSong?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val clientId = BuildConfig.ACOUSTID_CLIENT_ID.trim()
                if (clientId.isBlank()) throw IOException("AcoustID client ID is not configured")
                val fp = URLEncoder.encode(fingerprint, "UTF-8")
                val url = "https://api.acoustid.org/v2/lookup" +
                    "?client=$clientId&format=json&meta=recordings+releasegroups" +
                    "&duration=${durationSeconds.coerceAtLeast(1)}&fingerprint=$fp"
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .header("User-Agent", "HiPlayer/1.0 (Android music recognition)")
                    .build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val message = runCatching {
                            JSONObject(body).optJSONObject("error")?.optString("message")
                        }.getOrNull().orEmpty()
                        throw IOException(
                            if (message.isNotBlank()) "AcoustID lookup failed: $message"
                            else "AcoustID lookup failed: HTTP ${response.code}"
                        )
                    }
                    if (body.isBlank()) throw IOException("Empty AcoustID response")
                    val root = JSONObject(body)
                    if (root.optString("status") != "ok") {
                        val error = root.optJSONObject("error")
                        val code = error?.optInt("code", -1) ?: -1
                        val message = error?.optString("message").orEmpty()
                        throw IOException(
                            if (message.isNotBlank()) "AcoustID error $code: $message"
                            else "AcoustID returned status ${root.optString("status", "unknown")}",
                        )
                    }
                    parse(root)
                }
            }
        }

    private fun parse(root: JSONObject): RecognizedSong? {
        val results = root.optJSONArray("results") ?: return null
        var best: RecognizedSong? = null
        for (index in 0 until results.length()) {
            val result = results.optJSONObject(index) ?: continue
            val score = result.optDouble("score", 0.0)
            val recordings = result.optJSONArray("recordings") ?: continue
            for (recordingIndex in 0 until recordings.length()) {
                val recording = recordings.optJSONObject(recordingIndex) ?: continue
                val title = recording.optString("title").trim()
                val artists = recording.optJSONArray("artists")
                val artist = artists?.optJSONObject(0)?.optString("name")?.trim().orEmpty()
                if (title.isNotBlank() && artist.isNotBlank() && (best == null || score > best!!.score)) {
                    best = RecognizedSong(title, artist, score)
                }
            }
        }
        return best
    }
}
