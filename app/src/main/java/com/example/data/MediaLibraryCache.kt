package com.example.data

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import com.example.model.VideoItem
import com.example.model.AudioItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** App-private durable snapshots. Call on IO; incomplete scans never replace these. */
internal object MediaLibraryCache {
    private fun file(context: Context, kind: String) = AtomicFile(File(context.noBackupFilesDir, "library_$kind.json"))
    @Synchronized private fun read(context: Context, kind: String): JSONArray? = runCatching {
        val root = JSONObject(file(context, kind).openRead().bufferedReader().use { it.readText() })
        require(root.getInt("version") == 2)
        root.getJSONArray("items")
    }.getOrNull()
    @Synchronized private fun write(context: Context, kind: String, items: JSONArray) {
        val target = file(context, kind)
        var stream: java.io.FileOutputStream? = null
        try {
            stream = target.startWrite()
            stream.write(JSONObject().put("version", 2).put("items", items).toString().toByteArray(Charsets.UTF_8))
            target.finishWrite(stream)
        } catch (e: Exception) {
            target.failWrite(stream)
            android.util.Log.w("HiLibrary", "Could not persist $kind snapshot", e)
        }
    }
    @Synchronized fun clear(context: Context, audio: Boolean) { file(context, if (audio) "audio" else "videos").delete() }
    private fun local(uri: Uri) = uri.scheme == "content" || uri.scheme == "file"
    fun saveVideos(context: Context, items: List<VideoItem>) {
        if (!LibraryAccessPolicy.canRestoreCache(LibraryAccessPolicy.scope(context, audio = false))) { clear(context, false); return }
        val array = JSONArray()
        items.filter { local(it.uri) && !it.isPartialDownload }.forEach { item ->
            array.put(JSONObject()
                .put("id", item.id)
                .put("uri", item.uri.toString())
                .put("title", item.title)
                .put("durationMs", item.durationMs)
                .put("sizeBytes", item.sizeBytes)
                .put("width", item.width)
                .put("height", item.height)
                .put("mimeType", item.mimeType)
                .put("dateAdded", item.dateAdded)
                .put("folderName", item.folderName)
                .put("path", item.path)
                .put("isHdr", item.isHdr)
                .put("hdrFormat", item.hdrFormat)
                .put("codec", item.codec)
                .put("bitrate", item.bitrate)
                .put("frameRate", item.frameRate)
                .put("audioChannels", item.audioChannels)
                .put("isNewVideo", item.isNewVideo)
            )
        }
        write(context, "videos", array)
    }
    fun readVideos(context: Context): List<VideoItem>? = runCatching {
        if (!LibraryAccessPolicy.canRestoreCache(LibraryAccessPolicy.scope(context, audio = false))) { clear(context, false); return null }
        val array = read(context, "videos") ?: return null
        List(array.length()) { index ->
            val o = array.getJSONObject(index)
            VideoItem(
                id = o.getLong("id"),
                uri = Uri.parse(o.getString("uri")),
                title = o.getString("title"),
                durationMs = o.getLong("durationMs"),
                sizeBytes = o.getLong("sizeBytes"),
                width = o.getInt("width"),
                height = o.getInt("height"),
                mimeType = o.getString("mimeType"),
                dateAdded = o.getLong("dateAdded"),
                folderName = o.getString("folderName"),
                path = o.getString("path"),
                isHdr = o.getBoolean("isHdr"),
                hdrFormat = if (o.isNull("hdrFormat")) null else o.getString("hdrFormat"),
                codec = o.getString("codec"),
                bitrate = o.getLong("bitrate"),
                frameRate = o.getDouble("frameRate").toFloat(),
                audioChannels = o.getInt("audioChannels"),
                isNewVideo = o.getBoolean("isNewVideo"),
            )
        }
    }.getOrNull()
    fun saveAudio(context: Context, items: List<AudioItem>) {
        if (!LibraryAccessPolicy.canRestoreCache(LibraryAccessPolicy.scope(context, audio = true))) { clear(context, true); return }
        val array = JSONArray()
        items.filter { local(it.uri) }.forEach { item ->
            array.put(JSONObject()
                .put("id", item.id)
                .put("uri", item.uri.toString())
                .put("title", item.title)
                .put("artist", item.artist)
                .put("album", item.album)
                .put("durationMs", item.durationMs)
                .put("sizeBytes", item.sizeBytes)
                .put("path", item.path)
                .put("mimeType", item.mimeType)
                .put("dateAdded", item.dateAdded)
                .put("albumId", item.albumId)
                .put("albumArtUri", item.albumArtUri?.toString())
            )
        }
        write(context, "audio", array)
    }
    fun readAudio(context: Context): List<AudioItem>? = runCatching {
        if (!LibraryAccessPolicy.canRestoreCache(LibraryAccessPolicy.scope(context, audio = true))) { clear(context, true); return null }
        val array = read(context, "audio") ?: return null
        List(array.length()) { index ->
            val o = array.getJSONObject(index)
            AudioItem(
                id = o.getLong("id"),
                uri = Uri.parse(o.getString("uri")),
                title = o.getString("title"),
                artist = o.getString("artist"),
                album = o.getString("album"),
                durationMs = o.getLong("durationMs"),
                sizeBytes = o.getLong("sizeBytes"),
                path = o.getString("path"),
                mimeType = o.getString("mimeType"),
                dateAdded = o.getLong("dateAdded"),
                albumId = o.getLong("albumId"),
                albumArtUri = if (o.isNull("albumArtUri")) null else Uri.parse(o.getString("albumArtUri")),
            )
        }
    }.getOrNull()
}
