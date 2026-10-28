package com.example.data

import android.content.Context
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioMediaScannerTest {
    class AudioProvider : ContentProvider() {
        var deny = false
        var selectionSeen: String? = "not-called"
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
            if (deny) throw SecurityException("Audio permission denied")
            selectionSeen = selection
            // Typical downloaded audio with IS_MUSIC=0, missing duration and
            // optional artist/album/path fields must still be visible.
            return MatrixCursor(arrayOf("_id", "_display_name", "mime_type", "is_music")).apply { addRow(arrayOf(71L, "downloaded.opus", "audio/opus", 0)) }
        }
        override fun getType(uri: Uri) = "audio/opus"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }
    @Test fun downloadedAudioWithoutMusicFlagDurationOrOptionalColumnsIsIncluded() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val provider = AudioProvider()
        provider.attachInfo(context, android.content.pm.ProviderInfo().apply { authority = "media" })
        ShadowContentResolver.registerProviderInternal("media", provider)
        val result = AudioMediaScanner.scan(context)
        assertTrue(result.complete)
        assertNull(provider.selectionSeen)
        assertEquals("downloaded.opus", result.tracks.single().title)
        assertEquals(0L, result.tracks.single().durationMs)
        assertEquals("content://media/external/audio/media/71", result.tracks.single().uri.toString())
    }
    @Test fun deniedProviderIsIncompleteAndCannotAuthorizeClearingCachedMusic() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val provider = AudioProvider().apply { deny = true }
        provider.attachInfo(context, android.content.pm.ProviderInfo().apply { authority = "media" })
        ShadowContentResolver.registerProviderInternal("media", provider)
        assertFalse(AudioMediaScanner.scan(context).complete)
    }
}
