package com.example.player

import android.os.Bundle
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.example.model.VideoItem
import com.example.ui.screens.PlayerScreen
import com.example.ui.theme.HiPlayerTheme
import com.example.viewmodel.PlayerViewModel
import java.io.File

/** Debug-only host of the complete production screen, including controls and SurfaceView. */
class PlaybackGestureActivity : ComponentActivity() {
    lateinit var model: PlayerViewModel
        private set
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getSharedPreferences("first_use_tips",MODE_PRIVATE).edit().putBoolean("video_player_visual_guide_v2",true).commit()
        model = ViewModelProvider(this)[PlayerViewModel::class.java]
        setContent { HiPlayerTheme { PlayerScreen(model,onBack = { finish() },onEnterPip = {}) } }
        val file = File(requireNotNull(intent.getStringExtra("fixture")))
        model.playVideo(VideoItem(uri = Uri.fromFile(file), title = "Playback gesture fixture",
            mimeType = if (file.extension == "mkv") "video/x-matroska" else "video/mp4"),startPositionOverride = 0)
    }
}
