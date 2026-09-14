package com.example.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import java.util.concurrent.atomic.AtomicBoolean

/** Single guarded owner of the HDR window mode and HDR grading effects. */
@UnstableApi
class HdrColorModeManager(private val activity: Activity) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val isApplying = AtomicBoolean(false)
    private var appliedHdr = false
    private var attachedPlayer: ExoPlayer? = null

    var isHdrActive by mutableStateOf(false)
        private set
    var isSwitching by mutableStateOf(false)
        private set

    fun attach(player: ExoPlayer) {
        attachedPlayer?.removeListener(listener)
        attachedPlayer = player
        player.addListener(listener)
        applyFromTracks(player.currentTracks)
    }

    private val listener = object : Player.Listener {
        override fun onTracksChanged(tracks: Tracks) {
            val isHdr = tracks.groups.any { group ->
                (0 until group.length).any { index ->
                    group.isTrackSelected(index) &&
                        isHdrColorInfo(group.getTrackFormat(index).colorInfo)
                }
            }
            requestColorMode(isHdr)
        }
    }

    private fun applyFromTracks(tracks: Tracks) {
        val isHdr = tracks.groups.any { group ->
            (0 until group.length).any { index ->
                group.isTrackSelected(index) &&
                    isHdrColorInfo(group.getTrackFormat(index).colorInfo)
            }
        }
        requestColorMode(isHdr)
    }

    private fun isHdrColorInfo(colorInfo: ColorInfo?): Boolean = colorInfo != null && (
        colorInfo.colorTransfer == C.COLOR_TRANSFER_ST2084 ||
            colorInfo.colorTransfer == C.COLOR_TRANSFER_HLG
        )

    fun requestColorMode(hdrDesired: Boolean) {
        if (hdrDesired == appliedHdr || !isApplying.compareAndSet(false, true)) return
        isSwitching = true
        mainHandler.post {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    activity.window.colorMode = if (hdrDesired) {
                        ActivityInfo.COLOR_MODE_HDR
                    } else {
                        ActivityInfo.COLOR_MODE_DEFAULT
                    }
                }
                // Media3 grading is owned by HiPlayerEngine.setHdrEnhanceActive.
                // Keeping it out of the window-mode manager prevents track
                // callbacks from overwriting the user's toggle immediately.
                appliedHdr = hdrDesired
                isHdrActive = hdrDesired
            } finally {
                isSwitching = false
                isApplying.set(false)
            }
        }
    }

    fun release() {
        attachedPlayer?.removeListener(listener)
        attachedPlayer = null
        mainHandler.removeCallbacksAndMessages(null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.window.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
        }
        appliedHdr = false
        isHdrActive = false
        isSwitching = false
        isApplying.set(false)
    }
}

fun Context.findActivity(): Activity {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    throw IllegalStateException("Context is not an Activity and has no Activity in its chain")
}
