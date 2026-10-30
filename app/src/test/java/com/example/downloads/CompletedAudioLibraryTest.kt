package com.example.downloads

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CompletedAudioLibraryTest {
    class DownloadsProvider : ContentProvider() {
        var missing = false
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
            // Simulate SAF/Downloads URI with no Audio index row and no filesystem DATA access.
            if (projection?.contains(MediaStore.MediaColumns.DATA) == true) throw IllegalArgumentException("No filesystem path")
            return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
                if (!missing) addRow(arrayOf("downloaded song.mp3", 4096L))
            }
        }
        override fun getType(uri: Uri) = "audio/mpeg"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }
    private fun entry() = BrowserDownload(1000, "downloaded song.mp3", "https://example.test/audio", "audio/mp3",
        downloadedBytes = 4096, status = BrowserDownloadStatus.COMPLETED,
        savedUri = "content://downloaded.audio.test/document/123", durationMs = 43000)

    @Test fun completedMp3IsPlayableWithoutAudioIndexOrFilesystemPermission() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val provider = DownloadsProvider()
        provider.attachInfo(context, android.content.pm.ProviderInfo().apply { authority = "downloaded.audio.test" })
        ShadowContentResolver.registerProviderInternal("downloaded.audio.test", provider)
        val track = CompletedAudioLibrary.read(context, entry())
        assertNotNull(track)
        assertEquals("downloaded song", track!!.title)
        assertEquals("audio/mpeg", track.mimeType)
        assertEquals(43000L, track.durationMs)
        assertEquals(entry().savedUri, track.uri.toString())
        assertEquals(entry().savedUri, track.path)
    }
    @Test fun deletedCompletedDownloadIsNotReintroducedFromJournal() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val provider = DownloadsProvider().apply { missing = true }
        provider.attachInfo(context, android.content.pm.ProviderInfo().apply { authority = "downloaded.audio.test" })
        ShadowContentResolver.registerProviderInternal("downloaded.audio.test", provider)
        assertNull(CompletedAudioLibrary.read(context, entry()))
    }
    @Test fun partialFailedCancelledAndMissingUrisNeverEnterMusic() {
        listOf(BrowserDownloadStatus.RUNNING, BrowserDownloadStatus.PAUSED, BrowserDownloadStatus.FAILED, BrowserDownloadStatus.CANCELLED)
            .forEach { assertFalse(entry().copy(status = it).isCompletedAudio) }
        assertFalse(entry().copy(savedUri = null).isCompletedAudio)
    }
    @Test fun legacyMimeAliasesAndGenericMp3ExtensionAreRecognized() {
        assertTrue(entry().isCompletedAudio)
        assertTrue(entry().copy(mimeType = "application/octet-stream").isCompletedAudio)
        assertEquals("audio/mpeg", DownloadFileResolver.resolve("audio.bin", "", "audio/x-mp3").mimeType)
    }
    @Test fun videoAndHtmlErrorDownloadsDoNotBecomeSongs() {
        assertFalse(entry().copy(mimeType = "video/mp4", fileName = "movie.mp4").isCompletedAudio)
        assertFalse(entry().copy(mimeType = "text/html").isCompletedAudio)
    }
}
