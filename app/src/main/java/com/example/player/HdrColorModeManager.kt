package com.example.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Single guarded owner of the Activity HDR window mode for one player instance.
 * Native HDR output follows the selected track; it is not gated by whether the
 * user enabled optional color grading.
 *
 * GPU grading is intentionally not installed here. Some decoder/surface
 * combinations reject Media3 video effects and leave audio playing over a black
 * surface or crash the renderer. Native HDR output remains enabled through
 * window.colorMode without changing the decoder pipeline.
 */
@UnstableApi
class HdrColorModeManager(private val activity: Activity) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val isApplying = AtomicBoolean(false)
    private var appliedHdr = false
    @Volatile private var latestHdrRequest = false
    private var attachedPlayer: ExoPlayer? = null
    private var trackListener: androidx.media3.common.Player.Listener? = null

    var isHdrActive by mutableStateOf(false)
        private set
    var isSwitching by mutableStateOf(false)
        private set

    /** Attach once when the player is created/rebuilt. */
    fun attach(player: ExoPlayer) {
        if (attachedPlayer === player) {
            evaluateAndRequest(player.currentTracks)
            return
        }
        attachedPlayer?.let { old -> trackListener?.let(old::removeListener) }
        attachedPlayer = player
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                evaluateAndRequest(tracks)
            }
        }
        trackListener = listener
        player.addListener(listener)
        // onTracksChanged only fires on FUTURE changes — if this player already selected
        // its tracks before attach() ran (e.g. media prepared before the UI composed),
        // the listener above never fires again and the HDR indicator/animation would
        // stay stuck off forever. Check what's already selected right now too.
        evaluateAndRequest(player.currentTracks)
    }

    private fun evaluateAndRequest(tracks: Tracks) {
        val isHdr = tracks.groups.any { group ->
            (0 until group.length).any { i ->
                group.isTrackSelected(i) &&
                    group.getTrackFormat(i).colorInfo?.let(::isHdrColorInfo) == true
            }
        }
        requestColorMode(isHdr)
    }

    private fun isHdrColorInfo(colorInfo: ColorInfo): Boolean {
        return colorInfo.colorTransfer == C.COLOR_TRANSFER_ST2084 ||
            colorInfo.colorTransfer == C.COLOR_TRANSFER_HLG
    }

    fun requestColorMode(hdrDesired: Boolean) {
        latestHdrRequest = hdrDesired
        if (isApplying.get() || hdrDesired == appliedHdr) return
        if (!isApplying.compareAndSet(false, true)) return

        isSwitching = true
        mainHandler.post {
            try {
                var desired = latestHdrRequest
                while (true) {
                    if (desired != appliedHdr) {
                        applyColorModeInternal(desired)
                        appliedHdr = desired
                        isHdrActive = desired
                    }
                    val latest = latestHdrRequest
                    if (latest == desired) break
                    desired = latest
                }
            } finally {
                isSwitching = false
                isApplying.set(false)
                if (latestHdrRequest != appliedHdr) requestColorMode(latestHdrRequest)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun applyColorModeInternal(isHdr: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        activity.window.colorMode = if (isHdr) {
            ActivityInfo.COLOR_MODE_HDR
        } else {
            ActivityInfo.COLOR_MODE_DEFAULT
        }
    }

    fun release() {
        attachedPlayer?.let { player -> trackListener?.let(player::removeListener) }
        mainHandler.removeCallbacksAndMessages(null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.window.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
        }
        attachedPlayer = null
        trackListener = null
        appliedHdr = false
        latestHdrRequest = false
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
