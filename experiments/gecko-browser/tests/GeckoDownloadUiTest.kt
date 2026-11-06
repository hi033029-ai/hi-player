package com.example.browser.gecko

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ui.theme.HiPlayerTheme
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executor
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
class GeckoDownloadUiTest {
    @get:Rule val compose = createComposeRule()
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
        compose.runOnIdle {
            controller.offer(object : GeckoDownloadSource {
                override val mimeType = "text/plain"
                override val contentDisposition = "attachment; filename=\"report.txt\""
                override val contentLength = 3L
                override val statusCode = 200
                override fun takeBody() = ByteArrayInputStream(byteArrayOf(1, 2, 3))
                override fun close() = Unit
            })
        }
        compose.onNodeWithText("Download file?").assertIsDisplayed()
        compose.onNodeWithText("Download").performClick()
        compose.onNodeWithText("Download complete").assertIsDisplayed()
        compose.runOnIdle { assertTrue(committed) }
        compose.onNodeWithText("OK").performClick()
        compose.runOnIdle { controller.close() }
    }
}
