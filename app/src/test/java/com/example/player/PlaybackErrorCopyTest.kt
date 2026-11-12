package com.example.player

import androidx.media3.common.PlaybackException
import org.junit.Assert.*
import org.junit.Test

class PlaybackErrorCopyTest {
    @Test fun copyDoesNotExposeInternalErrorTextOrPaths() {
        val secret = "HTTP 403 /private/secret.mp4"
        val message = PlaybackErrorCopy.messageFor(PlaybackException.ERROR_CODE_UNSPECIFIED, IllegalStateException(secret))
        assertFalse(message.contains(secret))
        assertFalse(message.contains("HTTP"))
        assertTrue(message.isNotBlank())
    }
    @Test fun missingFileHasActionableCopy() {
        assertTrue(PlaybackErrorCopy.messageFor(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND).contains("moved or deleted"))
    }
}
