package com.example.data

import android.net.Uri
import com.example.model.GoogleDriveConnection
import com.example.model.RemoteMediaMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Direct client for browsing and streaming media files from Google Drive v3 REST API.
 * Uses OAuth 2.0 Access Token to query Drive files and builds direct streaming URIs
 * compatible with ExoPlayer / Media3 via authenticated HttpDataSource headers.
 */
object GoogleDriveMediaSourceClient {

    private const val DRIVE_API_BASE = "https://www.googleapis.com/drive/v3/files"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Common video extensions scanned from Google Drive.
     */
    private val VIDEO_MIME_TYPES = setOf(
        "video/mp4",
        "video/x-matroska",
        "video/webm",
        "video/quicktime",
        "video/x-msvideo",
        "video/mpeg",
        "video/3gpp"
    )

    fun requestHeaders(connection: GoogleDriveConnection): Map<String, String> {
        return mapOf(
            "Authorization" to "Bearer ${connection.accessToken.trim()}"
        )
    }

    suspend fun verify(connection: GoogleDriveConnection): Unit = withContext(Dispatchers.IO) {
        val testUrl = "$DRIVE_API_BASE?pageSize=1&fields=files(id,name)"
        val request = Request.Builder()
            .url(testUrl)
            .header("Authorization", "Bearer ${connection.accessToken.trim()}")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val code = response.code
                val errBody = response.body?.string().orEmpty()
                val message = if (code == 401) {
                    "Google Drive access token expired or invalid."
                } else {
                    "Google Drive error ($code): $errBody"
                }
                throw IllegalStateException(message)
            }
        }
    }

    suspend fun search(
        connection: GoogleDriveConnection,
        query: String
    ): List<RemoteMediaMatch> = withContext(Dispatchers.IO) {
        if (!connection.isConfigured) return@withContext emptyList()

        val cleanQuery = query.trim().replace("'", "\\'")
        // Search for video files matching name in Drive
        val qFilter = "trashed = false and mimeType contains 'video/'" +
                if (cleanQuery.isNotBlank()) " and name contains '$cleanQuery'" else ""

        val url = Uri.parse(DRIVE_API_BASE).buildUpon()
            .appendQueryParameter("q", qFilter)
            .appendQueryParameter("pageSize", "50")
            .appendQueryParameter("fields", "files(id,name,mimeType,size,videoMediaMetadata,thumbnailLink)")
            .build()
            .toString()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${connection.accessToken.trim()}")
            .get()
            .build()

        val json = httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            JSONObject(response.body?.string() ?: return@withContext emptyList())
        }

        val filesArray = json.optJSONArray("files") ?: return@withContext emptyList()
        val headers = requestHeaders(connection)

        buildList {
            for (i in 0 until filesArray.length()) {
                val file = filesArray.optJSONObject(i) ?: continue
                val id = file.optString("id")
                val name = file.optString("name")
                val mimeType = file.optString("mimeType", "video/mp4")
                val size = file.optLong("size", 0L)
                val thumb = file.optString("thumbnailLink").takeIf(String::isNotBlank)

                val videoMeta = file.optJSONObject("videoMediaMetadata")
                val width = videoMeta?.optInt("width", 0) ?: 0
                val height = videoMeta?.optInt("height", 0) ?: 0
                val durationMs = videoMeta?.optLong("durationMillis", 0L) ?: 0L

                // Direct streaming media link for ExoPlayer:
                // GET https://www.googleapis.com/drive/v3/files/{id}?alt=media
                val streamUri = Uri.parse("$DRIVE_API_BASE/$id?alt=media")

                add(
                    RemoteMediaMatch(
                        id = "gdrive:$id",
                        sourceName = connection.displayName,
                        title = name,
                        subtitle = "Google Drive",
                        streamUri = streamUri,
                        streamMimeType = mimeType,
                        requestHeaders = headers,
                        width = width,
                        height = height,
                        durationMs = durationMs,
                        sizeBytes = size,
                        artworkUrl = thumb
                    )
                )
            }
        }
    }
}
