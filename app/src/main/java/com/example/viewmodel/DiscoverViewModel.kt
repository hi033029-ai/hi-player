package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.JellyfinMediaSourceClient
import com.example.data.MediaSourcePreferences
import com.example.data.PlayerPreferencesRepository
import com.example.data.TmdbDiscoverClient
import com.example.data.WebDavMediaSourceClient
import com.example.model.DiscoverDetails
import com.example.model.DiscoverTitle
import com.example.model.DiscoverUiState
import com.example.model.JellyfinConnection
import com.example.model.MediaSourceSettings
import com.example.model.RemoteMediaMatch
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DiscoverViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = PlayerPreferencesRepository(application)
    private val mediaSources = MediaSourcePreferences(application)

    val sourceSettings = mediaSources.settings

    val tmdbConfigured = preferences.settingsFlow
        .map { it.tmdbApiKey.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

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
    }

    fun setQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query, errorMessage = null)
    }

    fun searchTitles() {
        val query = _uiState.value.query.trim()
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Enter a movie or TV-series title.")
            return
        }
        viewModelScope.launch {
            val apiKey = preferences.settingsFlow.first().tmdbApiKey
            if (apiKey.isBlank()) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Add your free TMDB API key in Settings to search the catalog."
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                results = emptyList(),
                selectedDetails = null,
                sourceMatches = emptyList(),
                errorMessage = null,
                sourceMessage = null
            )
            runCatching { TmdbDiscoverClient.search(apiKey, query) }
                .onSuccess { results ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        results = results,
                        errorMessage = if (results.isEmpty()) "No TMDB titles matched \"$query\"." else null
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "TMDB search failed: ${error.message ?: "check your network and key"}"
                    )
                }
        }
    }

    fun selectTitle(title: DiscoverTitle) {
        viewModelScope.launch {
            val apiKey = preferences.settingsFlow.first().tmdbApiKey
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                selectedDetails = null,
                sourceMatches = emptyList(),
                errorMessage = null,
                sourceMessage = null
            )
            val details = runCatching { TmdbDiscoverClient.details(apiKey, title) }.getOrNull()
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
                sourceMessage = "Connect a Jellyfin server or WebDAV folder in Settings to find your own playable copies."
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
                    sourceMessage = "Source search failed: ${error.message ?: "check the source connection"}"
                )
            }
        }
    }

    fun connectJellyfin(baseUrl: String, username: String, password: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = JellyfinMediaSourceClient.authenticate(baseUrl, username, password)
                .map { connection ->
                    mediaSources.saveJellyfin(connection)
                    RemoteStreamHeadersRegistry.register(connection.baseUrl, JellyfinMediaSourceClient.requestHeaders(connection))
                }
            onResult(result)
        }
    }

    fun saveWebDav(folderUrl: String, username: String, password: String, displayName: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                val connection = WebDavConnection(
                    folderUrl = folderUrl.trim(),
                    username = username.trim(),
                    password = password,
                    displayName = displayName.trim().ifBlank { "WebDAV" }
                )
                require(connection.isConfigured) { "Enter the WebDAV folder URL, username, and password." }
                // Validate the configured folder itself without performing a full
                // media crawl during setup.
                WebDavMediaSourceClient.verify(connection)
                mediaSources.saveWebDav(connection)
                RemoteStreamHeadersRegistry.register(connection.folderUrl, WebDavMediaSourceClient.requestHeaders(connection))
            }
            onResult(result)
        }
    }

    fun removeJellyfin() {
        sourceSettings.value.jellyfin?.baseUrl?.let(RemoteStreamHeadersRegistry::clear)
        mediaSources.clearJellyfin()
    }

    fun removeWebDav() {
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
