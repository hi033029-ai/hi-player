package com.example.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.MainActivity

@OptIn(UnstableApi::class)
class HiMediaSessionService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    companion object {
        const val CHANNEL_ID = "hi_player_playback_channel"
        const val NOTIFICATION_ID = 4001
        var sharedPlayer: androidx.media3.common.Player? = null
            private set

        private var activeService: HiMediaSessionService? = null

        /**
         * Points the playback notification at the engine's current player. The
         * engine replaces its ExoPlayer when settings change; a session still
         * holding the released instance could crash or control nothing.
         * Must be called on the main thread.
         */
        fun updatePlayer(player: androidx.media3.common.Player?) {
            sharedPlayer = player
            val service = activeService ?: return
            if (player == null) return
            val session = service.mediaSession
            if (session == null) {
                service.createSession(player)
            } else if (session.player !== player) {
                runCatching { session.player = player }
                    .onFailure {
                        session.release()
                        service.mediaSession = null
                        service.createSession(player)
                    }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        activeService = this
        createNotificationChannel()
        sharedPlayer?.let(::createSession)
    }

    private fun createSession(player: androidx.media3.common.Player) {
        val sessionActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            sessionActivityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        mediaSession = runCatching {
            MediaSession.Builder(this, player)
                .setSessionActivity(pendingIntent)
                .build()
        }.getOrNull()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Hi Player Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "4K UHD Remux Background & Media Notification"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        if (activeService === this) activeService = null
        mediaSession?.run {
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
