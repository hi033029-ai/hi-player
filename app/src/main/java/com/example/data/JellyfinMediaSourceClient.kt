package com.example.data

import android.net.Uri
import com.example.model.JellyfinConnection
import com.example.model.RemoteMediaMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Talks to a user-owned Jellyfin server. Search only returns items in that
 * account's own library and playback uses Jellyfin direct-play URLs, preserving
 * the server file's 4K/HDR/audio tracks where the device can decode them.
 */
object JellyfinMediaSourceClient {
    private const val APP_NAME = "Hi Player"
    private const val APP_VERSION = "1.0"

    suspend fun authenticate(baseUrl: String, username: String, password: String): Result<JellyfinConnection> =
        withContext(Dispatchers.IO) {
            runCatching {
                val normalizedBase = normalizeBaseUrl(baseUrl)
                require(username.isNotBlank()) { "Enter the Jellyfin username." }
                val deviceId = UUID.nameUUIDFromBytes("$normalizedBase:$username".toByteArray()).toString()
                val body = JSONObject()
                    .put("Username", username.trim())
                    .put("Pw", password)
                    .toString()
                val root = requestJson(
                    url = "$normalizedBase/Users/AuthenticateByName",
                    method = "POST",
                    body = body,
                    deviceId = deviceId
                )
                val token = root.optString("AccessToken")
                val user = root.optJSONObject("User")
                val userId = user?.optString("Id").orEmpty()
                require(token.isNotBlank() && userId.isNotBlank()) { "Jellyfin did not return a usable user session." }
                JellyfinConnection(
                    baseUrl = normalizedBase,
                    username = username.trim(),
                    password = password,
                    accessToken = token,
                    userId = userId,
                    serverName = user?.optString("ServerName")?.ifBlank { "Jellyfin" } ?: "Jellyfin"
                )
            }
        }

    suspend fun search(connection: JellyfinConnection, query: String): List<RemoteMediaMatch> =
        withContext(Dispatchers.IO) {
            if (!connection.isConfigured || query.isBlank()) return@withContext emptyList()
            val url = Uri.parse("${connection.baseUrl}/Users/${connection.userId}/Items").buildUpon()
                .appendQueryParameter("SearchTerm", query.trim())
                .appendQueryParameter("IncludeItemTypes", "Movie,Episode,Video")
                .appendQueryParameter("Recursive", "true")
                .appendQueryParameter("Fields", "MediaSources,MediaStreams,Overview,ProviderIds")
                .appendQueryParameter("SortBy", "SortName")
                .appendQueryParameter("Limit", "60")
                .build()
                .toString()
            val root = requestJson(
                url = url,
                method = "GET",
                accessToken = connection.accessToken,
                deviceId = deviceId(connection)
            )
            val items = root.optJSONArray("Items") ?: return@withContext emptyList()
            buildList {
                for (index in 0 until items.length()) {
                    items.optJSONObject(index)?.let { item ->
                        parseItem(connection, item)?.let(::add)
                    }
                }
            }
        }

    fun requestHeaders(connection: JellyfinConnection): Map<String, String> = mapOf(
        "X-Emby-Token" to connection.accessToken,
        "X-Emby-Authorization" to authorizationValue(deviceId(connection))
    )

    private fun parseItem(connection: JellyfinConnection, item: JSONObject): RemoteMediaMatch? {
        val id = item.optString("Id").takeIf { it.isNotBlank() } ?: return null
        val mediaSources = item.optJSONArray("MediaSources")
        val source = mediaSources?.optJSONObject(0)
        val streams = source?.optJSONArray("MediaStreams") ?: item.optJSONArray("MediaStreams") ?: JSONArray()
        val videoStream = streams.firstObjectWhere { it.optString("Type").equals("Video", true) }
        val audioLabels = buildList {
            streams.forEachObject { stream ->
                if (stream.optString("Type").equals("Audio", true)) {
                    val label = stream.optString("DisplayTitle")
                        .ifBlank { stream.optString("Language") }
                        .ifBlank { stream.optString("Codec") }
                    if (label.isNotBlank()) add(label)
                }
            }
        }.distinct()
        val container = source?.optString("Container")
            ?.ifBlank { item.optString("Container") }
            ?.lowercase()
            .orEmpty()
        val width = source?.optInt("Width", 0)?.takeIf { it > 0 }
            ?: videoStream?.optInt("Width", 0)?.takeIf { it > 0 }
            ?: item.optInt("Width", 0)
        val height = source?.optInt("Height", 0)?.takeIf { it > 0 }
            ?: videoStream?.optInt("Height", 0)?.takeIf { it > 0 }
            ?: item.optInt("Height", 0)
        val hdrType = listOfNotNull(
            source?.optString("VideoRangeType"),
            videoStream?.optString("VideoRangeType"),
            videoStream?.optString("VideoRange")
        ).firstOrNull { it.isNotBlank() && !it.equals("SDR", true) }
        val name = item.optString("Name").ifBlank { "Jellyfin item" }
        val seriesName = item.optString("SeriesName").takeIf { it.isNotBlank() }
        val subtitle = buildString {
            seriesName?.let { append(it).append(" • ") }
            val season = item.optInt("ParentIndexNumber", 0)
            val episode = item.optInt("IndexNumber", 0)
            if (season > 0 && episode > 0) append("S%02dE%02d".format(season, episode))
        }.trim().takeIf { it.isNotBlank() }
        val directUrl = "${connection.baseUrl}/Videos/$id/stream?static=true"
        return RemoteMediaMatch(
            id = "jellyfin:$id",
            sourceName = connection.serverName.ifBlank { "Jellyfin" },
            title = name,
            subtitle = subtitle,
            streamUri = Uri.parse(directUrl),
            streamMimeType = mimeTypeFor(container),
            requestHeaders = requestHeaders(connection),
            width = width,
            height = height,
            durationMs = item.optLong("RunTimeTicks", 0L) / 10_000L,
            sizeBytes = source?.optLong("Size", 0L) ?: 0L,
            isHdr = hdrType != null,
            hdrFormat = hdrType,
            codec = videoStream?.optString("Codec")?.uppercase()?.ifBlank { "Jellyfin direct play" } ?: "Jellyfin direct play",
            audioLabels = audioLabels,
            artworkUrl = null
        )
    }

    private fun requestJson(
        url: String,
        method: String,
        body: String? = null,
        accessToken: String? = null,
        deviceId: String
    ): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Emby-Authorization", authorizationValue(deviceId))
            accessToken?.takeIf(String::isNotBlank)?.let { setRequestProperty("X-Emby-Token", it) }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                outputStream.bufferedWriter().use { it.write(body) }
            }
        }
        return try {
            if (connection.responseCode !in 200..299) {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IllegalStateException("Jellyfin returned HTTP ${connection.responseCode}${if (error.isBlank()) "" else ": $error"}")
            }
            connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        } finally {
            connection.disconnect()
        }
    }

    private fun normalizeBaseUrl(raw: String): String {
        val value = raw.trim().trimEnd('/')
        require(value.startsWith("http://") || value.startsWith("https://")) { "Use a full Jellyfin URL starting with http:// or https://." }
        return value
    }

    private fun deviceId(connection: JellyfinConnection): String =
        UUID.nameUUIDFromBytes("${connection.baseUrl}:${connection.username}".toByteArray()).toString()

    private fun authorizationValue(deviceId: String): String =
        "MediaBrowser Client=\"$APP_NAME\", Device=\"Android\", DeviceId=\"$deviceId\", Version=\"$APP_VERSION\""

    private fun mimeTypeFor(container: String): String? = when (container) {
        "mkv", "matroska" -> "video/x-matroska"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        "avi" -> "video/x-msvideo"
        "ts", "m2ts" -> "video/mp2t"
        "mp4", "m4v" -> "video/mp4"
        else -> null
    }

    private inline fun JSONArray.forEachObject(block: (JSONObject) -> Unit) {
        for (index in 0 until length()) optJSONObject(index)?.let(block)
    }

    private inline fun JSONArray.firstObjectWhere(predicate: (JSONObject) -> Boolean): JSONObject? {
        for (index in 0 until length()) {
            val value = optJSONObject(index) ?: continue
            if (predicate(value)) return value
        }
        return null
    }
}
