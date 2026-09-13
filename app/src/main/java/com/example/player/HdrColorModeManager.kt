package com.example.player

import android.app.Activity
import android.os.Build
import android.view.Window
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi

/**
 * Applies HDR window output as soon as the selected track metadata is known,
 * before the first decoded frame reaches the video surface. This avoids the
 * first-play black flash and prevents HDR frames from being displayed through
 * an SDR window color mode on devices that do not auto-negotiate reliably.
 */
@OptIn(UnstableApi::class)
class HdrColorModeManager(private val activity: Activity) {
    private var appliedHdr = false

    fun attach(player: Player) {
        player.addListener(listener)
        applyFromTracks(player.currentTracks)
    }

    private val listener = object : Player.Listener {
        override fun onTracksChanged(tracks: Tracks) {
            applyFromTracks(tracks)
        }
    }

    private fun applyFromTracks(tracks: Tracks) {
        val isHdr = tracks.groups.any { group ->
            (0 until group.length).any { index ->
                group.isTrackSelected(index) &&
                    isHdrColorInfo(group.getTrackFormat(index).colorInfo)
            }
        }
        applyColorMode(isHdr)
    }

    private fun isHdrColorInfo(colorInfo: ColorInfo?): Boolean {
        return colorInfo != null && (
            colorInfo.colorTransfer == C.COLOR_TRANSFER_ST2084 ||
                colorInfo.colorTransfer == C.COLOR_TRANSFER_HLG
            )
    }

    private fun applyColorMode(isHdr: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || isHdr == appliedHdr) return
        appliedHdr = isHdr
        activity.runOnUiThread {
            activity.window.colorMode = if (isHdr) {
                android.content.pm.ActivityInfo.COLOR_MODE_HDR
            } else {
                android.content.pm.ActivityInfo.COLOR_MODE_DEFAULT
            }
        }
    }

    fun release() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.window.colorMode = android.content.pm.ActivityInfo.COLOR_MODE_DEFAULT
        }
        appliedHdr = false
    }
}
