package com.example.drive

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

internal data class DriveMediaFile(val id: String, val name: String, val mimeType: String, val size: Long, val canDownload: Boolean) {
    val isFolder get() = mimeType == "application/vnd.google-apps.folder"
    val isPlayable get() = mimeType.startsWith("video/") || mimeType.startsWith("audio/")
}
internal data class DriveMediaPage(val files: List<DriveMediaFile>, val nextPage: String?)
internal object GoogleDriveApi {
    val http = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private fun json(context: Context, owner: GoogleDriveSession.Owner, url: String): JSONObject {
        repeat(2) { attempt ->
            GoogleDriveSession.requireOwner(owner)
            val token = GoogleDriveSession.token(context, owner, force = attempt > 0)
            http.newCall(Request.Builder().url(url).header("Authorization", "Bearer $token").build()).execute().use { response ->
                GoogleDriveSession.requireOwner(owner)
                if (response.code == 401 && attempt == 0) return@repeat
                if (response.code == 403) throw IOException("Drive access is unavailable. Check permission, Drive API enablement, and OAuth review status.")
                if (!response.isSuccessful) throw IOException("Drive request failed (${response.code}). Please retry.")
                return JSONObject(response.body?.string() ?: throw IOException("Empty Drive response"))
            }
        }
        throw IOException("Drive access expired. Reconnect Drive.")
    }
    fun verifyAccount(context: Context, owner: GoogleDriveSession.Owner) {
        val email = json(context, owner, "${GoogleDrivePolicy.API_ROOT}/about?fields=user(emailAddress)").getJSONObject("user").optString("emailAddress")
        check(email.equals(owner.email, true)) { "Drive returned a different account. Sign in again." }
    }
    fun list(context: Context, owner: GoogleDriveSession.Owner, folder: String, query: String, page: String?): DriveMediaPage {
        require(folder == "root" || GoogleDrivePolicy.validFileId(folder))
        val terms = mutableListOf("trashed = false", "(mimeType = 'application/vnd.google-apps.folder' or mimeType contains 'video/' or mimeType contains 'audio/')")
        if (query.isBlank()) terms += "'${GoogleDrivePolicy.escapeQuery(folder)}' in parents" else terms += "name contains '${GoogleDrivePolicy.escapeQuery(query.trim())}'"
        val url = "${GoogleDrivePolicy.API_ROOT}/files".toHttpUrl().newBuilder().addQueryParameter("q", terms.joinToString(" and "))
            .addQueryParameter("fields", "nextPageToken,files(id,name,mimeType,size,capabilities(canDownload))")
            .addQueryParameter("orderBy", "folder,name_natural").addQueryParameter("pageSize", "100")
            .addQueryParameter("supportsAllDrives", "true").addQueryParameter("includeItemsFromAllDrives", "true")
            .apply { page?.let { addQueryParameter("pageToken", it) } }.build().toString()
        val result = json(context, owner, url); val files = result.optJSONArray("files")
        return DriveMediaPage(List(files?.length() ?: 0) { index ->
            val f = files!!.getJSONObject(index)
            DriveMediaFile(f.getString("id"), f.getString("name"), f.getString("mimeType"), f.optString("size").toLongOrNull() ?: 0,
                f.optJSONObject("capabilities")?.optBoolean("canDownload", false) == true)
        }, result.optString("nextPageToken").takeIf(String::isNotBlank))
    }
}
