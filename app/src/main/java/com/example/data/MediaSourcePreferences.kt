package com.example.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.model.JellyfinConnection
import com.example.model.MediaSourceSettings
import com.example.model.GoogleDriveConnection
import com.example.model.WebDavConnection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Stores user-owned Jellyfin/WebDAV credentials and Google Drive folder grants
 * on this device. Sensitive server credentials are encrypted with the Android
 * Keystore-backed [EncryptedSharedPreferences] store and never enter a URL.
 */
data class SavedMediaSource(val id: String, val name: String, val kind: String)
internal data class SavedSourceCredentials(val metadata: SavedMediaSource, val jellyfin: JellyfinConnection? = null, val webDav: WebDavConnection? = null)

class MediaSourcePreferences(context: Context) {
    private val appContext = context.applicationContext
    private val preferences by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val _settings = MutableStateFlow(readSettings())
    val settings = _settings.asStateFlow()

    private val _savedProfiles = MutableStateFlow(readSavedMetadata())
    val savedProfiles = _savedProfiles.asStateFlow()

    private fun profileArray(): org.json.JSONArray = runCatching {
        org.json.JSONArray(preferences.getString("saved_source_profiles_v1", "[]"))
    }.getOrDefault(org.json.JSONArray())

    private fun readSavedMetadata(): List<SavedMediaSource> {
        val array = profileArray()
        return (0 until minOf(array.length(), 12)).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val kind = item.optString("kind").takeIf { it in listOf("jellyfin", "webdav") } ?: return@mapNotNull null
            SavedMediaSource(item.optString("id"), item.optString("name"), kind)
        }
    }

    @Synchronized fun saveCurrentProfile(name: String, kind: String) {
        val label = name.trim().take(40)
        require(label.isNotBlank())
        val item = org.json.JSONObject().put("id", java.util.UUID.randomUUID().toString()).put("name", label).put("kind", kind)
        when (kind) {
            "jellyfin" -> {
                val source = requireNotNull(_settings.value.jellyfin?.takeIf { it.isConfigured })
                item.put("url", source.baseUrl).put("username", source.username).put("password", source.password)
                    .put("token", source.accessToken).put("userId", source.userId).put("serverName", source.serverName)
            }
            "webdav" -> {
                val source = requireNotNull(_settings.value.webDav?.takeIf { it.isConfigured })
                item.put("url", source.folderUrl).put("username", source.username).put("password", source.password).put("displayName", source.displayName)
            }
            else -> error("Unsupported source")
        }
        val array = profileArray()
        require(array.length() < 12)
        array.put(item)
        check(preferences.edit().putString("saved_source_profiles_v1", array.toString()).commit())
        _savedProfiles.value = readSavedMetadata()
    }

    @Synchronized internal fun savedCredentials(id: String): SavedSourceCredentials {
        val array = profileArray()
        val item = (0 until array.length()).mapNotNull { array.optJSONObject(it) }.first { it.optString("id") == id }
        val metadata = SavedMediaSource(id, item.getString("name"), item.getString("kind"))
        return if (metadata.kind == "jellyfin") SavedSourceCredentials(metadata, jellyfin = JellyfinConnection(
            item.getString("url"), item.getString("username"), item.getString("password"), item.getString("token"), item.getString("userId"), item.getString("serverName")))
        else SavedSourceCredentials(metadata, webDav = WebDavConnection(item.getString("url"), item.getString("username"), item.getString("password"), item.getString("displayName")))
    }

    @Synchronized fun deleteSavedProfile(id: String) {
        val array = profileArray()
        val kept = (0 until array.length()).mapNotNull { array.optJSONObject(it) }.filterNot { it.optString("id") == id }
        check(preferences.edit().putString("saved_source_profiles_v1", org.json.JSONArray(kept).toString()).commit())
        _savedProfiles.value = readSavedMetadata()
    }

    fun saveJellyfin(connection: JellyfinConnection) {
        preferences.edit()
            .putString(KEY_JELLYFIN_BASE_URL, connection.baseUrl.trimEnd('/'))
            .putString(KEY_JELLYFIN_USERNAME, connection.username)
            .putString(KEY_JELLYFIN_PASSWORD, connection.password)
            .putString(KEY_JELLYFIN_TOKEN, connection.accessToken)
            .putString(KEY_JELLYFIN_USER_ID, connection.userId)
            .putString(KEY_JELLYFIN_SERVER_NAME, connection.serverName)
            .apply()
        _settings.value = readSettings()
    }

    fun clearJellyfin() {
        preferences.edit()
            .remove(KEY_JELLYFIN_BASE_URL)
            .remove(KEY_JELLYFIN_USERNAME)
            .remove(KEY_JELLYFIN_PASSWORD)
            .remove(KEY_JELLYFIN_TOKEN)
            .remove(KEY_JELLYFIN_USER_ID)
            .remove(KEY_JELLYFIN_SERVER_NAME)
            .apply()
        _settings.value = readSettings()
    }

    fun saveGoogleDrive(connection: GoogleDriveConnection) {
        preferences.edit()
            .putString(KEY_GDRIVE_ACCOUNT, connection.accountName)
            .putString(KEY_GDRIVE_DOCUMENT_TREE_URI, connection.documentTreeUri)
            .putString(KEY_GDRIVE_NAME, connection.displayName)
            .remove(LEGACY_KEY_GDRIVE_TOKEN)
            .remove(LEGACY_KEY_GDRIVE_FOLDER)
            .apply()
        _settings.value = readSettings()
    }

    fun clearGoogleDrive() {
        preferences.edit()
            .remove(KEY_GDRIVE_ACCOUNT)
            .remove(KEY_GDRIVE_DOCUMENT_TREE_URI)
            .remove(KEY_GDRIVE_NAME)
            .remove(LEGACY_KEY_GDRIVE_TOKEN)
            .remove(LEGACY_KEY_GDRIVE_FOLDER)
            .apply()
        _settings.value = readSettings()
    }

    fun saveWebDav(connection: WebDavConnection) {
        preferences.edit()
            .putString(KEY_WEBDAV_FOLDER_URL, connection.folderUrl.trimEnd('/') + "/")
            .putString(KEY_WEBDAV_USERNAME, connection.username)
            .putString(KEY_WEBDAV_PASSWORD, connection.password)
            .putString(KEY_WEBDAV_DISPLAY_NAME, connection.displayName)
            .apply()
        _settings.value = readSettings()
    }

    fun clearWebDav() {
        preferences.edit()
            .remove(KEY_WEBDAV_FOLDER_URL)
            .remove(KEY_WEBDAV_USERNAME)
            .remove(KEY_WEBDAV_PASSWORD)
            .remove(KEY_WEBDAV_DISPLAY_NAME)
            .apply()
        _settings.value = readSettings()
    }

    private fun readSettings(): MediaSourceSettings {
        val jellyfinBase = preferences.getString(KEY_JELLYFIN_BASE_URL, "").orEmpty()
        val jellyfin = if (jellyfinBase.isBlank()) {
            null
        } else {
            JellyfinConnection(
                baseUrl = jellyfinBase,
                username = preferences.getString(KEY_JELLYFIN_USERNAME, "").orEmpty(),
                password = preferences.getString(KEY_JELLYFIN_PASSWORD, "").orEmpty(),
                accessToken = preferences.getString(KEY_JELLYFIN_TOKEN, "").orEmpty(),
                userId = preferences.getString(KEY_JELLYFIN_USER_ID, "").orEmpty(),
                serverName = preferences.getString(KEY_JELLYFIN_SERVER_NAME, "Jellyfin").orEmpty()
            )
        }
        val webDavFolder = preferences.getString(KEY_WEBDAV_FOLDER_URL, "").orEmpty()
        val webDav = if (webDavFolder.isBlank()) {
            null
        } else {
            WebDavConnection(
                folderUrl = webDavFolder,
                username = preferences.getString(KEY_WEBDAV_USERNAME, "").orEmpty(),
                password = preferences.getString(KEY_WEBDAV_PASSWORD, "").orEmpty(),
                displayName = preferences.getString(KEY_WEBDAV_DISPLAY_NAME, "WebDAV").orEmpty()
            )
        }
        val gDriveAccount = preferences.getString(KEY_GDRIVE_ACCOUNT, "").orEmpty()
        val gDriveTreeUri = preferences.getString(KEY_GDRIVE_DOCUMENT_TREE_URI, "").orEmpty()
        val googleDrive = if (gDriveTreeUri.isBlank()) {
            null
        } else {
            GoogleDriveConnection(
                accountName = gDriveAccount.ifBlank { "Google Drive" },
                documentTreeUri = gDriveTreeUri,
                displayName = preferences.getString(KEY_GDRIVE_NAME, "Google Drive").orEmpty().ifEmpty { "Google Drive" }
            )
        }
        return MediaSourceSettings(jellyfin = jellyfin, webDav = webDav, googleDrive = googleDrive)
    }

    private companion object {
        const val FILE_NAME = "hi_player_media_sources"
        const val KEY_JELLYFIN_BASE_URL = "jellyfin_base_url"
        const val KEY_JELLYFIN_USERNAME = "jellyfin_username"
        const val KEY_JELLYFIN_PASSWORD = "jellyfin_password"
        const val KEY_JELLYFIN_TOKEN = "jellyfin_token"
        const val KEY_JELLYFIN_USER_ID = "jellyfin_user_id"
        const val KEY_JELLYFIN_SERVER_NAME = "jellyfin_server_name"
        const val KEY_WEBDAV_FOLDER_URL = "webdav_folder_url"
        const val KEY_WEBDAV_USERNAME = "webdav_username"
        const val KEY_WEBDAV_PASSWORD = "webdav_password"
        const val KEY_WEBDAV_DISPLAY_NAME = "webdav_display_name"
        const val KEY_GDRIVE_ACCOUNT = "gdrive_account"
        const val KEY_GDRIVE_DOCUMENT_TREE_URI = "gdrive_document_tree_uri"
        const val KEY_GDRIVE_NAME = "gdrive_name"
        const val LEGACY_KEY_GDRIVE_TOKEN = "gdrive_token"
        const val LEGACY_KEY_GDRIVE_FOLDER = "gdrive_folder"
    }
}
