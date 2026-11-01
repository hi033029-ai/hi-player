package com.example.connections

import android.net.Uri
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URI

internal data class StorageFile(val id: String, val name: String, val folder: Boolean, val mime: String, val size: Long = 0) {
    val playable get() = !folder && (mime.startsWith("video/") || mime.startsWith("audio/"))
}
internal data class StoragePage(val files: List<StorageFile>, val next: String? = null, val note: String? = null)
internal object StoragePolicy {
    fun graph(url: String): Boolean = runCatching { val u = URI(url)
        u.scheme == "https" && u.host == "graph.microsoft.com" && (u.port == -1 || u.port == 443) &&
            u.rawUserInfo == null && u.rawFragment == null && u.path.startsWith("/v1.0/me/drive/") &&
            u.path.split('/').none { it == "." || it == ".." }
    }.getOrDefault(false)
    fun download(url: String): Boolean = runCatching { val u = URI(url)
        u.scheme == "https" && !u.host.isNullOrBlank() && u.rawUserInfo == null && u.rawFragment == null && (u.port == -1 || u.port == 443)
    }.getOrDefault(false)
    fun smbPath(raw: String): String {
        require(!raw.startsWith('/') && !raw.startsWith('\\') && ':' !in raw && '\u0000' !in raw)
        val parts = raw.replace('\\', '/').split('/')
        require(parts.none { it == "." || it == ".." })
        return parts.filter { it.isNotEmpty() }.joinToString("\\")
    }
    fun mime(name: String): String = when (name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)) {
        "mp4", "m4v" -> "video/mp4"; "mkv" -> "video/x-matroska"; "webm" -> "video/webm"; "avi" -> "video/x-msvideo"; "mov" -> "video/quicktime"; "ts", "m2ts" -> "video/mp2t"
        "mp3" -> "audio/mpeg"; "m4a" -> "audio/mp4"; "flac" -> "audio/flac"; "aac" -> "audio/aac"; "ogg", "opus" -> "audio/ogg"; "wav" -> "audio/wav"; else -> "application/octet-stream"
    }
}
internal object CloudFiles {
    fun list(provider: CloudProvider, sessionId: String, folder: String, next: String?): StoragePage {
        val session = PersonalCloud.requireSession(provider, sessionId)
        fun request(url: String, body: JSONObject? = null): JSONObject {
            val builder = Request.Builder().url(url).header("Authorization", "Bearer ${session.token}")
            if (body != null) builder.post(body.toString().toRequestBody("application/json".toMediaType()))
            return PersonalCloud.json(builder.build()).also { PersonalCloud.requireSession(provider, sessionId) }
        }
        if (provider == CloudProvider.ONEDRIVE) {
            val url = next ?: (if (folder == "root") "https://graph.microsoft.com/v1.0/me/drive/root/children" else "https://graph.microsoft.com/v1.0/me/drive/items/${Uri.encode(folder)}/children") + "?\$top=100&\$select=id,name,size,file,folder"
            require(StoragePolicy.graph(url))
            val result = request(url); val items = result.getJSONArray("value")
            val files = (0 until items.length()).map { i -> val item = items.getJSONObject(i)
                val name = item.getString("name")
                StorageFile(item.getString("id"), name, item.has("folder"), item.optJSONObject("file")?.optString("mimeType")?.ifBlank { StoragePolicy.mime(name) } ?: StoragePolicy.mime(name), item.optLong("size")) }
            val nextPage = result.optString("@odata.nextLink").takeIf { it.isNotBlank() }
            require(nextPage == null || StoragePolicy.graph(nextPage))
            return StoragePage(files, nextPage)
        }
        val result = if (next == null) request("https://api.dropboxapi.com/2/files/list_folder", JSONObject().put("path", if (folder == "root") "" else folder).put("recursive", false).put("limit", 100))
            else request("https://api.dropboxapi.com/2/files/list_folder/continue", JSONObject().put("cursor", next))
        val items = result.getJSONArray("entries")
        val files = (0 until items.length()).mapNotNull { i -> val item = items.getJSONObject(i)
            if (item.optString(".tag") == "deleted") null else {
                val name = item.getString("name")
                StorageFile(item.getString("id"), name, item.optString(".tag") == "folder", StoragePolicy.mime(name), item.optLong("size"))
            } }
        return StoragePage(files, if (result.optBoolean("has_more")) result.getString("cursor") else null)
    }
    /** Signed content URLs stay in this call chain only, never in media history or generic header registry. */
    fun download(provider: CloudProvider, sessionId: String, id: String): String {
        val session = PersonalCloud.requireSession(provider, sessionId)
        val builder = if (provider == CloudProvider.ONEDRIVE) Request.Builder().url("https://graph.microsoft.com/v1.0/me/drive/items/${Uri.encode(id)}")
            else Request.Builder().url("https://api.dropboxapi.com/2/files/get_temporary_link")
                .post(JSONObject().put("path", id).toString().toRequestBody("application/json".toMediaType()))
        val result = PersonalCloud.json(builder.header("Authorization", "Bearer ${session.token}").build())
        PersonalCloud.requireSession(provider, sessionId)
        val url = if (provider == CloudProvider.ONEDRIVE) {
            check(result.getString("id") == id); result.getString("@microsoft.graph.downloadUrl")
        } else { check(result.getJSONObject("metadata").getString("id") == id); result.getString("link") }
        require(StoragePolicy.download(url)); return url
    }
    fun mediaUri(provider: CloudProvider, sessionId: String, id: String): Uri = Uri.Builder().scheme("hiplayer-cloud").authority(provider.key).appendPath(sessionId).appendPath(id).build()
}
