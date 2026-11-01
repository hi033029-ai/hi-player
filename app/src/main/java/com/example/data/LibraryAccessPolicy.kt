package com.example.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat

/** OS grants, not cached rows or app-owned files, decide whether a library may be shown. */
internal object LibraryAccessPolicy {
    enum class Scope { NONE, SELECTED, FULL }
    fun scope(context: Context, audio: Boolean): Scope {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            if (audio) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_MEDIA_VIDEO
        } else Manifest.permission.READ_EXTERNAL_STORAGE
        return resolve(
            mediaGranted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED,
            allFiles = Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager(),
            selectedVideos = !audio && Build.VERSION.SDK_INT >= 34 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED,
            audio = audio,
        )
    }
    fun resolve(mediaGranted: Boolean, allFiles: Boolean, selectedVideos: Boolean, audio: Boolean): Scope = when {
        mediaGranted || allFiles -> Scope.FULL
        selectedVideos && !audio -> Scope.SELECTED
        else -> Scope.NONE
    }
    fun canRestoreCache(scope: Scope) = scope == Scope.FULL
}
