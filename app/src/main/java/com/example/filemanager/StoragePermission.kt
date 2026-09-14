package com.example.filemanager

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings

/**
 * MANAGE_EXTERNAL_STORAGE (Android 11+) can't be granted through the normal runtime
 * permission dialog — it requires sending the user to a dedicated system settings
 * screen, where they flip it on manually. This wraps that flow.
 */
object StoragePermission {

    fun isGranted(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true // pre-R uses the normal runtime READ/WRITE_EXTERNAL_STORAGE permissions instead
        }

    /** Launch this via an Activity Result launcher; check isGranted() again onResume. */
    fun buildRequestIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
    }
}
