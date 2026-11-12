package com.example.player

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource

/** Short, honest player error copy. Raw exception text and file paths are never shown. */
internal object PlaybackErrorCopy {
    fun messageFor(errorCode: Int, cause: Throwable? = null): String {
        if (cause is HttpDataSource.InvalidResponseCodeException) {
            return when (cause.responseCode) {
                401, 403 -> "This video needs website access. Open it in the browser and try again."
                404, 410 -> "This video is no longer available at its address."
                else -> "The video server did not respond correctly. Try again later."
            }
        }
        return when (errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                "Couldn't reach the video. Check your connection and try again."
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
                "This file was moved or deleted."
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION ->
                "Hi Player can't read this file. Open it again from its folder."
            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE ->
                "This file is incomplete. If it is still downloading, try again shortly."
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ->
                "This file is damaged or incomplete, or its format isn't supported."
            PlaybackException.ERROR_CODE_DRM_UNSPECIFIED,
            PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED,
            PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR,
            PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED,
            PlaybackException.ERROR_CODE_DRM_DISALLOWED_OPERATION,
            PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR,
            PlaybackException.ERROR_CODE_DRM_DEVICE_REVOKED,
            PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED ->
                "This video is protected and can only play on its website."
            else -> "This video couldn't be played. Try again, or open it from its folder or website."
        }
    }
}
