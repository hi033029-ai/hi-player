package com.example.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.JellyfinMediaSourceClient
import com.example.data.MediaSourcePreferences
import com.example.data.GoogleDriveMediaSourceClient
import com.example.data.PublicCatalogClient
import com.example.data.WebDavMediaSourceClient
import com.example.model.DiscoverDetails
import com.example.model.DiscoverTitle
import com.example.model.DiscoverUiState
import com.example.model.JellyfinConnection
import com.example.model.MediaSourceSettings
import com.example.model.RemoteMediaMatch
import com.example.model.GoogleDriveConnection
import com.example.model.WebDavConnection
import com.example.player.RemoteStreamHeadersRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
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

    private val _uiState = MutableStateFlow(DiscoverUiState())
    val uiState = _uiState.asStateFlow()

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
        // The Discover destination is retired. Settings may still manage saved
        // personal-source credentials without starting catalog requests.
    }

    fun setQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query, errorMessage = null)
        if (query.isBlank() && _uiState.value.results.isNotEmpty()) {
            loadHome()
        }
    }

    /** Loads TMDB metadata-only sections when Discover opens or search is cleared. */
    fun loadHome() {
        if (_uiState.value.isLoadingHome) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoadingHome = true,
                results = emptyList(),
                googleDriveFiles = emptyList(),
                googleDriveMessage = null,
                selectedDetails = null,
                sourceMatches = emptyList(),
                errorMessage = null,
                sourceMessage = null,
            )
            runCatching { PublicCatalogClient.home() }
                .onSuccess { sections ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingHome = false,
                        homeSections = sections,
                        errorMessage = if (sections.isEmpty()) {
                            "TMDB did not return any titles. Please try again."
                        } else {
                            null
                        },
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingHome = false,
                        homeSections = emptyList(),
                        errorMessage = com.example.ui.feedback.UserFacingErrors.report("catalog_home", error, "Couldn't load titles. Check your connection and try again."),
                    )
                }
        }
    }

    fun searchTitles() {
        val query = _uiState.value.query.trim()
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Enter a movie or TV-series title.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                homeSections = emptyList(),
                results = emptyList(),
                googleDriveFiles = emptyList(),
                googleDriveMessage = null,
                selectedDetails = null,
                sourceMatches = emptyList(),
                errorMessage = null,
                sourceMessage = null
            )
            runCatching { PublicCatalogClient.search(query) }
                .onSuccess { results ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        results = results,
                        errorMessage = if (results.isEmpty()) "No catalog titles matched \"$query\"." else null
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = com.example.ui.feedback.UserFacingErrors.report("catalog_search", error, "Couldn't search titles. Check your connection and try again.")
                    )
                }
        }
    }

    fun browseGoogleDrive() {
        val connection = sourceSettings.value.googleDrive
        if (connection?.isConfigured != true) {
            _uiState.value = _uiState.value.copy(
                googleDriveFiles = emptyList(),
                googleDriveMessage = "Connect a Google Drive folder in Settings first."
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isBrowsingGoogleDrive = true,
                homeSections = emptyList(),
                results = emptyList(),
                googleDriveFiles = emptyList(),
                googleDriveMessage = null,
                selectedDetails = null,
                errorMessage = null
            )
            runCatching {
                GoogleDriveMediaSourceClient.search(getApplication(), connection, query = "")
            }.onSuccess { files ->
                _uiState.value = _uiState.value.copy(
                    isBrowsingGoogleDrive = false,
                    googleDriveFiles = files,
                    googleDriveMessage = if (files.isEmpty()) {
                        "No supported video files were found in ${connection.displayName}."
                    } else {
                        "${files.size} video file${if (files.size == 1) "" else "s"} in ${connection.displayName}"
                    }
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isBrowsingGoogleDrive = false,
                    googleDriveFiles = emptyList(),
                    googleDriveMessage = com.example.ui.feedback.UserFacingErrors.report("drive_folder", error, "Couldn't open this Drive folder. Reconnect Drive in Settings and try again.")
                )
            }
        }
    }

    fun selectTitle(title: DiscoverTitle) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                selectedDetails = null,
                sourceMatches = emptyList(),
                errorMessage = null,
                sourceMessage = null
            )
            val details = runCatching { PublicCatalogClient.details(title) }.getOrNull()
                ?: DiscoverDetails(title = title)
            _uiState.value = _uiState.value.copy(isLoading = false, selectedDetails = details)
            searchConnectedSources(details.title)
        }
    }

    fun closeDetails() {
        _uiState.value = _uiState.value.copy(
            selectedDetails = null,
            sourceMatches = emptyList(),
            sourceMessage = null,
            errorMessage = null
        )
    }

    fun searchConnectedSources(title: DiscoverTitle) {
        val sources = sourceSettings.value
        if (!sources.hasAnySource) {
            _uiState.value = _uiState.value.copy(
                sourceMatches = emptyList(),
                sourceMessage = "Connect a Jellyfin server or WebDAV folder in Settings. Google Drive media is available from your signed-in Google account."
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSearchingSources = true,
                sourceMatches = emptyList(),
                sourceMessage = "Searching your connected media sources…"
            )
            // Media servers and filenames often omit the release year; use the
            // canonical title rather than over-filtering valid copies.
            val query = title.title
            val result = runCatching { searchAllSources(sources, query) }
            result.onSuccess { matches ->
                val ordered = matches.sortedWith(
                    compareByDescending<RemoteMediaMatch> { it.is4k }
                        .thenByDescending { it.isHdr }
                        .thenBy { it.title.lowercase() }
                )
                _uiState.value = _uiState.value.copy(
                    isSearchingSources = false,
                    sourceMatches = ordered,
                    sourceMessage = if (ordered.isEmpty()) {
                        "No matching copy was found in your connected sources."
                    } else {
                        "${ordered.size} matching item${if (ordered.size == 1) "" else "s"} in your sources"
                    }
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isSearchingSources = false,
                    sourceMessage = com.example.ui.feedback.UserFacingErrors.report("source_search", error, "Couldn't search your media. Check the server connection in Settings and try again.")
                )
            }
        }
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

    fun preparePlayback(match: RemoteMediaMatch) {
        RemoteStreamHeadersRegistry.register(match.streamUri.toString(), match.requestHeaders)
    }

    private suspend fun searchAllSources(settings: MediaSourceSettings, query: String): List<RemoteMediaMatch> {
        return coroutineScope {
            val jellyfin = settings.jellyfin
            val webDav = settings.webDav
            val jellyfinSearch: Deferred<List<RemoteMediaMatch>>? = if (jellyfin?.isConfigured == true) {
                async { runCatching { JellyfinMediaSourceClient.search(jellyfin, query) }.getOrDefault(emptyList()) }
            } else null
            val webDavSearch: Deferred<List<RemoteMediaMatch>>? = if (webDav?.isConfigured == true) {
                async { runCatching { WebDavMediaSourceClient.search(webDav, query) }.getOrDefault(emptyList()) }
            } else null
            buildList {
                jellyfinSearch?.await()?.let(::addAll)
                webDavSearch?.await()?.let(::addAll)
            }
        }
    }
}
