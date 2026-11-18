package com.example.filemanager

import android.app.Activity
import android.app.Application
import android.content.ContentValues
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.example.model.FileItem
import com.example.model.FileType
import com.example.model.VideoItem
import com.example.player.PlaybackSmokeActivity
import com.example.viewmodel.FileManagerViewModel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RecycleBinDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as Application
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync { block() }
    private fun waitFor(message: String, ready: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + 15_000
        while (!ready() && SystemClock.elapsedRealtime() < until) Thread.sleep(50)
        assertTrue(message, ready())
    }
    private fun fileItem(file: File) = FileItem(file, file.name, file.path, false, file.length(), fileType = FileType.VIDEO)

    @Test fun cancelMoveRestoreCollisionAndEraseOnlyConfirmedFiles() {
        val root = File(app.cacheDir, "recycle-test-${System.nanoTime()}").canonicalFile.apply { mkdirs() }
        lateinit var model: FileManagerViewModel
        main { model = FileManagerViewModel(app, root) }
        try {
            val movie = File(root, "one/film.mp4").apply { parentFile!!.mkdirs(); writeText("first") }
            val other = File(root, "two/film.mp4").apply { parentFile!!.mkdirs(); writeText("second") }
            main { model.requestDelete(listOf(fileItem(movie))); model.dismissDeleteConfirm() }
            assertEquals("first", movie.readText())
            main { model.requestDelete(listOf(fileItem(movie))); model.confirmDelete() }
            waitFor("Selected movie did not move") { model.recycleBinItems.value.any { it.originalPath == movie.path } }
            assertFalse(movie.exists())
            assertEquals("second", other.readText())
            movie.writeText("replacement")
            val firstId = model.recycleBinItems.value.single { it.originalPath == movie.path }.id
            main { model.toggleRecycleBinSelection(firstId); model.restoreRecycleBinItems() }
            val restored = File(movie.parentFile, "film (1).mp4")
            waitFor("Restoration did not finish") { model.binAction.value == null && restored.exists() }
            assertEquals("first", restored.readText())
            assertEquals("replacement", movie.readText())
            main { model.requestDelete(listOf(fileItem(movie), fileItem(other))); model.confirmDelete() }
            waitFor("Batch move failed") { model.recycleBinItems.value.count { it.originalPath in setOf(movie.path, other.path) } == 2 }
            val onlyMovie = model.recycleBinItems.value.single { it.originalPath == movie.path }.id
            main { model.selectAllRecycleBin(); model.permanentlyDeleteRecycleBinItems(setOf(onlyMovie)) }
            waitFor("Confirmed item did not leave the bin") { model.recycleBinItems.value.none { it.id == onlyMovie } }
            assertTrue(model.recycleBinItems.value.any { it.originalPath == other.path })
            assertEquals("first", restored.readText())
        } finally {
            main { model.viewModelScope.cancel() }
            root.deleteRecursively()
        }
    }

    @SdkSuppress(minSdkVersion = 30)
    @Test fun androidConsentCanCancelMoveAndRestoreAUriOnlyVideo() {
        val root = File(app.cacheDir, "scoped-test-${System.nanoTime()}").canonicalFile.apply { mkdirs() }
        val name = "hi-trash-test-${System.nanoTime()}.mp4"
        val uri = requireNotNull(app.contentResolver.insert(
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/HiPlayerTests")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }))
        lateinit var model: FileManagerViewModel
        main { model = FileManagerViewModel(app, root) }
        try {
            app.assets.open("grading_reference.mp4").use { input ->
                app.contentResolver.openOutputStream(uri)!!.use { input.copyTo(it) }
            }
            app.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            ActivityScenario.launch<PlaybackSmokeActivity>(Intent(app, PlaybackSmokeActivity::class.java)).use { scenario ->
                scenario.onActivity { host ->
                    val launcher = host.activityResultRegistry.register("trash-fixture", ActivityResultContracts.StartIntentSenderForResult()) {
                        model.finishMediaTrashRequest(it.resultCode == Activity.RESULT_OK)
                    }
                    model.viewModelScope.launch {
                        model.mediaTrashRequests.collect { launcher.launch(IntentSenderRequest.Builder(it.intentSender).build()) }
                    }
                }
                val video = VideoItem(uri = uri, title = name)
                main { model.moveVideosToRecycleBin(listOf(video)) }
                answerAndroidPrompt(false)
                waitFor("Cancelled move remained busy") { model.deleteProgress.value == null }
                assertEquals(false, isTrashed(uri))
                main { model.moveVideosToRecycleBin(listOf(video)) }
                answerAndroidPrompt(true)
                waitFor("Approved system move missing from bin") { model.recycleBinItems.value.any { it.mediaUri == uri } }
                assertEquals(true, isTrashed(uri))
                main { model.toggleRecycleBinSelection(uri.toString()); model.restoreRecycleBinItems() }
                answerAndroidPrompt(true)
                waitFor("Approved restoration did not finish") { model.binAction.value == null && model.recycleBinItems.value.none { it.mediaUri == uri } }
                assertEquals(false, isTrashed(uri))
            }
        } finally {
            main { model.viewModelScope.cancel() }
            app.contentResolver.delete(uri, Bundle().apply { putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE) })
            root.deleteRecursively()
        }
    }

    private fun isTrashed(uri: android.net.Uri): Boolean? = app.contentResolver.query(uri,
        arrayOf(MediaStore.MediaColumns.IS_TRASHED), Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
        }, null)?.use { if (it.moveToFirst()) it.getInt(0) == 1 else null }

    private fun answerAndroidPrompt(allow: Boolean) {
        val automation = instrumentation.uiAutomation
        val info = automation.serviceInfo
        info.flags = info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        automation.serviceInfo = info
        val id = if (allow) "android:id/button1" else "android:id/button2"
        val until = SystemClock.elapsedRealtime() + 12_000
        do {
            val root = automation.rootInActiveWindow
            if (root?.packageName?.toString()?.contains("providers.media") == true) {
                val button = root.findAccessibilityNodeInfosByViewId(id).firstOrNull { it.isEnabled }
                if (button?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return
            }
            Thread.sleep(100)
        } while (SystemClock.elapsedRealtime() < until)
        throw AssertionError("Android media confirmation did not offer the expected action")
    }
}
