package com.example.util

import android.net.Uri
import java.net.URL
import java.net.HttpURLConnection
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal object PublicSubtitleSearch {
    data class Match(val title: String, val file: String, val id: String)
    private const val root = "https://archive.org"
    private fun cc0(value: String) = value.trim().removePrefix("https://").removePrefix("http://").trimEnd('/') == "creativecommons.org/publicdomain/zero/1.0"

    private suspend fun read(url: String, limit: Int): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 7000
        connection.readTimeout = 10000
        try {
            require(connection.responseCode == 200)
            return connection.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(count <= limit - out.size())
                    out.write(buffer, 0, count)
                }
                out.toByteArray()
            }
        } finally { connection.disconnect() }
    }

    suspend fun search(title: String): List<Match> = withContext(Dispatchers.IO) {
        val words = Regex("[\\p{L}\\p{N}]+").findAll(title.take(150)).take(12).joinToString(" ") { it.value }
        if (words.isBlank()) return@withContext emptyList()
        val q = "title:\"$words\" AND licenseurl:(\"https://creativecommons.org/publicdomain/zero/1.0/\" OR \"http://creativecommons.org/publicdomain/zero/1.0/\")"
        val query = Uri.parse("$root/advancedsearch.php").buildUpon().appendQueryParameter("q", q)
            .appendQueryParameter("fl[]", "identifier").appendQueryParameter("rows", "8").appendQueryParameter("output", "json").build()
        val docs = JSONObject(read(query.toString(), 128000).decodeToString()).getJSONObject("response").getJSONArray("docs")
        buildList {
            for (i in 0 until docs.length()) {
                val id = docs.getJSONObject(i).optString("identifier")
                if (!id.matches(Regex("[a-zA-Z0-9_.-]+")) || id == "." || id == "..") continue
                val item = JSONObject(read("$root/metadata/$id", 4_000_000).decodeToString())
                val meta = item.getJSONObject("metadata")
                if (!cc0(meta.optString("licenseurl"))) continue
                val files = item.getJSONArray("files")
                for (j in 0 until files.length()) {
                    val file = files.getJSONObject(j)
                    val name = file.optString("name")
                    if (file.optString("source") != "original" || file.optBoolean("private") ||
                        !cc0(file.optString("licenseurl", meta.optString("licenseurl"))) ||
                        name.substringAfterLast('.').lowercase() !in setOf("srt", "vtt") ||
                        name.contains('/') || name.contains('\\')) continue
                    add(Match(meta.optString("title"), name, id))
                }
            }
        }
    }

    suspend fun download(match: Match, dir: File): File = withContext(Dispatchers.IO) {
        val bytes = read("$root/download/${match.id}/${Uri.encode(match.file)}", 2_000_000)
        val text = bytes.decodeToString().removePrefix("\uFEFF")
        require(text.startsWith("WEBVTT") || Regex("(?m)^\\s*\\d{1,2}:\\d{2}:\\d{2}[,.]\\d{3}\\s+-->").containsMatchIn(text))
        File.createTempFile("cc0-caption-", "." + match.file.substringAfterLast('.').lowercase(), dir).apply { writeBytes(bytes) }
    }
}
