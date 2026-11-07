package com.example.ui.screens

import android.os.Build
import androidx.annotation.DoNotInline
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.browser.gecko.GeckoBrowserEntry

internal object GeckoEnginePolicy {
    fun supported(sdk: Int): Boolean = sdk >= 26
}

internal data class GeckoBrowserArguments(
    val onBack: () -> Unit,
    val appNavigation: @Composable () -> Unit,
    val onPlayDirectMedia: (String) -> Unit,
    val modifier: Modifier,
    val appTextSizeSp: Int,
    val onAppTextSizeChange: (Int) -> Unit,
    val onPlayAccountMedia: (com.example.model.VideoItem) -> Unit,
    val isInPictureInPicture: Boolean,
    val onEnterPictureInPicture: (() -> Boolean)?,
    val externalUrl: String?,
    val openDownloads: Boolean,
    val onDownloadsConsumed: () -> Unit,
    val onExternalUrlConsumed: () -> Unit,
    val onPlayBrowserVideo: ((String, String?, String?) -> Unit)?,
)

/** The real app entry point in the Gecko build, with an API 24-25 compatibility route. */
@Composable
fun HiBrowserScreen(
    onBack: () -> Unit,
    appNavigation: @Composable () -> Unit = {},
    onPlayDirectMedia: (String) -> Unit,
    modifier: Modifier = Modifier,
    appTextSizeSp: Int = 14,
    onAppTextSizeChange: (Int) -> Unit = {},
    onPlayAccountMedia: (com.example.model.VideoItem) -> Unit = {},
    isInPictureInPicture: Boolean = false,
    onEnterPictureInPicture: (() -> Boolean)? = null,
    externalUrl: String? = null,
    openDownloads: Boolean = false,
    onDownloadsConsumed: () -> Unit = {},
    onExternalUrlConsumed: () -> Unit = {},
    onPlayBrowserVideo: ((String, String?, String?) -> Unit)? = null,
) {
    var useWebView by rememberSaveable { mutableStateOf(!GeckoEnginePolicy.supported(Build.VERSION.SDK_INT)) }
    if (useWebView || !GeckoEnginePolicy.supported(Build.VERSION.SDK_INT)) {
        WebViewBrowserScreen(onBack, appNavigation, onPlayDirectMedia, modifier, appTextSizeSp,
            onAppTextSizeChange, onPlayAccountMedia, isInPictureInPicture, onEnterPictureInPicture,
            externalUrl, openDownloads, onDownloadsConsumed, onExternalUrlConsumed, onPlayBrowserVideo)
    } else {
        Api26Browser.Content(GeckoBrowserArguments(onBack, appNavigation, onPlayDirectMedia, modifier,
            appTextSizeSp, onAppTextSizeChange, onPlayAccountMedia, isInPictureInPicture,
            onEnterPictureInPicture, externalUrl, openDownloads, onDownloadsConsumed,
            onExternalUrlConsumed, onPlayBrowserVideo), onUseWebView = { useWebView = true })
    }
}

@RequiresApi(26)
private object Api26Browser {
    @DoNotInline @Composable
    fun Content(arguments: GeckoBrowserArguments, onUseWebView: () -> Unit) {
        GeckoBrowserEntry(arguments, onUseWebView)
    }
}
