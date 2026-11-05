package com.example.auth

import android.app.Activity
import android.app.Application
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

internal data class GoogleAccountProfile(val uid: String, val name: String, val email: String, val photoUrl: String?)

/** Google issues the credential; Firebase validates it before the UI trusts a profile.
 * Firebase owns persistence/refresh. No password, raw ID token or refresh token is
 * copied into app preferences, browser cookies, logs, URLs, or stream headers. */
internal class GoogleAccountViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = runCatching {
        FirebaseApp.initializeApp(application)
        FirebaseAuth.getInstance()
    }.getOrNull()
    private val credentials = CredentialManager.create(application)
    private val _profile = MutableStateFlow(auth?.currentUser.toProfile())
    val profile = _profile.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    val configured: Boolean get() = auth != null && GoogleSignInConfiguration.clientId(getApplication()).isNotBlank()
    private val listener = FirebaseAuth.AuthStateListener {
        val updated = it.currentUser.toProfile()
        if (_profile.value?.uid != updated?.uid) com.example.drive.GoogleDriveSession.clear()
        _profile.value = updated
    }
    init { auth?.addAuthStateListener(listener) }

    fun signIn(activity: Activity) {
        if (_busy.value) return
        if (!configured) {
            _message.value = "Google sign-in setup is missing. Enable Google in Firebase Authentication and configure the app's OAuth clients."
            return
        }
        _busy.value = true
        _message.value = null
        viewModelScope.launch {
            try {
                val option = GetSignInWithGoogleOption.Builder(GoogleSignInConfiguration.clientId(activity)).build()
                val response = credentials.getCredential(activity, GetCredentialRequest.Builder().addCredentialOption(option).build())
                val custom = response.credential as? CustomCredential
                    ?: error("Unexpected credential type")
                require(custom.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
                val google = GoogleIdTokenCredential.createFrom(custom.data)
                // This backend exchange validates signature, issuer, audience and expiry.
                // Parsing a JWT or choosing an Android account alone is NOT sign-in.
                val result = checkNotNull(auth).signInWithCredential(GoogleAuthProvider.getCredential(google.idToken, null)).await()
                _profile.value = checkNotNull(result.user.toProfile())
            } catch (_: GetCredentialCancellationException) {
                // Account chooser cancellation is not a failure and changes no account.
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: NoCredentialException) {
                _message.value = "No Google account is available. Add an account in Android settings and check Google Play services."
            } catch (_: FirebaseNetworkException) {
                _message.value = "Google sign-in needs an internet connection. Please try again."
            } catch (_: FirebaseAuthException) {
                _message.value = "Google could not complete sign-in. Check the enabled Google provider, Web client ID, Android package and signing fingerprints."
            } catch (_: Exception) {
                _message.value = "Google sign-in couldn't finish. Check Google Play services and the app's OAuth setup, then retry."
            } finally { _busy.value = false }
        }
    }

    fun signOut() {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                com.example.drive.GoogleDriveSession.clear()
                auth?.signOut()
                _profile.value = null
                _message.value = null
                credentials.clearCredentialState(ClearCredentialStateRequest())
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                _message.value = "Signed out of Hi Player. The credential provider could not clear its account selection; website sessions are separate."
            } finally { _busy.value = false }
        }
    }
    override fun onCleared() { auth?.removeAuthStateListener(listener); super.onCleared() }
}

private fun FirebaseUser?.toProfile(): GoogleAccountProfile? {
    if (this == null || providerData.none { it.providerId == GoogleAuthProvider.PROVIDER_ID }) return null
    return GoogleAccountProfile(uid, displayName.orEmpty(), email.orEmpty(), photoUrl?.toString()?.takeIf { it.startsWith("https://") })
}
