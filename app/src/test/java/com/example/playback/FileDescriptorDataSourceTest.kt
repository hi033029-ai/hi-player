package com.example.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.test.core.app.ApplicationProvider
import com.example.player.DownloadingFilesRegistry
import kotlin.concurrent.thread
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FileDescriptorDataSourceTest {

    @Test
    fun `waits at temporary eof then reads appended download bytes`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File.createTempFile("growing-source", ".mp4", context.cacheDir)
        val uri = Uri.fromFile(file)
        val initial = byteArrayOf(1, 2, 3, 4)
        val appended = byteArrayOf(5, 6, 7, 8)
        file.writeBytes(initial)
        DownloadingFilesRegistry.markDownloading(file.absolutePath, initial.size.toLong())

        val source = FileDescriptorDataSource(context, waitForGrowth = true)
        try {
            assertEquals(
                C.LENGTH_UNSET.toLong(),
                source.open(DataSpec.Builder().setUri(uri).build())
            )

            val received = ByteArray(initial.size + appended.size)
            assertEquals(initial.size, source.read(received, 0, initial.size))

            val writer = thread(start = true) {
                Thread.sleep(200L)
                FileOutputStream(file, true).use { it.write(appended) }
                DownloadingFilesRegistry.markDownloading(file.absolutePath, file.length())
            }

            assertEquals(appended.size, source.read(received, initial.size, appended.size))
            writer.join(2_000L)
            assertArrayEquals(initial + appended, received)
        } finally {
            source.close()
            DownloadingFilesRegistry.markComplete(file.absolutePath)
            file.delete()
        }
    }

    @Test
    fun `waits for an extractor position that has not been written yet`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File.createTempFile("growing-position", ".mkv", context.cacheDir)
        val uri = Uri.fromFile(file)
        file.writeBytes(byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte()))
        DownloadingFilesRegistry.markDownloading(file.absolutePath, file.length())

        val writer = thread(start = true) {
            Thread.sleep(200L)
            FileOutputStream(file, true).use { it.write(ByteArray(12) { 0 }) }
            DownloadingFilesRegistry.markDownloading(file.absolutePath, file.length())
        }
        val source = FileDescriptorDataSource(context, waitForGrowth = true)
        try {
            assertEquals(
                C.LENGTH_UNSET.toLong(),
                source.open(
                    DataSpec.Builder()
                        .setUri(uri)
                        .setPosition(16L)
                        .build()
                )
            )
            writer.join(2_000L)
        } finally {
            source.close()
            DownloadingFilesRegistry.markComplete(file.absolutePath)
            file.delete()
        }
    }
}
