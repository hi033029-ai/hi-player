package com.example.auth

import android.content.Context
import com.example.BuildConfig

internal object GoogleSignInConfiguration {
    fun clientId(context: Context): String {
        val configured = BuildConfig.GOOGLE_WEB_CLIENT_ID.trim()
        if (validClientId(configured)) return configured
        // Google Services generates this resource when OAuth is configured.
        val resource = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        val generated = if (resource != 0) context.getString(resource).trim() else ""
        return generated.takeIf(::validClientId).orEmpty()
    }
    fun validClientId(value: String): Boolean =
        value.endsWith(".apps.googleusercontent.com") && value.length > 35 &&
            value.none(Char::isWhitespace) && !value.contains("YOUR_", true) && !value.contains("REPLACE", true)
}
