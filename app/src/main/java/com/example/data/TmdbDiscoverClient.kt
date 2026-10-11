package com.example.data

import android.net.Uri
import com.example.model.DiscoverCatalogSource
import com.example.model.DiscoverDetails
import com.example.model.DiscoverMediaType
import com.example.model.DiscoverTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Catalog metadata client for Discover.
 *
 * A user-provided TMDB key is optional. Without one, Discover uses the public
 * iTunes Search API for movie metadata and TVMaze for TV metadata. Neither
 * fallback needs an account or embeds a shared API credential in the APK.
 */
object TmdbDiscoverClient {

    private const val TMDB_API_BASE = "https://api.themoviedb.org/3"
    private const val ITUNES_SEARCH_URL = "https://itunes.apple.com/search"

    suspend fun search(apiKey: String, query: String): List<DiscoverTitle> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext emptyList()

        val tmdbResults = apiKey.trim()
            .takeIf { it.isNotBlank() }
            ?.let { key -> searchTmdb(key, cleanQuery) }
            .orEmpty()

        val keylessResults = buildList {
            addAll(searchItunesMovies(cleanQuery))
            addAll(searchTvMaze(cleanQuery))
        }

        (tmdbResults + keylessResults)
            .distinctBy { title ->
                "${title.mediaType}:${title.title.trim().lowercase()}:${title.releaseYear.orEmpty()}"
            }
            .take(40)
    }

    suspend fun details(apiKey: String, title: DiscoverTitle): DiscoverDetails? = withContext(Dispatchers.IO) {
        // Keyless catalog sources provide enough information to search the
        // user's connected library. Do not issue unauthenticated TMDB calls.
        if (title.catalogSource != DiscoverCatalogSource.TMDB || apiKey.isBlank()) {
            return@withContext DiscoverDetails(title = title)
        }

        val typePath = if (title.mediaType == DiscoverMediaType.TV) "tv" else "movie"
        val url = Uri.parse("$TMDB_API_BASE/$typePath/${title.tmdbId}").buildUpon()
            .appendQueryParameter("api_key", apiKey.trim())
            .appendQueryParameter("append_to_response", "credits,external_ids,videos")
            .build()
            .toString()

        val json = getJson(url) ?: return@withContext DiscoverDetails(title = title)
        val genres = buildList {
            val array = json.optJSONArray("genres") ?: return@buildList
            for (index in 0 until array.length()) {
                val name = array.optJSONObject(index)?.optString("name").orEmpty()
                if (name.isNotBlank()) add(name)
            }
        }
        val runtime = json.optInt("runtime", 0).takeIf { it > 0 }
            ?: json.optJSONArray("episode_run_time")?.optInt(0, 0)?.takeIf { it > 0 }
        val updatedTitle = title.copy(
            overview = json.optString("overview", title.overview),
            rating = json.optDouble("vote_average", title.rating ?: 0.0)
                .takeIf { it > 0.0 } ?: title.rating
        )

        DiscoverDetails(
            title = updatedTitle,
            runtimeMinutes = runtime,
            genres = genres
        )
    }

    private fun searchTmdb(apiKey: String, query: String): List<DiscoverTitle> {
        val url = Uri.parse("$TMDB_API_BASE/search/multi").buildUpon()
            .appendQueryParameter("api_key", apiKey)
            .appendQueryParameter("query", query)
            .appendQueryParameter("include_adult", "false")
            .build()
            .toString()
        val results = getJson(url)?.optJSONArray("results") ?: return emptyList()
        return buildList {
            for (index in 0 until results.length()) {
                results.optJSONObject(index)?.let(::parseTmdbTitle)?.let(::add)
            }
        }
    }

    private fun searchItunesMovies(query: String): List<DiscoverTitle> {
        val url = Uri.parse(ITUNES_SEARCH_URL).buildUpon()
            .appendQueryParameter("term", query)
            .appendQueryParameter("country", "US")
            .appendQueryParameter("media", "movie")
            .appendQueryParameter("entity", "movie")
            .appendQueryParameter("limit", "20")
            .build()
            .toString()
        val results = getJson(url)?.optJSONArray("results") ?: return emptyList()
        return buildList {
            for (index in 0 until results.length()) {
                val item = results.optJSONObject(index) ?: continue
                val title = item.optString("trackName").ifBlank { item.optString("collectionName") }
                if (title.isBlank()) continue
                val date = item.optString("releaseDate")
                val catalogId = item.optLong("trackId", 0L).takeIf { it > 0L } ?: (index + 1).toLong()
                add(
                    DiscoverTitle(
                        tmdbId = -catalogId,
                        mediaType = DiscoverMediaType.MOVIE,
                        title = title,
                        overview = item.optString("longDescription").ifBlank {
                            item.optString("shortDescription")
                        },
                        releaseYear = date.take(4).takeIf { it.length == 4 },
                        posterUrl = item.optString("artworkUrl100").takeIf { it.isNotBlank() },
                        backdropUrl = item.optString("artworkUrl100").takeIf { it.isNotBlank() },
                        language = item.optString("country").takeIf { it.isNotBlank() },
                        catalogSource = DiscoverCatalogSource.ITUNES
                    )
                )
            }
        }
    }

    private fun searchTvMaze(query: String): List<DiscoverTitle> {
        val url = "https://api.tvmaze.com/search/shows?q=${Uri.encode(query)}"
        val array = getJsonArray(url) ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index)?.optJSONObject("show") ?: continue
                val title = item.optString("name")
                if (title.isBlank()) continue
                val premiered = item.optString("premiered")
                val catalogId = item.optLong("id", 0L).takeIf { it > 0L } ?: (index + 1).toLong()
                val image = item.optJSONObject("image")
                val poster = image?.optString("medium").orEmpty()
                    .ifBlank { image?.optString("original").orEmpty() }
                    .takeIf { it.isNotBlank() }
                val ratingValue = item.optJSONObject("rating")?.optDouble("average", 0.0) ?: 0.0
                add(
                    DiscoverTitle(
                        tmdbId = -catalogId,
                        mediaType = DiscoverMediaType.TV,
                        title = title,
                        overview = item.optString("summary").replace(Regex("<[^>]*>"), "").trim(),
                        releaseYear = premiered.take(4).takeIf { it.length == 4 },
                        posterUrl = poster,
                        backdropUrl = poster,
                        rating = ratingValue.takeIf { it > 0.0 },
                        language = item.optString("language").takeIf { it.isNotBlank() },
                        catalogSource = DiscoverCatalogSource.TVMAZE
                    )
                )
            }
        }
    }

    private fun parseTmdbTitle(entry: JSONObject): DiscoverTitle? {
        val mediaType = when (entry.optString("media_type")) {
            "tv" -> DiscoverMediaType.TV
            "movie" -> DiscoverMediaType.MOVIE
            else -> return null
        }
        val title = entry.optString("title").ifBlank { entry.optString("name") }
        if (title.isBlank()) return null
        val releaseDate = entry.optString("release_date").ifBlank { entry.optString("first_air_date") }
        val posterPath = entry.optString("poster_path").takeIf { it.isNotBlank() }
        val backdropPath = entry.optString("backdrop_path").takeIf { it.isNotBlank() }
        return DiscoverTitle(
            tmdbId = entry.optLong("id", 0L),
            mediaType = mediaType,
            title = title,
            overview = entry.optString("overview"),
            releaseYear = releaseDate.take(4).takeIf { it.length == 4 },
            posterUrl = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
            backdropUrl = backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" },
            rating = entry.optDouble("vote_average", 0.0).takeIf { it > 0.0 },
            language = entry.optString("original_language").takeIf { it.isNotBlank() },
            catalogSource = DiscoverCatalogSource.TMDB
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
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun getJsonArray(url: String): JSONArray? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }
}
