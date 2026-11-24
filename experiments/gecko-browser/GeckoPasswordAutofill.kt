package com.example.browser.gecko

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.example.auth.SecureWebsitePolicy

/** Stores only the opt-in flag. The Android autofill provider owns all credentials. */
internal object GeckoPasswordAutofill {
    const val ENABLED = "enabled"

    fun available(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    fun preferences(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences("browser_password_autofill", Context.MODE_PRIVATE)

    fun shouldEnable(optedIn: Boolean, tab: GeckoTabSnapshot): Boolean =
        optedIn && !tab.privateTab && !tab.loadFailed &&
            SecureWebsitePolicy.originKey(tab.url) != null

    fun chooseProvider(context: Context): Boolean = runCatching {
        context.startActivity(Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
            .setData(Uri.fromParts("package", context.packageName, null)))
    }.isSuccess
}
