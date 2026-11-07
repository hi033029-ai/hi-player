package com.example.browser.gecko

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.ui.screens.HiBrowserScreen
import com.example.ui.theme.HiPlayerTheme

/** No Gecko types are referenced here: this exercises the actual API 24-25 route. */
class GeckoFallbackTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HiPlayerTheme { HiBrowserScreen(onBack = { finish() }, onPlayDirectMedia = {}, externalUrl = intent.getStringExtra("url")) } }
    }
}
