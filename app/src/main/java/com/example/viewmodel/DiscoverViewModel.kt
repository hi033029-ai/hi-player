package com.example.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.JellyfinMediaSourceClient
import com.example.data.MediaSourcePreferences
import com.example.data.GoogleDriveMediaSourceClient
import com.example.data.WebDavMediaSourceClient
import com.example.model.JellyfinConnection
import com.example.model.GoogleDriveConnection
import com.example.model.WebDavConnection
import com.example.player.RemoteStreamHeadersRegistry
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

class DiscoverViewModel(application: Application) : AndroidViewModel(application) {
    private val mediaSources = MediaSourcePreferences(application)

    val sourceSettings = mediaSources.settings

    val savedSourceProfiles = mediaSources.savedProfiles
    // Main-thread generation prevents a slow verification from reconnecting a
    // source after Disconnect or a newer connection request.
    private var sourceGeneration = 0L
    private fun requireSourceGeneration(generation: Long) {
        if (generation != sourceGeneration) throw kotlinx.coroutines.CancellationException("Superseded connection")
    }
    private suspend fun commitJellyfin(generation: Long, connection: JellyfinConnection) =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
            currentCoroutineContext().ensureActive(); requireSourceGeneration(generation)
            sourceSettings.value.jellyfin?.baseUrl?.let(RemoteStreamHeadersRegistry::clear)
            mediaSources.saveJellyfin(connection)
            RemoteStreamHeadersRegistry.register(connection.baseUrl, JellyfinMediaSourceClient.requestHeaders(connection))
        }
    private suspend fun commitWebDav(generation: Long, connection: WebDavConnection) =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
            currentCoroutineContext().ensureActive(); requireSourceGeneration(generation)
            sourceSettings.value.webDav?.folderUrl?.let(RemoteStreamHeadersRegistry::clear)
            mediaSources.saveWebDav(connection)
            RemoteStreamHeadersRegistry.register(connection.folderUrl, WebDavMediaSourceClient.requestHeaders(connection))
        }

    internal fun beginJellyfinAuthorization(): Long = ++sourceGeneration
    internal suspend fun completeJellyfinAuthorization(generation: Long, connection: JellyfinConnection) {
        commitJellyfin(generation, connection)
    }

    suspend fun saveSourceProfile(name: String, kind: String) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        mediaSources.saveCurrentProfile(name, kind)
    }
    suspend fun deleteSourceProfile(id: String) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        mediaSources.deleteSavedProfile(id)
    }
    suspend fun activateSourceProfile(id: String) {
        val generation = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) { ++sourceGeneration }
        val source = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { mediaSources.savedCredentials(id) }
        source.jellyfin?.let { saved ->
            // Verify the existing device token. Revoked sessions require a fresh
            // Quick Connect/password login, not a retained account password.
            val verified = JellyfinMediaSourceClient.verifySession(saved)
            currentCoroutineContext().ensureActive()
            commitJellyfin(generation, verified)
        }
        source.webDav?.let { saved ->
            WebDavMediaSourceClient.verify(saved)
            currentCoroutineContext().ensureActive()
            commitWebDav(generation, saved)
        }
    }

    init {
        // Re-register saved source headers for any stream selected after an
        // activity recreation. Headers stay in memory and never enter a URL.
        viewModelScope.launch {
            sourceSettings.collect { settings ->
                settings.jellyfin?.takeIf { it.isConfigured }?.let { connection ->
                    RemoteStreamHeadersRegistry.register(connection.baseUrl, JellyfinMediaSourceClient.requestHeaders(connection))
                }
                settings.webDav?.takeIf { it.isConfigured }?.let { connection ->
                    RemoteStreamHeadersRegistry.register(connection.folderUrl, WebDavMediaSourceClient.requestHeaders(connection))
                }
            }
        }
        // Only user-connected personal media sources are managed here.
    }

    fun connectJellyfin(baseUrl: String, username: String, password: String, onResult: (Result<Unit>) -> Unit): Job = viewModelScope.launch {
        val generation = ++sourceGeneration
        val result = runCatching {
            val connection = JellyfinMediaSourceClient.authenticate(baseUrl, username, password).getOrThrow()
            currentCoroutineContext().ensureActive()
            commitJellyfin(generation, connection)
        }
        currentCoroutineContext().ensureActive()
        requireSourceGeneration(generation)
        onResult(result)
    }

    fun saveGoogleDriveFolder(
        treeUri: Uri,
        accountName: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val result = runCatching {
                val app = getApplication<Application>()
                require(accountName.isNotBlank()) { "Choose a Google account before selecting a Drive folder." }
                require(GoogleDriveMediaSourceClient.isGoogleDriveTree(treeUri)) {
                    "Choose a folder inside the Google Drive provider."
                }
                app.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                val connection = GoogleDriveConnection(
                    accountName = accountName.trim(),
                    documentTreeUri = treeUri.toString(),
                    displayName = GoogleDriveMediaSourceClient.selectedFolderName(app, treeUri)
                )
                GoogleDriveMediaSourceClient.verify(app, connection)
                mediaSources.saveGoogleDrive(connection)
            }
            onResult(result)
        }
    }

    fun removeGoogleDrive() {
        mediaSources.clearGoogleDrive()
    }

    fun saveWebDav(folderUrl: String, username: String, password: String, displayName: String, onResult: (Result<Unit>) -> Unit): Job = viewModelScope.launch {
        val generation = ++sourceGeneration
        val result = runCatching {
            val connection = WebDavConnection(
                folderUrl = WebDavMediaSourceClient.normalizeFolderUrl(folderUrl),
                username = username.trim(),
                password = password,
                displayName = displayName.trim().ifBlank { "WebDAV" }
            )
            require(connection.isConfigured) { "Enter the WebDAV folder URL, username, and password." }
            // Validate the configured folder itself without performing a full
            // media crawl during setup.
            WebDavMediaSourceClient.verify(connection)
            currentCoroutineContext().ensureActive()
            commitWebDav(generation, connection)
        }
        currentCoroutineContext().ensureActive()
        requireSourceGeneration(generation)
        onResult(result)
    }

    fun removeJellyfin() {
        sourceGeneration++
        sourceSettings.value.jellyfin?.baseUrl?.let(RemoteStreamHeadersRegistry::clear)
        mediaSources.clearJellyfin()
    }

    fun removeWebDav() {
        sourceGeneration++
        sourceSettings.value.webDav?.folderUrl?.let(RemoteStreamHeadersRegistry::clear)
        mediaSources.clearWebDav()
    }

}
