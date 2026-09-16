package com.example.recognition

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/** Free MusicBrainz metadata search. MusicBrainz requires an identifying User-Agent. */
data class MusicBrainzSong(
    val title: String,
    val artist: String,
    val recordingMbid: String?,
    val releaseTitle: String?,
    val releaseYear: Int?,
)

object MusicBrainzApi {
    private val client = OkHttpClient()
    private const val USER_AGENT = "HiPlayer/1.0 (Android music recognition)"

    suspend fun findRecording(song: RecognizedSong): Result<MusicBrainzSong?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val queries = listOf(
                    "recording:\"${escape(song.title)}\" AND artist:\"${escape(song.artist)}\"",
                    "recording:${escape(song.title)} AND artist:${escape(song.artist)}",
                    "recording:\"${escape(song.title)}\"",
                )

                var lastFailure: IOException? = null
                for ((index, query) in queries.withIndex()) {
                    if (index > 0) delay(1_100) // MusicBrainz average limit: 1 request/sec.
                    try {
                        val match = requestAndParse(query, song)
                        if (match != null) return@runCatching match
                    } catch (error: IOException) {
                        lastFailure = error
                    }
                }
                // No MusicBrainz match is not an error: the caller can continue
                // with the original AcoustID artist/title and search YouTube.
                if (lastFailure != null && queries.size == 1) throw lastFailure
                null
            }
        }

    private fun requestAndParse(query: String, source: RecognizedSong): MusicBrainzSong? {
        val url = "https://musicbrainz.org/ws/2/recording/" +
            "?query=${URLEncoder.encode(query, "UTF-8")}&fmt=json&limit=5"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("MusicBrainz lookup failed: HTTP ${response.code}")
            }
            if (body.isBlank()) throw IOException("Empty MusicBrainz response")
            return parse(body, source)
        }
    }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun parse(body: String, source: RecognizedSong): MusicBrainzSong? {
        val recordings = JSONObject(body).optJSONArray("recordings") ?: return null
        if (recordings.length() == 0) return null
        var bestIndex = 0
        var bestScore = Double.NEGATIVE_INFINITY
        for (index in 0 until recordings.length()) {
            val candidate = recordings.optJSONObject(index) ?: continue
            val title = candidate.optString("title").trim()
            val artist = candidate.optJSONArray("artist-credit")?.optJSONObject(0)
                ?.optJSONObject("artist")?.optString("name")?.trim().orEmpty()
            val titleMatch = similarity(title, source.title)
            val artistMatch = similarity(artist, source.artist)
            val score = titleMatch * 0.65 + artistMatch * 0.35 +
                candidate.optDouble("score", 0.0) * 0.05
            if (score > bestScore) {
                bestScore = score
                bestIndex = index
            }
        }
        val recording = recordings.optJSONObject(bestIndex) ?: return null
        val title = recording.optString("title").trim()
        val artistCredit = recording.optJSONArray("artist-credit")
        val artist = artistCredit?.optJSONObject(0)
            ?.optJSONObject("artist")?.optString("name")?.trim().orEmpty()
        if (title.isBlank() || artist.isBlank()) return null

        val releases = recording.optJSONArray("releases")
        val release = releases?.optJSONObject(0)
        val releaseTitle = release?.optString("title")?.trim()?.ifBlank { null }
        val year = release?.optString("date")?.take(4)?.toIntOrNull()
        return MusicBrainzSong(
            title = title,
            artist = artist,
            recordingMbid = recording.optString("id").ifBlank { null },
            releaseTitle = releaseTitle,
            releaseYear = year,
        )
    }

    private fun similarity(left: String, right: String): Double {
        val a = left.lowercase().replace(Regex("[^a-z0-9 ]"), " ").trim()
        val b = right.lowercase().replace(Regex("[^a-z0-9 ]"), " ").trim()
        return when {
            a.isBlank() || b.isBlank() -> 0.0
            a == b -> 1.0
            a.contains(b) || b.contains(a) -> 0.8
            else -> a.split(" ").intersect(b.split(" ").toSet()).size.toDouble() /
                maxOf(a.split(" ").size, b.split(" ").size)
        }
    }
}
