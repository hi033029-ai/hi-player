package com.example.drive

import android.accounts.Account
import android.content.Context
import android.os.SystemClock
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Memory-only access bound to verified Firebase Google identity. Never reuse Firebase ID tokens for Drive. */
internal object GoogleDriveSession {
    data class Owner(val uid: String, val email: String)
    private data class Grant(val owner: Owner, val token: String, val expiresAt: Long)
    @Volatile private var grant: Grant? = null
    fun owner(): Owner? = runCatching {
        val user = FirebaseAuth.getInstance().currentUser ?: return null
        val google = user.providerData.firstOrNull { it.providerId == GoogleAuthProvider.PROVIDER_ID } ?: return null
        google.email?.takeIf(String::isNotBlank)?.let { Owner(user.uid, it) }
    }.getOrNull()
    fun request(owner: Owner) = AuthorizationRequest.builder().setAccount(Account(owner.email, "com.google"))
        .setRequestedScopes(listOf(Scope(GoogleDrivePolicy.READ_SCOPE))).build()
    fun clear() { grant = null }
    fun accept(result: AuthorizationResult, expected: Owner) {
        check(owner() == expected) { "Google account changed. Reopen Drive." }
        check(result.grantedScopes.contains(GoogleDrivePolicy.READ_SCOPE)) { "Read-only Drive permission was not granted." }
        val token = result.accessToken?.takeIf(String::isNotBlank) ?: error("Google did not return Drive access.")
        grant = Grant(expected, token, SystemClock.elapsedRealtime() + TimeUnit.MINUTES.toMillis(45))
    }
    fun requireOwner(expected: Owner) {
        if (owner() != expected) { clear(); throw IOException("Google account changed. Reopen Drive from your account.") }
    }
    /** IO/player loading thread only. Consent UI is never launched during playback. */
    fun token(context: Context, expected: Owner, force: Boolean = false): String {
        if (!com.example.BuildConfig.GOOGLE_DRIVE_ENABLED) throw IOException("Drive permission setup is pending.")
        requireOwner(expected)
        val current = grant
        if (!force && current?.owner == expected && current.expiresAt > SystemClock.elapsedRealtime()) return current.token
        val result = try { Tasks.await(Identity.getAuthorizationClient(context.applicationContext).authorize(request(expected)), 30, TimeUnit.SECONDS)
        } catch (_: Exception) { throw IOException("Drive access expired or is unavailable. Reconnect Drive from your Google account.") }
        if (result.hasResolution()) throw IOException("Drive permission is required. Open Drive from your Google account.")
        accept(result, expected)
        return checkNotNull(grant).token
    }
}
