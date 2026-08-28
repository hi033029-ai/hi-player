package com.example.util

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class SubtitleSearchResult(
    val subtitleId: String,
    val language: String,       // ISO code, e.g. "en"
    val languageName: String,   // display name, e.g. "English"
    val release: String,        // release name, e.g. "Movie.Name.2020.1080p.BluRay"
    val fileId: Long,
    val fileName: String,
    val downloadCount: Int
)

/**
 * Real search + download against the OpenSubtitles.com REST API
 * (api.opensubtitles.com/api/v1). This is the genuine, ToS-compliant "free
 * source" for subtitles - not a scraper.
 *
 * Auth model, confirmed against OpenSubtitles' current docs:
 *  - /subtitles (search) needs only an Api-Key header.
 *  - /download needs BOTH the Api-Key header AND a Bearer token, and the
 *    token comes from /login (username + password). There's no way to
 *    download without logging in - a free OpenSubtitles account is enough,
 *    no paid tier required.
 *
 * The API key and account credentials are entered once via the Subtitles
 * settings sheet and persisted in DataStore; the login token itself is kept
 * only in memory for this session (tokens expire, so nothing is gained by
 * persisting it, and it's one less sensitive value sitting on disk).
 */
object OpenSubtitlesHelper {

    private const val BASE_URL = "https://api.opensubtitles.com/api/v1"
    private const val USER_AGENT = "HiPlayer v1.0"

    private val languageNames: Map<String, String> = mapOf(
        "en" to "English", "es" to "Spanish", "fr" to "French", "de" to "German",
        "it" to "Italian", "pt" to "Portuguese", "pt-br" to "Portuguese (Brazil)",
        "ru" to "Russian", "ja" to "Japanese", "ko" to "Korean", "zh-cn" to "Chinese (Simplified)",
        "zh-tw" to "Chinese (Traditional)", "ar" to "Arabic", "hi" to "Hindi", "bn" to "Bengali",
        "tr" to "Turkish", "nl" to "Dutch", "pl" to "Polish", "sv" to "Swedish", "no" to "Norwegian",
        "da" to "Danish", "fi" to "Finnish", "el" to "Greek", "he" to "Hebrew", "th" to "Thai",
        "vi" to "Vietnamese", "id" to "Indonesian", "ms" to "Malay", "uk" to "Ukrainian",
        "cs" to "Czech", "ro" to "Romanian", "hu" to "Hungarian", "fa" to "Persian", "ur" to "Urdu"
    )

    fun languageDisplayName(code: String): String =
        languageNames[code.lowercase()] ?: code.uppercase()

    /** Searches by title (and season/episode for TV) across all languages. */
    suspend fun search(
        apiKey: String,
        title: String,
        year: String?,
        seasonNumber: Int?,
        episodeNumber: Int?
    ): Result<List<SubtitleSearchResult>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(IllegalStateException("No OpenSubtitles API key set"))
        if (title.isBlank()) return@withContext Result.failure(IllegalStateException("No title to search for"))
        try {
            val url = StringBuilder(BASE_URL)
                .append("/subtitles?query=")
                .append(Uri.encode(title))
            if (!year.isNullOrBlank()) url.append("&year=").append(year)
            if (seasonNumber != null) url.append("&season_number=").append(seasonNumber)
            if (episodeNumber != null) url.append("&episode_number=").append(episodeNumber)

            val conn = openConnection(url.toString(), "GET", apiKey)
            val code = conn.responseCode
            if (code != 200) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
                conn.disconnect()
                return@withContext Result.failure(Exception("Search failed ($code): ${err ?: "no details"}"))
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(body)
            val data = json.optJSONArray("data") ?: JSONArray()
            val results = mutableListOf<SubtitleSearchResult>()
            for (i in 0 until data.length()) {
                val item = data.getJSONObject(i)
                val id = item.optString("id")
                val attrs = item.optJSONObject("attributes") ?: continue
                val language = attrs.optString("language", "en")
                val release = attrs.optString("release", attrs.optString("feature_details", ""))
                val downloadCount = attrs.optInt("download_count", 0)
                val files = attrs.optJSONArray("files") ?: continue
                if (files.length() == 0) continue
                val file = files.getJSONObject(0)
                val fileId = file.optLong("file_id", -1L)
                val fileName = file.optString("file_name", "$release.srt")
                if (fileId <= 0) continue
                results.add(
                    SubtitleSearchResult(
                        subtitleId = id,
                        language = language,
                        languageName = languageDisplayName(language),
                        release = release.ifBlank { fileName },
                        fileId = fileId,
                        fileName = fileName,
                        downloadCount = downloadCount
                    )
                )
            }
            // Most-downloaded (usually most accurate/trusted) first within each language.
            Result.success(results.sortedByDescending { it.downloadCount })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Logs in with a free OpenSubtitles account and returns a bearer token. */
    suspend fun login(apiKey: String, username: String, password: String): Result<String> =
        withContext(Dispatchers.IO) {
            if (username.isBlank() || password.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Username and password are required to download"))
            }
            try {
                val conn = openConnection("$BASE_URL/login", "POST", apiKey)
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                val payload = JSONObject().apply {
                    put("username", username)
                    put("password", password)
                }
                OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }

                val code = conn.responseCode
                if (code != 200) {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
                    conn.disconnect()
                    return@withContext Result.failure(Exception("Login failed ($code): ${err ?: "check your username/password"}"))
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val token = JSONObject(body).optString("token")
                if (token.isBlank()) {
                    Result.failure(Exception("Login succeeded but no token was returned"))
                } else {
                    Result.success(token)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** Downloads the actual subtitle text for a given file_id (requires a login token). */
    suspend fun download(apiKey: String, token: String, fileId: Long): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val conn = openConnection("$BASE_URL/download", "POST", apiKey, token)
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                val payload = JSONObject().apply {
                    put("file_id", fileId)
                    put("sub_format", "srt")
                }
                OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }

                val code = conn.responseCode
                if (code != 200) {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
                    conn.disconnect()
                    return@withContext Result.failure(
                        Exception(
                            if (code == 406) "Daily download quota reached for this account"
                            else "Download failed ($code): ${err ?: "no details"}"
                        )
                    )
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val link = JSONObject(body).optString("link")
                if (link.isBlank()) return@withContext Result.failure(Exception("No download link returned"))

                // The /download response gives a short-lived signed URL - fetch the actual file from it.
                val fileConn = URL(link).openConnection() as HttpURLConnection
                fileConn.connectTimeout = 10000
                fileConn.readTimeout = 10000
                val srtText = fileConn.inputStream.bufferedReader().use { it.readText() }
                fileConn.disconnect()
                Result.success(srtText)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun openConnection(urlString: String, method: String, apiKey: String, bearerToken: String? = null): HttpURLConnection {
        val conn = URL(urlString).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("Api-Key", apiKey.trim())
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.setRequestProperty("Accept", "application/json")
        if (bearerToken != null) {
            conn.setRequestProperty("Authorization", "Bearer $bearerToken")
        }
        return conn
    }
}
