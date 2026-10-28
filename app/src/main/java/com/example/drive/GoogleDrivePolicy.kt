package com.example.drive

import java.net.URI

/** Only the exact HTTPS Drive API endpoint receives OAuth credentials. */
internal object GoogleDrivePolicy {
    const val READ_SCOPE = "https://www.googleapis.com/auth/drive.readonly"
    const val API_ROOT = "https://www.googleapis.com/drive/v3"
    fun validFileId(id: String) = id.matches(Regex("[A-Za-z0-9_-]{1,200}"))
    fun mediaUrl(id: String): String {
        require(validFileId(id)) { "Invalid Drive file identifier" }
        return "$API_ROOT/files/$id?alt=media"
    }
    fun isMediaUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host == "www.googleapis.com" && uri.userInfo == null &&
            (uri.port == -1 || uri.port == 443) &&
            uri.path.matches(Regex("/drive/v3/files/[A-Za-z0-9_-]{1,200}")) && uri.rawQuery == "alt=media"
    }.getOrDefault(false)
    fun safeRedirect(url: String): Boolean = runCatching {
        val u = URI(url)
        u.scheme == "https" && u.userInfo == null && (u.port == -1 || u.port == 443) &&
            (u.host == "www.googleapis.com" || u.host == "googleusercontent.com" || u.host?.endsWith(".googleusercontent.com") == true)
    }.getOrDefault(false)
    fun escapeQuery(value: String) = value.replace("\\", "\\\\").replace("'", "\\'")
}
