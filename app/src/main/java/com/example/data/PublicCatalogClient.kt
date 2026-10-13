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
 * Free, keyless metadata search for Discover.
 *
 * This uses the public iTunes Search API for movie metadata and TVMaze for TV
 * metadata. The results are for discovery only; playback is always routed to a
 * media source that the user has connected to Hi Player.
 */
object PublicCatalogClient {
    private const val ITUNES_SEARCH_URL = "https://itunes.apple.com/search"

    suspend fun search(query: String): List<DiscoverTitle> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext emptyList()

        buildList {
            addAll(searchItunesMovies(cleanQuery))
            addAll(searchTvMaze(cleanQuery))
        }
            .distinctBy { title ->
                "${title.mediaType}:${title.title.trim().lowercase()}:${title.releaseYear.orEmpty()}"
            }
            .take(40)
    }

    suspend fun details(title: DiscoverTitle): DiscoverDetails = withContext(Dispatchers.IO) {
        // Both public sources already provide the metadata needed to search a
        // connected media library. No account or private API credential is used.
        DiscoverDetails(title = title)
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
                        catalogId = catalogId,
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
                        catalogId = catalogId,
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
