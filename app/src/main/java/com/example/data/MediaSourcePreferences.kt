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
