package com.example.data

import android.net.Uri
import com.example.model.DiscoverDetails
import com.example.model.DiscoverMediaType
import com.example.model.DiscoverTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object TmdbDiscoverClient {

    private const val API_BASE = "https://api.themoviedb.org/3"
    const val DEFAULT_API_KEY = "b4435cefc19ea78f0d86fa73117fe948"

    suspend fun search(apiKey: String, query: String): List<DiscoverTitle> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val effectiveKey = apiKey.trim().ifBlank { DEFAULT_API_KEY }
        val url = Uri.parse("$API_BASE/search/multi").buildUpon()
            .appendQueryParameter("api_key", effectiveKey)
            .appendQueryParameter("query", query.trim())
            .appendQueryParameter("include_adult", "false")
            .build()
            .toString()

        val results = getJson(url)?.optJSONArray("results")
        val list = if (results != null && results.length() > 0) {
            buildList {
                for (index in 0 until results.length()) {
                    val entry = results.optJSONObject(index) ?: continue
                    parseTitle(entry)?.let(::add)
                }
            }
        } else {
            emptyList()
        }

        if (list.isNotEmpty()) return@withContext list

        searchTvMaze(query)
    }

    suspend fun details(apiKey: String, title: DiscoverTitle): DiscoverDetails? = withContext(Dispatchers.IO) {
        val effectiveKey = apiKey.trim().ifBlank { DEFAULT_API_KEY }
        val typePath = if (title.mediaType == DiscoverMediaType.TV) "tv" else "movie"
        val url = Uri.parse("$API_BASE/$typePath/${title.tmdbId}").buildUpon()
            .appendQueryParameter("api_key", effectiveKey)
            .appendQueryParameter("append_to_response", "credits,external_ids,videos")
            .build()
            .toString()

        val json = getJson(url) ?: return@withContext DiscoverDetails(
            title = title,
            genres = emptyList()
        )

        val genres = buildList {
            val arr = json.optJSONArray("genres") ?: return@buildList
            for (index in 0 until arr.length()) {
                val item = arr.optJSONObject(index) ?: continue
                val name = item.optString("name")
                if (name.isNotBlank()) add(name)
            }
        }

        val runtime = json.optInt("runtime", 0).takeIf { it > 0 }
            ?: json.optJSONArray("episode_run_time")?.optInt(0, 0)?.takeIf { it > 0 }

        val updatedTitle = title.copy(
            overview = json.optString("overview", title.overview),
            rating = json.optDouble("vote_average", title.rating ?: 0.0).takeIf { it > 0.0 } ?: title.rating
        )

        DiscoverDetails(
            title = updatedTitle,
            runtimeMinutes = runtime,
            genres = genres
        )
    }

    private fun searchTvMaze(query: String): List<DiscoverTitle> {
        val encoded = Uri.encode(query.trim())
        val url = "https://api.tvmaze.com/search/shows?q=$encoded"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
        }
        val text = try {
            if (connection.responseCode !in 200..299) return emptyList()
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            return emptyList()
        } finally {
            connection.disconnect()
        }

        val array = try { JSONArray(text) } catch (e: Exception) { return emptyList() }
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i)?.optJSONObject("show") ?: continue
                val name = item.optString("name", "")
                if (name.isBlank()) continue
                val premiered = item.optString("premiered", "")
                val imgObj = item.optJSONObject("image")
                val posterMedium = imgObj?.optString("medium", "").orEmpty()
                val posterOrig = imgObj?.optString("original", "").orEmpty()
                val poster = if (posterMedium.isNotBlank()) posterMedium else posterOrig.takeIf(String::isNotBlank)
                val ratingVal = item.optJSONObject("rating")?.optDouble("average", 0.0) ?: 0.0
                val rating = if (ratingVal > 0.0) ratingVal else null
                val summaryRaw = item.optString("summary", "")
                val summaryClean = summaryRaw.replace(Regex("<[^>]*>"), "").trim()
                val year = if (premiered.length >= 4) premiered.substring(0, 4) else null
                val lang = item.optString("language", "").takeIf(String::isNotBlank)

                add(
                    DiscoverTitle(
                        tmdbId = item.optLong("id", 0L),
                        mediaType = DiscoverMediaType.TV,
                        title = name,
                        overview = summaryClean,
                        releaseYear = year,
                        posterUrl = poster,
                        backdropUrl = poster,
                        rating = rating,
                        language = lang
                    )
                )
            }
        }
    }

    private fun parseTitle(entry: JSONObject): DiscoverTitle? {
        val mediaTypeStr = entry.optString("media_type")
        val mediaType = when (mediaTypeStr) {
            "tv" -> DiscoverMediaType.TV
            "movie" -> DiscoverMediaType.MOVIE
            else -> return null
        }
        val title = entry.optString("title").ifBlank { entry.optString("name") }
        if (title.isBlank()) return null

        val releaseDate = entry.optString("release_date").ifBlank { entry.optString("first_air_date") }
        val posterPath = entry.optString("poster_path").takeIf(String::isNotBlank)
        val backdropPath = entry.optString("backdrop_path").takeIf(String::isNotBlank)

        return DiscoverTitle(
            tmdbId = entry.optLong("id", 0L),
            mediaType = mediaType,
            title = title,
            overview = entry.optString("overview"),
            releaseYear = releaseDate.take(4).takeIf { it.length == 4 },
            posterUrl = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
            backdropUrl = backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" },
            rating = entry.optDouble("vote_average", 0.0).takeIf { it > 0.0 },
            language = entry.optString("original_language").takeIf(String::isNotBlank)
        )
    }

    private fun getJson(url: String): JSONObject? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(responseText)
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }
}
