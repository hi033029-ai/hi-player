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
 * Single guarded owner of everything HDR-related for one player instance:
 *  - window.colorMode (fixes the color-distortion / black-flash issues)
 *  - the exposure+saturation grading pass (fixes residual washed-out colors on
 *    devices that tone-map HDR to SDR in hardware regardless of colorMode)
 *
 * Both are applied together inside the SAME guarded, main-thread-posted block —
 * this is what keeps this safe: nothing outside requestColorMode() may touch
 * window.colorMode OR player.setVideoEffects for this player, ever. Doing the
 * grading call from a second, uncoordinated place would reopen the exact
 * double-writer race that caused the earlier freeze.
 */
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

    /** Attach once when the player is created/rebuilt. */
    fun attach(player: ExoPlayer) {
        attachedPlayer = player
        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                evaluateAndRequest(tracks)
            }
        })
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

    /** The HDR button calls this too — same guarded path as the automatic track listener. */
    fun requestColorMode(hdrDesired: Boolean) {
        if (hdrDesired == appliedHdr) return
        if (!isApplying.compareAndSet(false, true)) return // switch already in flight — drop it

        isSwitching = true
        mainHandler.post {
            applyColorModeInternal(hdrDesired)
            applyGradingInternal(hdrDesired)
            appliedHdr = hdrDesired
            isHdrActive = hdrDesired
            isSwitching = false
            isApplying.set(false)
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

    private fun applyGradingInternal(isHdr: Boolean) {
        val player = attachedPlayer ?: return
        player.setVideoEffects(
            if (isHdr) HdrGradingEffects.buildHdrCompensationEffects() else emptyList(),
        )
    }

    fun release() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.window.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
        }
        attachedPlayer?.setVideoEffects(emptyList())
        attachedPlayer = null
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
