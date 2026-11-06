package com.example.browser.gecko

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.ui.theme.HiPlayerTheme
import java.io.ByteArrayInputStream
import java.util.concurrent.Executor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h640dp")
class GeckoLegacyDownloadUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun legacyAndroidRequiresAChosenFolder() {
        val controller = GeckoDownloadController(Executor { it.run() }, { it() }) { _, _ -> error("must choose folder") }
        compose.setContent { HiPlayerTheme { GeckoDownloadUi(controller) } }
        compose.runOnIdle { controller.offer(object : GeckoDownloadSource {
            override val mimeType = "text/plain"
            override val contentDisposition = "attachment; filename=\"report.txt\""
            override val contentLength = 1L
            override val statusCode = 200
            override fun takeBody() = ByteArrayInputStream(byteArrayOf(1))
            override fun close() = Unit
        }) }
        compose.onNodeWithText("Download").assertIsNotEnabled()
        compose.runOnIdle { controller.close() }
    }
}
