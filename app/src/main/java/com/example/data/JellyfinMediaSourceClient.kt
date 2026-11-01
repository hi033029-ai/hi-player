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
                require(token.isNotBlank() && userId.matches(Regex("[A-Fa-f0-9-]{1,36}"))) { "Jellyfin did not return a usable user session." }
                JellyfinConnection(
                    baseUrl = normalizedBase,
                    username = username.trim(),
                    password = "", // The server token is sufficient; do not retain the password.
                    accessToken = token,
                    userId = userId,
                    serverName = user?.optString("ServerName")?.ifBlank { "Jellyfin" } ?: "Jellyfin"
                )
            }
        }

    internal suspend fun beginQuickConnect(rawUrl: String): com.example.model.JellyfinQuickSession = withContext(Dispatchers.IO) {
        val base = normalizeBaseUrl(rawUrl)
        val device = UUID.randomUUID().toString()
        val enabled = requestText("$base/QuickConnect/Enabled", "GET", deviceId = device).trim()
        check(enabled == "true") { "Quick Connect unavailable" }
        val response = requestJson("$base/QuickConnect/Initiate", "POST", body = "{}", deviceId = device)
        val code = response.getString("Code")
        val secret = response.getString("Secret")
        require(code.matches(Regex("[A-Za-z0-9]{6}")) && secret.length in 16..512)
        com.example.model.JellyfinQuickSession(base, device, code, secret)
    }

    internal suspend fun pollQuickConnect(session: com.example.model.JellyfinQuickSession): JellyfinConnection? = withContext(Dispatchers.IO) {
        val url = Uri.parse("${session.baseUrl}/QuickConnect/Connect").buildUpon().appendQueryParameter("secret", session.secret).build().toString()
        val status = requestJson(url, "GET", deviceId = session.deviceId)
        if (!status.optBoolean("Authenticated")) return@withContext null
        val result = requestJson("${session.baseUrl}/Users/AuthenticateWithQuickConnect", "POST",
            body = JSONObject().put("Secret", session.secret).toString(), deviceId = session.deviceId)
        val user = result.getJSONObject("User")
        val connection = JellyfinConnection(session.baseUrl, user.optString("Name"), "", result.getString("AccessToken"),
            user.getString("Id"), "Jellyfin", session.deviceId)
        require(connection.isConfigured && connection.userId.matches(Regex("[A-Fa-f0-9-]{1,36}")))
        connection
    }

    suspend fun verifySession(connection: JellyfinConnection): JellyfinConnection = withContext(Dispatchers.IO) {
        require(connection.isConfigured && connection.userId.matches(Regex("[A-Fa-f0-9-]{1,36}")))
        val user = requestJson("${normalizeBaseUrl(connection.baseUrl)}/Users/Me", "GET",
            accessToken = connection.accessToken, deviceId = deviceId(connection))
        require(user.getString("Id").replace("-", "").equals(connection.userId.replace("-", ""), true))
        connection.copy(username = user.optString("Name", connection.username), password = "")
    }

    suspend fun browse(connection: JellyfinConnection, parentId: String? = null, offset: Int = 0): com.example.model.NetworkMediaPage = withContext(Dispatchers.IO) {
        require(connection.isConfigured && connection.userId.matches(Regex("[A-Fa-f0-9-]{1,36}")) && offset >= 0)
        require(parentId == null || parentId.matches(Regex("[A-Fa-f0-9-]{1,36}")))
        val base = normalizeBaseUrl(connection.baseUrl)
        val endpoint = if (parentId == null) "$base/Users/${connection.userId}/Views" else "$base/Users/${connection.userId}/Items"
        val builder = Uri.parse(endpoint).buildUpon()
            .appendQueryParameter("Fields", "MediaSources,MediaStreams")
            .appendQueryParameter("SortBy", "SortName").appendQueryParameter("SortOrder", "Ascending")
        if (parentId != null) builder.appendQueryParameter("ParentId", parentId).appendQueryParameter("Recursive", "false")
            .appendQueryParameter("StartIndex", offset.toString()).appendQueryParameter("Limit", "60")
        val result = requestJson(builder.build().toString(), "GET", accessToken = connection.accessToken, deviceId = deviceId(connection))
        val items = result.optJSONArray("Items") ?: JSONArray()
        val entries = (0 until items.length()).mapNotNull { index ->
            val item = items.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("Id").takeIf { it.matches(Regex("[A-Fa-f0-9-]{1,36}")) } ?: return@mapNotNull null
            val folder = parentId == null || item.optBoolean("IsFolder") || item.optString("Type") in setOf("Series", "Season", "MusicAlbum", "MusicArtist", "CollectionFolder", "BoxSet", "Playlist")
            val type = item.optString("MediaType")
            val playable = if (!folder && (type in setOf("Video", "Audio") || item.optString("Type") in setOf("Movie", "Episode", "Video", "Audio", "MusicVideo"))) parseItem(connection, item) else null
            if (!folder && playable == null) return@mapNotNull null
            com.example.model.NetworkMediaEntry(id, item.optString("Name", "Media"), folder, id, playable, if (folder) "Library / folder" else if (type == "Audio") "Audio" else "Video")
        }
        val total = result.optInt("TotalRecordCount", offset + items.length())
        com.example.model.NetworkMediaPage(entries, if (parentId != null && items.length() > 0 && offset + items.length() < total) offset + items.length() else null)
    }

    suspend fun search(connection: JellyfinConnection, query: String): List<RemoteMediaMatch> =
        withContext(Dispatchers.IO) {
            if (!connection.isConfigured || !connection.userId.matches(Regex("[A-Fa-f0-9-]{1,36}")) || query.isBlank()) return@withContext emptyList()
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
        val id = item.optString("Id").takeIf { it.matches(Regex("[A-Fa-f0-9-]{1,36}")) } ?: return null
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
        val audio = item.optString("MediaType") == "Audio" || item.optString("Type") == "Audio"
        val directUrl = "${connection.baseUrl}/${if (audio) "Audio" else "Videos"}/$id/stream?static=true"
        return RemoteMediaMatch(
            id = "jellyfin:$id",
            sourceName = connection.serverName.ifBlank { "Jellyfin" },
            title = name,
            subtitle = subtitle,
            streamUri = Uri.parse(directUrl),
            streamMimeType = if (audio) when (container) { "mp3" -> "audio/mpeg"; "flac" -> "audio/flac"; "m4a", "mp4" -> "audio/mp4"; "ogg", "opus" -> "audio/ogg"; "wav" -> "audio/wav"; else -> "audio/*" } else mimeTypeFor(container),
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

    private fun requestJson(url: String, method: String, body: String? = null, accessToken: String? = null, deviceId: String): JSONObject =
        JSONObject(requestText(url, method, body, accessToken, deviceId))

    // No implicit redirects: credentials and Quick Connect secrets never travel
    // to a different endpoint. Server aliases must be configured explicitly.
    private fun requestText(url: String, method: String, body: String? = null, accessToken: String? = null, deviceId: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection)
        return try {
            connection.instanceFollowRedirects = false
            connection.requestMethod = method
            connection.connectTimeout = 12_000; connection.readTimeout = 12_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("X-Emby-Authorization", authorizationValue(deviceId))
            accessToken?.takeIf(String::isNotBlank)?.let { connection.setRequestProperty("X-Emby-Token", it) }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            }
            check(connection.responseCode in 200..299) { "Jellyfin request unavailable" }
            connection.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (true) { val count = input.read(buffer); if (count < 0) break
                    check(out.size() + count <= 4 * 1024 * 1024) { "Server response too large" }; out.write(buffer, 0, count) }
                out.toString("UTF-8")
            }
        } finally { connection.disconnect() }
    }

    internal fun normalizeBaseUrl(raw: String) = NetworkMediaPolicy.server(raw)

    private fun deviceId(connection: JellyfinConnection): String =
        connection.deviceId.ifBlank { UUID.nameUUIDFromBytes("${connection.baseUrl}:${connection.username}".toByteArray()).toString() }

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
