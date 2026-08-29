package com.example.util

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ParsedMediaInfo(
    val cleanTitle: String,
    val year: String?,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null
)

data class MediaRating(
    val source: String,      // "TMDB"
    val voteAverageOutOf10: Double,
    val voteCount: Int,
    val starsOutOf5: Float
)

/**
 * Fetches a public rating for a video from TMDB (themoviedb.org).
 *
 * TMDB was chosen over OMDb/IMDb directly because it has a genuinely free,
 * instantly-approved API key with a generous rate limit and no per-request
 * cost, and its `vote_average` field is a reasonable public stand-in for a
 * "star rating" without scraping IMDb (which its ToS prohibits).
 *
 * A TMDB API key is still required (there's no fully keyless public rating
 * API) - the user pastes their own free key once via Settings; get one at
 * https://www.themoviedb.org/settings/api. Nothing is fetched until a key
 * is present.
 */
object TmdbRatingHelper {

    private const val BASE_URL = "https://api.themoviedb.org/3"

    fun parseFilename(filename: String): ParsedMediaInfo {
        val nameWithoutExt = filename.substringBeforeLast(".")

        // Season/episode detection (S01E02, 1x02, Season 1 Episode 2) - run
        // before the generic tag stripping below so "S01E02" isn't mistaken
        // for release-group noise, and so the title split happens at the
        // season/episode marker for TV files instead of at whatever tag
        // happens to appear first.
        val seasonEpisodeRegex = Regex(
            "(?i)\\bS(\\d{1,2})[.\\s_-]?E(\\d{1,3})\\b|\\b(\\d{1,2})x(\\d{1,3})\\b|\\bSeason[.\\s_-]?(\\d{1,2})[.\\s_-]?Episode[.\\s_-]?(\\d{1,3})\\b"
        )
        val seMatch = seasonEpisodeRegex.find(nameWithoutExt)
        val seasonNumber = seMatch?.let {
            (it.groupValues[1].ifBlank { null } ?: it.groupValues[3].ifBlank { null } ?: it.groupValues[5].ifBlank { null })?.toIntOrNull()
        }
        val episodeNumber = seMatch?.let {
            (it.groupValues[2].ifBlank { null } ?: it.groupValues[4].ifBlank { null } ?: it.groupValues[6].ifBlank { null })?.toIntOrNull()
        }

        val tagRegex = Regex(
            "(?i)\\b(2160p|1080p|720p|480p|4k|uhd|fhd|hd|web-?dl|webrip|bluray|bdrip|dvdrip|hdtv|remux|x264|x265|hevc|h264|h265|hdr|hdr10|dts|aac|ac3|truehd|atmos|5\\.1|7\\.1|10bit|dual\\s?audio|esub|mkv|mp4|avi|wmv|flv|yify|rarbg|eztv)\\b"
        )

        // For TV files, cut the title at the season/episode marker itself so
        // "Show.Name.S01E02.1080p..." yields "Show Name" rather than running
        // the generic tag regex first and potentially keeping "S01E02" as
        // part of the title.
        val splitPart = if (seMatch != null) {
            nameWithoutExt.substring(0, seMatch.range.first)
        } else {
            nameWithoutExt.split(tagRegex).firstOrNull() ?: nameWithoutExt
        }

        val cleaned = splitPart.replace(".", " ")
            .replace("_", " ")
            .replace("-", " ")
            .replace("[", " ")
            .replace("]", " ")
            .replace("(", " ")
            .replace(")", " ")
            .trim()

        val yearRegex = Regex("\\b(19\\d\\d|20\\d\\d)\\b")
        val yearMatch = yearRegex.find(cleaned)
        val year = yearMatch?.value

        val cleanTitle = if (year != null) {
            cleaned.substringBefore(year).trim()
        } else {
            cleaned.trim()
        }.ifEmpty { nameWithoutExt }

        return ParsedMediaInfo(
            cleanTitle = cleanTitle,
            year = year,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber
        )
    }

    /**
     * Searches TMDB's /search/multi endpoint (covers both movies and TV) and
     * returns the top match's vote_average, converted to a 0-5 star scale.
     * Returns null on any failure (no key, no network, no match) so callers
     * can simply hide the rating rather than show an error in the player.
     */
    suspend fun fetchRating(apiKey: String, title: String, year: String?): MediaRating? =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank() || title.isBlank()) return@withContext null
            try {
                val encodedTitle = Uri.encode(title)
                val urlString = StringBuilder(BASE_URL)
                    .append("/search/multi?api_key=")
                    .append(apiKey.trim())
                    .append("&query=")
                    .append(encodedTitle)
                    .append("&include_adult=false")
                if (!year.isNullOrBlank()) {
                    urlString.append("&year=").append(year)
                }

                val json = getJson(urlString.toString()) ?: return@withContext null
                val results = json.optJSONArray("results") ?: return@withContext null
                if (results.length() == 0) return@withContext null

                // Prefer the result with the most votes among the first few
                // matches, since the top text-relevance match is sometimes a
                // low-vote obscure title sharing the same name.
                var best: JSONObject? = null
                var bestVotes = -1
                for (i in 0 until minOf(results.length(), 5)) {
                    val candidate = results.getJSONObject(i)
                    val mediaType = candidate.optString("media_type")
                    if (mediaType != "movie" && mediaType != "tv") continue
                    val votes = candidate.optInt("vote_count", 0)
                    if (votes > bestVotes) {
                        bestVotes = votes
                        best = candidate
                    }
                }
                val match = best ?: results.optJSONObject(0) ?: return@withContext null

                val voteAverage = match.optDouble("vote_average", 0.0)
                val voteCount = match.optInt("vote_count", 0)
                if (voteAverage <= 0.0 || voteCount <= 0) return@withContext null

                MediaRating(
                    source = "TMDB",
                    voteAverageOutOf10 = voteAverage,
                    voteCount = voteCount,
                    starsOutOf5 = (voteAverage / 2.0).toFloat().coerceIn(0f, 5f)
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    private fun getJson(urlString: String): JSONObject? {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.requestMethod = "GET"
            if (conn.responseCode == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                JSONObject(responseText)
            } else null
        } finally {
            conn.disconnect()
        }
    }
}
