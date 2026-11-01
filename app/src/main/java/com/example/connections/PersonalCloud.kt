package com.example.connections

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.browser.customtabs.CustomTabsIntent
import com.example.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import android.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit

internal enum class CloudProvider(val label: String, val clientId: String, val authorize: String, val tokenEndpoint: String, val scopes: String) {
    ONEDRIVE("OneDrive", BuildConfig.ONEDRIVE_CLIENT_ID,
        "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize", "https://login.microsoftonline.com/consumers/oauth2/v2.0/token", "Files.Read User.Read"),
    DROPBOX("Dropbox", BuildConfig.DROPBOX_APP_KEY,
        "https://www.dropbox.com/oauth2/authorize", "https://api.dropboxapi.com/oauth2/token", "account_info.read files.metadata.read files.content.read");
    val key get() = name.lowercase(java.util.Locale.ROOT)
    val redirect get() = "com.aistudio.hiplayer.connections://oauth/$key"
}
internal data class CloudStatus(val sessionId: String? = null, val account: String = "", val busy: Boolean = false, val message: String? = null)
internal class CloudSession(val id: String, val token: String, val owner: String, val expiresAt: Long)

/** Public-client PKCE. No passwords, refresh tokens, browser cookies or persisted authorization state. */
internal object PersonalCloud {
    private class Pending(val provider: CloudProvider, val state: String, val verifier: String, val started: Long)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val pending = mutableMapOf<CloudProvider, Pending>()
    private val jobs = mutableMapOf<CloudProvider, Job>()
    private val sessions = java.util.concurrent.ConcurrentHashMap<CloudProvider, CloudSession>()
    private val _status = MutableStateFlow(CloudProvider.entries.associateWith { CloudStatus() })
    val status = _status.asStateFlow()
    val http = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(12, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build()
    private fun update(p: CloudProvider, value: CloudStatus) { _status.value = _status.value + (p to value) }
    private fun random() = Base64.encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) }, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    fun connect(context: Context, provider: CloudProvider) {
        if (status.value.getValue(provider).busy) return
        if (provider.clientId.isBlank()) {
            update(provider, status.value.getValue(provider).copy(message = "${provider.label} setup required: the app developer must register its OAuth app and redirect address before sign-in is available.")); return
        }
        val request = Pending(provider, random(), random(), SystemClock.elapsedRealtime())
        pending[provider] = request
        update(provider, status.value.getValue(provider).copy(busy = true, message = "Complete read-only authorization in your browser, or return here and cancel."))
        val challenge = Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(request.verifier.toByteArray(Charsets.US_ASCII)), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        val url = Uri.parse(provider.authorize).buildUpon().appendQueryParameter("client_id", provider.clientId)
            .appendQueryParameter("response_type", "code").appendQueryParameter("redirect_uri", provider.redirect)
            .appendQueryParameter("scope", provider.scopes).appendQueryParameter("state", request.state)
            .appendQueryParameter("code_challenge_method", "S256").appendQueryParameter("code_challenge", challenge)
        if (provider == CloudProvider.DROPBOX) url.appendQueryParameter("token_access_type", "online")
        else url.appendQueryParameter("response_mode", "query").appendQueryParameter("prompt", "select_account")
        try { CustomTabsIntent.Builder().build().launchUrl(context, url.build()) }
        catch (_: Exception) { cancel(provider, "No supported browser is available. Install or enable one, then try again.") }
    }
    fun acceptRedirect(uri: Uri) {
        val provider = CloudProvider.entries.firstOrNull { uri.scheme == "com.aistudio.hiplayer.connections" && uri.host == "oauth" && uri.path == "/${it.key}" } ?: return
        if (uri.userInfo != null || uri.port != -1) return
        val request = pending[provider] ?: return
        if (SystemClock.elapsedRealtime() - request.started > 10 * 60_000 || uri.fragment != null ||
            uri.getQueryParameters("state").size != 1 || uri.getQueryParameter("state") != request.state) return
        // Consume once. Retain object identity to reject cancelled/superseded exchange completion.
        if (uri.getQueryParameter("error") != null || uri.getQueryParameters("code").size != 1) { cancel(provider, "Authorization was not completed. You can try again."); return }
        val code = uri.getQueryParameter("code")?.takeIf { it.isNotBlank() && it.length <= 8192 } ?: return
        if (jobs[provider]?.isActive == true) return
        jobs[provider] = scope.launch {
            try {
                val verified = withContext(Dispatchers.IO) {
                    val form = FormBody.Builder().add("grant_type", "authorization_code").add("client_id", provider.clientId)
                        .add("code", code).add("redirect_uri", provider.redirect).add("code_verifier", request.verifier).build()
                    val result = json(Request.Builder().url(provider.tokenEndpoint).post(form).build())
                    val token = result.getString("access_token"); require(token.isNotBlank() && result.optString("token_type", "Bearer").equals("Bearer", true))
                    val seconds = result.optLong("expires_in", 3600).coerceIn(1, 86_400)
                    val accountRequest = if (provider == CloudProvider.ONEDRIVE) Request.Builder().url("https://graph.microsoft.com/v1.0/me?\$select=id,displayName").get()
                        else Request.Builder().url("https://api.dropboxapi.com/2/users/get_current_account").post("null".toRequestBody("application/json".toMediaType()))
                    val account = json(accountRequest.header("Authorization", "Bearer $token").build())
                    val owner = if (provider == CloudProvider.ONEDRIVE) account.getString("id") else account.getString("account_id")
                    require(owner.isNotBlank())
                    val label = if (provider == CloudProvider.ONEDRIVE) account.optString("displayName").ifBlank { "Microsoft account" }
                        else account.optJSONObject("name")?.optString("display_name").orEmpty().ifBlank { "Dropbox account" }
                    Pair(CloudSession(UUID.randomUUID().toString(), token, owner, SystemClock.elapsedRealtime() + seconds * 1000), label)
                }
                ensureActive(); if (pending[provider] !== request) return@launch
                sessions[provider] = verified.first; pending.remove(provider)
                update(provider, CloudStatus(verified.first.id, verified.second))
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) {
                if (pending[provider] === request) cancel(provider, "Couldn't authorize ${provider.label}. Check app registration, permissions and your connection, then retry.")
            }
        }
    }
    fun cancel(provider: CloudProvider, message: String = "Sign-in cancelled. Any previously connected account is unchanged.") {
        pending.remove(provider); jobs.remove(provider)?.cancel()
        update(provider, status.value.getValue(provider).copy(busy = false, message = message))
    }
    fun disconnect(provider: CloudProvider) {
        cancel(provider); sessions.remove(provider); update(provider, CloudStatus(message = "Disconnected on this device. Provider website sessions and consent are unchanged."))
    }
    fun requireSession(provider: CloudProvider, id: String? = null): CloudSession {
        val session = sessions[provider] ?: error("Reconnect cloud storage")
        check((id == null || id == session.id) && SystemClock.elapsedRealtime() < session.expiresAt) { "Reconnect cloud storage" }
        return session
    }
    internal fun json(request: Request): JSONObject = http.newCall(request).execute().use { response ->
        check(response.isSuccessful) { "Cloud request unavailable" }
        val body = response.body ?: error("Empty cloud response")
        val out = java.io.ByteArrayOutputStream()
        body.byteStream().use { input -> val buffer = ByteArray(8192)
            while (true) { val count = input.read(buffer); if (count < 0) break
                check(out.size() + count <= 4 * 1024 * 1024); out.write(buffer, 0, count) } }
        JSONObject(out.toString("UTF-8"))
    }
}
