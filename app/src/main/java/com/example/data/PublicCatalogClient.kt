package com.example.data

import android.net.Uri
import com.example.BuildConfig
import com.example.model.DiscoverCastMember
import com.example.model.DiscoverDetails
import com.example.model.DiscoverHomeSection
import com.example.model.DiscoverMediaType
import com.example.model.DiscoverSeason
import com.example.model.DiscoverTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * TMDB metadata client used by Discover.
 *
 * This client never returns a playable stream. It provides title, artwork,
 * ratings, genres and cast metadata; Hi Player only plays files from sources
 * explicitly connected by the user (Google Drive folder, Jellyfin or WebDAV).
 */
object PublicCatalogClient {
    private const val API_BASE_URL = "https://api.themoviedb.org/3"
    private const val IMAGE_BASE_URL = "https://image.tmdb.org/t/p"
    private const val LANGUAGE = "en-US"

    suspend fun home(): List<DiscoverHomeSection> = withContext(Dispatchers.IO) {
        listOf(
            DiscoverHomeSection("trending", "Trending today", titlesFromPage("/trending/all/day")),
            DiscoverHomeSection("now_playing", "Now playing", titlesFromPage("/movie/now_playing")),
            DiscoverHomeSection("on_the_air", "New TV episodes", titlesFromPage("/tv/on_the_air")),
            DiscoverHomeSection("upcoming", "Coming soon", titlesFromPage("/movie/upcoming")),
        ).map { section -> section.copy(titles = section.titles.take(HOME_SECTION_LIMIT)) }
            .filter { it.titles.isNotEmpty() }
    }

    suspend fun search(query: String): List<DiscoverTitle> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext emptyList()
        val json = getJson(
            path = "/search/multi",
            query = mapOf(
                "query" to cleanQuery,
                "include_adult" to "false",
            ),
        )
        parseTitles(json.optJSONArray("results")).take(40)
    }

    suspend fun details(title: DiscoverTitle): DiscoverDetails = withContext(Dispatchers.IO) {
        val mediaPath = when (title.mediaType) {
            DiscoverMediaType.MOVIE -> "/movie/${title.catalogId}"
            DiscoverMediaType.TV -> "/tv/${title.catalogId}"
        }
        val detailsJson = getJson(mediaPath)
        val creditsJson = getJson("$mediaPath/credits")
        val canonicalTitle = parseTitle(detailsJson, title.mediaType, title.catalogId) ?: title
        DiscoverDetails(
            title = canonicalTitle,
            runtimeMinutes = parseRuntimeMinutes(detailsJson, title.mediaType),
            genres = parseNameList(detailsJson.optJSONArray("genres")),
            seasons = parseSeasons(detailsJson.optJSONArray("seasons")),
            cast = parseCast(creditsJson.optJSONArray("cast")),
        )
    }

    private fun titlesFromPage(path: String): List<DiscoverTitle> {
        val json = getJson(path)
        return parseTitles(json.optJSONArray("results"))
    }

    private fun parseTitles(items: JSONArray?): List<DiscoverTitle> = buildList {
        if (items == null) return@buildList
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val type = when (item.optString("media_type")) {
                "tv" -> DiscoverMediaType.TV
                "movie" -> DiscoverMediaType.MOVIE
                else -> when {
                    item.has("name") || item.has("first_air_date") -> DiscoverMediaType.TV
                    item.has("title") || item.has("release_date") -> DiscoverMediaType.MOVIE
                    else -> null
                }
            } ?: continue // People are intentionally excluded from title rows.
            parseTitle(item, type)?.let(::add)
        }
    }

    private fun parseTitle(
        item: JSONObject,
        knownType: DiscoverMediaType,
        fallbackId: Long = 0L,
    ): DiscoverTitle? {
        val id = item.optLong("id", fallbackId).takeIf { it > 0L } ?: return null
        val title = when (knownType) {
            DiscoverMediaType.MOVIE -> item.optString("title").ifBlank { item.optString("original_title") }
            DiscoverMediaType.TV -> item.optString("name").ifBlank { item.optString("original_name") }
        }.trim()
        if (title.isBlank()) return null
        val date = when (knownType) {
            DiscoverMediaType.MOVIE -> item.optString("release_date")
            DiscoverMediaType.TV -> item.optString("first_air_date")
        }
        return DiscoverTitle(
            catalogId = id,
            mediaType = knownType,
            title = title,
            overview = item.optString("overview").trim(),
            releaseYear = date.take(4).takeIf { it.length == 4 },
            posterUrl = imageUrl(item.optString("poster_path"), POSTER_SIZE),
            backdropUrl = imageUrl(item.optString("backdrop_path"), BACKDROP_SIZE),
            rating = item.optDouble("vote_average", 0.0).takeIf { it > 0.0 },
            language = item.optString("original_language").takeIf { it.isNotBlank() },
        )
    }

    private fun parseRuntimeMinutes(item: JSONObject, type: DiscoverMediaType): Int? {
        return when (type) {
            DiscoverMediaType.MOVIE -> item.optInt("runtime", 0).takeIf { it > 0 }
            DiscoverMediaType.TV -> {
                val episodeRuntimes = item.optJSONArray("episode_run_time")
                (episodeRuntimes?.optInt(0, 0) ?: 0).takeIf { it > 0 }
                    ?: item.optJSONObject("last_episode_to_air")?.optInt("runtime", 0)?.takeIf { it > 0 }
            }
        }
    }

    private fun parseNameList(items: JSONArray?): List<String> = buildList {
        if (items == null) return@buildList
        for (index in 0 until items.length()) {
            items.optJSONObject(index)?.optString("name")?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
        }
    }

    private fun parseSeasons(items: JSONArray?): List<DiscoverSeason> = buildList {
        if (items == null) return@buildList
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val number = item.optInt("season_number", -1)
            if (number < 0) continue
            val name = item.optString("name").ifBlank { "Season $number" }
            add(
                DiscoverSeason(
                    seasonNumber = number,
                    name = name,
                    episodeCount = item.optInt("episode_count", 0).coerceAtLeast(0),
                    posterUrl = imageUrl(item.optString("poster_path"), POSTER_SIZE),
                )
            )
        }
    }

    private fun parseCast(items: JSONArray?): List<DiscoverCastMember> = buildList {
        if (items == null) return@buildList
        for (index in 0 until minOf(items.length(), CAST_LIMIT)) {
            val item = items.optJSONObject(index) ?: continue
            val id = item.optLong("id", 0L)
            val name = item.optString("name").trim()
            if (id <= 0L || name.isBlank()) continue
            add(
                DiscoverCastMember(
                    id = id,
                    name = name,
                    character = item.optString("character").trim().takeIf { it.isNotBlank() },
                    profileUrl = imageUrl(item.optString("profile_path"), PROFILE_SIZE),
                )
            )
        }
    }

    private fun imageUrl(path: String, size: String): String? =
        path.trim().takeIf { it.startsWith("/") }?.let { "$IMAGE_BASE_URL/$size$it" }

    private fun getJson(path: String, query: Map<String, String> = emptyMap()): JSONObject {
        val token = BuildConfig.TMDB_READ_ACCESS_TOKEN.trim()
        check(token.isNotBlank()) { "TMDB metadata is not configured in this build." }
        val builder = Uri.parse("$API_BASE_URL$path").buildUpon()
            .appendQueryParameter("language", LANGUAGE)
        query.forEach { (key, value) -> builder.appendQueryParameter(key, value) }
        val connection = (URL(builder.build().toString()).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IOException(
                    when (code) {
                        401 -> "TMDB authorization was rejected."
                        429 -> "TMDB is busy. Please try again shortly."
                        else -> "TMDB metadata request failed (HTTP $code)."
                    },
                )
            }
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private const val HOME_SECTION_LIMIT = 14
    private const val CAST_LIMIT = 12
    private const val POSTER_SIZE = "w342"
    private const val BACKDROP_SIZE = "w780"
    private const val PROFILE_SIZE = "w185"
}
