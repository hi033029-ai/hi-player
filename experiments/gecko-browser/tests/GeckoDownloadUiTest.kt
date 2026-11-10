package com.example.browser.gecko

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.ui.theme.HiPlayerTheme
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executor
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
class GeckoDownloadUiTest {
    @get:Rule val compose = createComposeRule()

    private fun source(onClose: () -> Unit = {}, onTake: () -> Unit = {}) = object : GeckoDownloadSource {
        override val mimeType = "text/plain"
        override val contentDisposition = "attachment; filename=\"report.txt\""
        override val contentLength = 3L
        override val statusCode = 200
        override fun takeBody(): ByteArrayInputStream { onTake(); return ByteArrayInputStream(byteArrayOf(1, 2, 3)) }
        override fun close() { onClose() }
    }

    @Test fun approvingAnOfferShowsItsCompletion() {
        var committed = false
        val controller = GeckoDownloadController(Executor { it.run() }, { it() }) { _, _ ->
            object : GeckoDownloadDestination {
                override fun open() = ByteArrayOutputStream()
                override fun commit() { committed = true }
                override fun abort() = Unit
            }
        }
        compose.setContent { HiPlayerTheme { GeckoDownloadUi(controller) } }
        compose.runOnIdle { controller.offer(source()) }
        compose.onNodeWithText("Download file?").assertIsDisplayed()
        compose.onNodeWithTag("gecko-download-confirm").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Download complete").assertIsDisplayed()
        compose.runOnIdle { assertTrue(committed) }
        compose.onNodeWithText("OK").performClick()
        compose.runOnIdle { assertEquals(GeckoDownloadState.Idle, controller.state); controller.close() }
    }

    @Test fun privateOfferExplainsFileRemainsWithoutDownloadHistory() {
        val controller = GeckoDownloadController(Executor { it.run() }, { it() }) { _, _ -> error("not approved") }
        compose.setContent { HiPlayerTheme { GeckoDownloadUi(controller) } }
        compose.runOnIdle { controller.offer(source(), recordInHistory = false) }
        compose.onNodeWithText("The file stays on your device, but will not appear in browser download history.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("gecko-download-cancel").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(GeckoDownloadState.Idle, controller.state); controller.close() }
    }

    @Test fun editedFileNameIsUsedByTheActualDownload() {
        var savedName: String? = null
        val controller = GeckoDownloadController(Executor { it.run() }, { it() }) { name, _ ->
            savedName = name
            object : GeckoDownloadDestination {
                override fun open() = ByteArrayOutputStream()
                override fun commit() = Unit
                override fun abort() = Unit
            }
        }
        compose.setContent { HiPlayerTheme { GeckoDownloadUi(controller) } }
        compose.runOnIdle { controller.offer(source()) }
        compose.onNodeWithTag("gecko-download-name").performTextReplacement("renamed.txt")
        compose.onNodeWithTag("gecko-download-confirm").performScrollTo().performClick()
        compose.onNodeWithText("Download complete").assertIsDisplayed()
        compose.runOnIdle { assertEquals("renamed.txt", savedName); controller.close() }
    }

    @Test fun blankFileNameCannotStartATransfer() {
        var bodyTaken = false
        val controller = GeckoDownloadController(Executor { it.run() }, { it() }) { _, _ -> error("not approved") }
        compose.setContent { HiPlayerTheme { GeckoDownloadUi(controller) } }
        compose.runOnIdle { controller.offer(source(onTake = { bodyTaken = true })) }
        compose.onNodeWithTag("gecko-download-name").performTextReplacement("")
        compose.onNodeWithTag("gecko-download-confirm").assertIsNotEnabled()
        compose.runOnIdle { assertFalse(bodyTaken); assertTrue(controller.state is GeckoDownloadState.Offer); controller.close() }
    }

    @Test fun cancellingAnOfferClosesItsStreamWithoutTakingItsBody() {
        var closed = false
        var bodyTaken = false
        val controller = GeckoDownloadController(Executor { it.run() }, { it() }) { _, _ -> error("not approved") }
        compose.setContent { HiPlayerTheme { GeckoDownloadUi(controller) } }
        compose.runOnIdle { controller.offer(source(onClose = { closed = true }, onTake = { bodyTaken = true })) }
        compose.onNodeWithTag("gecko-download-cancel").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(closed); assertFalse(bodyTaken); assertEquals(GeckoDownloadState.Idle, controller.state); controller.close() }
    }
}
