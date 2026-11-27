package com.example.browser.gecko

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.example.ui.screens.HiBrowserScreen
import com.example.ui.theme.HiPlayerTheme

/** Full app browser entry, not a bare GeckoView; used only by device validation. */
class GeckoChromeTestActivity : ComponentActivity() {
    @Volatile var playedUrl: String? = null
        private set
    internal val owner get() = ViewModelProvider(this)[GeckoBrowserOwner::class.java]
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HiPlayerTheme {
            HiBrowserScreen(onBack = { finish() },onPlayDirectMedia = { playedUrl = it })
        } }
    }
}
