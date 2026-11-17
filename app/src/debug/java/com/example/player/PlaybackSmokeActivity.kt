package com.example.player

import android.app.Activity
import android.os.Bundle
import android.widget.FrameLayout

/** Debug-only UI host for actual native and Media3 playback smoke tests. */
class PlaybackSmokeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(FrameLayout(this))
    }
}
