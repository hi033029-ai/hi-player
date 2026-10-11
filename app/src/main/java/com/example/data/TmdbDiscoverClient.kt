package com.example.data

import android.net.Uri
import com.example.model.DiscoverDetails
import com.example.model.DiscoverMediaType
import com.example.model.DiscoverSeason
import com.example.model.DiscoverTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Free TMDB metadata client. It never returns or searches for streaming URLs. */
object TmdbDiscoverClient {
    private const val API_BASE = "https://api.themoviedb.org/3"
    const val DEFAULT_API_KEY = "b4435cefc19ea78f0d86fa73117fe948"
    private const val IMAGE_BASE = "https://image.tmdb.org/t/p/w500"

    suspend fun search(apiKey: String, query: String): List<DiscoverTitle> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val effectiveKey = apiKey.trim().ifBlank { DEFAULT_API_KEY }
        val url = Uri.parse("$API_BASE/search/multi").buildUpon()
            .appendQueryParameter("api_key", effectiveKey)
            .appendQueryParameter("query", query.trim())
            .appendQueryParameter("include_adult", "false")
            .appendQueryParameter("language", "en-US")
            .build()
            .toString()
        val results = getJson(url)?.optJSONArray("results") ?: return@withContext emptyList()
        buildList {
            for (index in 0 until results.length()) {
                val entry = results.optJSONObject(index) ?: continue
                parseTitle(entry)?.let(::add)
            }
        }
    }

    suspend fun details(apiKey: String, title: DiscoverTitle): DiscoverDetails? = withContext(Dispatchers.IO) {
        val effectiveKey = apiKey.trim().ifBlank { DEFAULT_API_KEY }
        val typePath = if (title.mediaType == DiscoverMediaType.TV) "tv" else "movie"
        val url = Uri.parse("$API_BASE/$typePath/${title.tmdbId}").buildUpon()
            .appendQueryParameter("api_key", effectiveKey)
            .appendQueryParameter("language", "en-US")
            .build()
            .toString()
        val root = getJson(url) ?: return@withContext null
        val genres = root.optJSONArray("genres")?.let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.optString("name")?.takeIf(String::isNotBlank)?.let(::add)
                }
            }
        }.orEmpty()
        val seasons = root.optJSONArray("seasons")?.let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    val season = array.optJSONObject(index) ?: continue
                    val number = season.optInt("season_number", -1)
                    if (number < 0) continue
                    add(
                        DiscoverSeason(
                            seasonNumber = number,
                            name = season.optString("name").ifBlank { "Season $number" },
                            episodeCount = season.optInt("episode_count", 0),
                            posterUrl = imageUrl(season.optString("poster_path"))
                        )
                    )
                }
            }
        }.orEmpty()
        val runtime = if (title.mediaType == DiscoverMediaType.MOVIE) {
            root.optInt("runtime", 0).takeIf { it > 0 }
        } else {
            root.optJSONArray("episode_run_time")?.optInt(0, 0)?.takeIf { it > 0 }
        }
        DiscoverDetails(title = title, runtimeMinutes = runtime, genres = genres, seasons = seasons)
    }

    private fun parseTitle(entry: JSONObject): DiscoverTitle? {
        val type = when (entry.optString("media_type")) {
            "movie" -> DiscoverMediaType.MOVIE
            "tv" -> DiscoverMediaType.TV
            else -> return null
        }
        val name = if (type == DiscoverMediaType.MOVIE) entry.optString("title") else entry.optString("name")
        if (name.isBlank()) return null
        val date = if (type == DiscoverMediaType.MOVIE) entry.optString("release_date") else entry.optString("first_air_date")
        return DiscoverTitle(
            tmdbId = entry.optLong("id", 0L),
            mediaType = type,
            title = name,
            overview = entry.optString("overview"),
            releaseYear = date.take(4).takeIf { it.length == 4 },
            posterUrl = imageUrl(entry.optString("poster_path")),
            backdropUrl = imageUrl(entry.optString("backdrop_path")),
            rating = entry.optDouble("vote_average", 0.0).takeIf { it > 0.0 },
            language = entry.optString("original_language").takeIf { it.isNotBlank() }
        )
    }

    private fun imageUrl(path: String): String? = path.takeIf { it.isNotBlank() }?.let { "$IMAGE_BASE$it" }

    private fun getJson(urlString: String): JSONObject? {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        } finally {
            connection.disconnect()
        }
    }
}
