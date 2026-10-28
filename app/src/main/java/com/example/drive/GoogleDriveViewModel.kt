package com.example.drive

import android.app.Activity
import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

internal data class DriveFolder(val id: String, val name: String)
internal data class DriveBrowserState(
    val connected: Boolean = false, val busy: Boolean = false, val files: List<DriveMediaFile> = emptyList(),
    val path: List<DriveFolder> = listOf(DriveFolder("root", "My Drive")), val query: String = "",
    val nextPage: String? = null, val message: String? = null,
)
internal class GoogleDriveViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(DriveBrowserState())
    val state = _state.asStateFlow()
    private var pendingOwner: GoogleDriveSession.Owner? = null
    private var connectedOwner: GoogleDriveSession.Owner? = null
    private var loadJob: Job? = null
    private val auth = FirebaseAuth.getInstance()
    private val listener = FirebaseAuth.AuthStateListener {
        if ((connectedOwner ?: pendingOwner)?.let { it != GoogleDriveSession.owner() } == true) disconnect()
    }
    init { auth.addAuthStateListener(listener) }
    fun connect(activity: Activity, consent: (PendingIntent) -> Unit) {
        val account = GoogleDriveSession.owner() ?: run { _state.value = DriveBrowserState(message = "Sign in to Hi Player with Google first."); return }
        if (_state.value.busy) return
        pendingOwner = account; _state.value = _state.value.copy(busy = true, message = null)
        viewModelScope.launch {
            try {
                val result = Identity.getAuthorizationClient(activity).authorize(GoogleDriveSession.request(account)).await()
                if (result.hasResolution()) consent(checkNotNull(result.pendingIntent)) else accept(result, account)
            } catch (c: CancellationException) { throw c
            } catch (_: Exception) { GoogleDriveSession.clear(); _state.value = _state.value.copy(busy = false, message = "Drive could not connect. Check Google Play services, Drive API setup, and OAuth permissions.") }
        }
    }
    fun authorizationResult(resultCode: Int, intent: Intent?) {
        val account = pendingOwner ?: return
        if (resultCode != Activity.RESULT_OK || intent == null) {
            _state.value = _state.value.copy(busy = false, message = "Drive permission was not granted. Your Google sign-in is unchanged."); return
        }
        viewModelScope.launch {
            try { accept(Identity.getAuthorizationClient(getApplication<Application>()).getAuthorizationResultFromIntent(intent), account)
            } catch (c: CancellationException) { throw c
            } catch (_: Exception) { GoogleDriveSession.clear(); _state.value = DriveBrowserState(message = "Drive access was not completed for the signed-in account. Please reconnect.") }
        }
    }
    private suspend fun accept(result: AuthorizationResult, account: GoogleDriveSession.Owner) {
        GoogleDriveSession.accept(result, account)
        withContext(Dispatchers.IO) { GoogleDriveApi.verifyAccount(getApplication(), account) }
        GoogleDriveSession.requireOwner(account)
        connectedOwner = account; pendingOwner = null; _state.value = DriveBrowserState(connected = true); load()
    }
    fun search(query: String) { _state.value = _state.value.copy(query = query, nextPage = null); load() }
    fun open(folder: DriveMediaFile) {
        if (!folder.isFolder) return
        _state.value = _state.value.copy(path = _state.value.path + DriveFolder(folder.id, folder.name), query = "", nextPage = null); load()
    }
    fun back() {
        val old = _state.value
        if (old.query.isNotBlank()) search("")
        else if (old.path.size > 1) { _state.value = old.copy(path = old.path.dropLast(1), nextPage = null); load() }
    }
    fun load(more: Boolean = false) {
        val account = connectedOwner ?: return
        val previous = _state.value
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = previous.copy(busy = true, message = null)
            try {
                val page = withContext(Dispatchers.IO) { GoogleDriveApi.list(getApplication(), account, previous.path.last().id, previous.query, if (more) previous.nextPage else null) }
                GoogleDriveSession.requireOwner(account)
                _state.value = _state.value.copy(busy = false, files = (if (more) previous.files + page.files else page.files).distinctBy { it.id }, nextPage = page.nextPage)
            } catch (c: CancellationException) { throw c
            } catch (e: java.io.IOException) { _state.value = _state.value.copy(busy = false, message = e.message ?: "Drive is unavailable. Retry or reconnect.")
            } catch (_: Exception) { _state.value = _state.value.copy(busy = false, message = "Could not read Drive files. Retry or reconnect.") }
        }
    }
    fun preparePlayback(file: DriveMediaFile, ready: (DriveMediaFile) -> Unit) {
        val account = connectedOwner ?: return
        if (!file.isPlayable || !file.canDownload || _state.value.busy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, message = null)
            try {
                withContext(Dispatchers.IO) { GoogleDriveSession.token(getApplication(), account) }
                GoogleDriveSession.requireOwner(account); _state.value = _state.value.copy(busy = false); ready(file)
            } catch (c: CancellationException) { throw c
            } catch (_: Exception) { _state.value = _state.value.copy(busy = false, message = "Drive permission needs attention. Reconnect Drive, then retry playback.") }
        }
    }
    fun disconnect() {
        // Local disconnect only; revoking Google access would also revoke other scopes.
        loadJob?.cancel(); pendingOwner = null; connectedOwner = null; GoogleDriveSession.clear(); _state.value = DriveBrowserState()
    }
    override fun onCleared() { auth.removeAuthStateListener(listener); loadJob?.cancel(); super.onCleared() }
}
