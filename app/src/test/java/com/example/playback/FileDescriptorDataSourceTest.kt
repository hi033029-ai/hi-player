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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FileDescriptorDataSourceTest {

    @Test
    fun `completed partial alias can reopen the published file after private copy is removed`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val partial = File(context.cacheDir, "deleted-${System.nanoTime()}.mp4.part")
        val completed = File.createTempFile("published-", ".mp4", context.cacheDir).apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        DownloadingFilesRegistry.completeOwned(partial.absolutePath, Uri.fromFile(completed).toString())
        val source = FileDescriptorDataSource(context, waitForGrowth = true)
        try {
            assertEquals(4L, source.open(DataSpec.Builder().setUri(Uri.fromFile(partial)).build()))
            val bytes = ByteArray(4)
            assertEquals(4, source.read(bytes, 0, 4))
            assertArrayEquals(byteArrayOf(1, 2, 3, 4), bytes)
        } finally { source.close(); completed.delete() }
    }

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

    @Test
    fun `cancelled source exits while waiting for an unwritten extractor position`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File.createTempFile("growing-cancel", ".mkv", context.cacheDir)
        file.writeBytes(byteArrayOf(1, 2, 3, 4))
        DownloadingFilesRegistry.markDownloading(file.absolutePath, file.length())

        val source = FileDescriptorDataSource(context, waitForGrowth = true)
        val finished = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>(null)
        val opener = thread(start = true) {
            try {
                source.open(
                    DataSpec.Builder()
                        .setUri(Uri.fromFile(file))
                        .setPosition(16L)
                        .build()
                )
            } catch (error: Throwable) {
                failure.set(error)
            } finally {
                finished.countDown()
            }
        }

        try {
            Thread.sleep(250L)
            source.close()
            assertTrue("open should stop promptly after cancellation", finished.await(2, TimeUnit.SECONDS))
            assertTrue(failure.get() is IOException)
            opener.join(1_000L)
        } finally {
            source.close()
            DownloadingFilesRegistry.markComplete(file.absolutePath)
            file.delete()
        }
    }

    @Test
    fun `far future metadata seek returns eof without failing source open`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File.createTempFile("growing-far-seek", ".mkv", context.cacheDir)
        file.writeBytes(byteArrayOf(1, 2, 3, 4))
        DownloadingFilesRegistry.markDownloading(file.absolutePath, file.length())

        val source = FileDescriptorDataSource(context, waitForGrowth = true)
        try {
            val length = source.open(
                DataSpec.Builder()
                    .setUri(Uri.fromFile(file))
                    .setPosition(64L * 1024L * 1024L)
                    .build()
            )
            assertEquals(C.LENGTH_UNSET.toLong(), length)
            assertEquals(C.RESULT_END_OF_INPUT, source.read(ByteArray(1), 0, 1))
        } finally {
            source.close()
            DownloadingFilesRegistry.markComplete(file.absolutePath)
            file.delete()
        }
    }
}
