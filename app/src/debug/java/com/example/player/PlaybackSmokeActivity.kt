package com.example.player

import androidx.activity.ComponentActivity
import android.os.Bundle
import android.widget.FrameLayout

/** Debug-only UI host for actual native and Media3 playback smoke tests. */
class PlaybackSmokeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(FrameLayout(this))
    }
}
