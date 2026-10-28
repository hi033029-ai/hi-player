package com.example.data

import android.content.Context
import android.net.Uri
import android.app.job.JobScheduler
import androidx.test.core.app.ApplicationProvider
import com.example.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaLibraryCacheTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Test fun completedVideoSurvivesRestartButPrivatePartialAndRemoteDoNot() {
        val video = VideoItem(id = 55, uri = Uri.parse("content://media/external/video/media/55"), title = "movie.mkv", path = "/storage/emulated/0/Download/movie.mkv", width = 3840, height = 2160, durationMs = 90000, sizeBytes = 123456, isHdr = true, hdrFormat = "HDR10", frameRate = 24f)
        MediaLibraryCache.saveVideos(context, listOf(video, video.copy(id = 56, isPartialDownload = true), video.copy(id = 57, uri = Uri.parse("https://example.test/demo.mp4"))))
        assertEquals(listOf(video), MediaLibraryCache.readVideos(context))
    }
    @Test fun audioMetadataAndAlbumArtRoundTripWithoutNetworkSamples() {
        val audio = AudioItem(id = 12, uri = Uri.parse("content://media/external/audio/media/12"), title = "song", artist = "Artist", album = "Album", albumId = 9, albumArtUri = Uri.parse("content://media/external/audio/albumart/9"), durationMs = 50000, path = "/storage/emulated/0/Music/song.mp3")
        MediaLibraryCache.saveAudio(context, listOf(audio, audio.copy(uri = Uri.parse("https://example.test/sample.mp3"))))
        assertEquals(listOf(audio), MediaLibraryCache.readAudio(context))
    }
    @Test fun validEmptyIsDistinctFromCorruptCache() {
        MediaLibraryCache.saveVideos(context, emptyList())
        assertEquals(emptyList<VideoItem>(), MediaLibraryCache.readVideos(context))
        File(context.noBackupFilesDir, "library_videos.json").writeText("broken")
        assertNull(MediaLibraryCache.readVideos(context))
    }
    @Test fun backgroundScanIsPersistedAndContentTriggersIncludeBothLibraries() {
        MediaLibraryScanService.schedule(context)
        val scheduler = context.getSystemService(JobScheduler::class.java)
        val periodic = scheduler.getPendingJob(MediaLibraryScanService.PERIODIC)!!
        assertTrue(periodic.isPersisted)
        assertTrue(periodic.intervalMillis >= 15 * 60_000)
        val changes = scheduler.getPendingJob(MediaLibraryScanService.CHANGES)!!
        assertEquals(2, changes.triggerContentUris!!.size)
        MediaLibraryScanService.schedule(context)
        assertEquals(2, scheduler.allPendingJobs.size)
    }
}
