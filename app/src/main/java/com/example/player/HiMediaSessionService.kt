package com.example.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.common.ForwardingPlayer
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.R
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
        private var sessionPlayer: Player? = null
        private var onNext: (() -> Unit)? = null
        private var onPrevious: (() -> Unit)? = null

        /** Called only by a user-started playback path, not by engine pre-warming. */
        fun startPlayback(context: Context, player: Player,
                          onNext: (() -> Unit)? = null, onPrevious: (() -> Unit)? = null) {
            if (sharedPlayer !== player) sharedPlayer?.pause()
            this.onNext = onNext
            this.onPrevious = onPrevious
            updatePlayer(player)
            if (activeService == null) {
                ContextCompat.startForegroundService(context.applicationContext,
                    Intent(context.applicationContext, HiMediaSessionService::class.java))
            }
        }

        fun detachPlayer(player: Player?) {
            if (player == null || sharedPlayer !== player) return
            sharedPlayer = null
            sessionPlayer = null
            onNext = null
            onPrevious = null
            activeService?.let { service ->
                service.mediaSession?.release()
                service.mediaSession = null
                service.stopSelf()
            }
        }

        /** Decoder replacement must not steal a music session or drop video queue commands. */
        fun replacePlayer(previous: Player?, replacement: Player?) {
            if (previous != null && sharedPlayer === previous) updatePlayer(replacement)
        }

        private fun forSession(player: Player): Player {
            if (onNext == null && onPrevious == null) return player
            return object : ForwardingPlayer(player) {
                private val listeners = mutableMapOf<Player.Listener, Player.Listener>()
                private fun queueCommands(commands: Player.Commands): Player.Commands =
                    commands.buildUpon()
                        .add(Player.COMMAND_SEEK_TO_NEXT)
                        .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                        .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                        .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM).build()
                override fun getAvailableCommands(): Player.Commands = queueCommands(super.getAvailableCommands())
                override fun isCommandAvailable(command: Int): Boolean = availableCommands.contains(command)
                override fun addListener(listener: Player.Listener) {
                    val forwarding = listeners.getOrPut(listener) {
                        object : Player.Listener by listener {
                            override fun onAvailableCommandsChanged(commands: Player.Commands) {
                                listener.onAvailableCommandsChanged(queueCommands(commands))
                            }
                        }
                    }
                    super.addListener(forwarding)
                }
                override fun removeListener(listener: Player.Listener) {
                    listeners.remove(listener)?.let { super.removeListener(it) }
                }
                override fun seekToNext() { onNext?.invoke() }
                override fun seekToNextMediaItem() { onNext?.invoke() }
                override fun seekToPrevious() { onPrevious?.invoke() }
                override fun seekToPreviousMediaItem() { onPrevious?.invoke() }
            }
        }

        /**
         * Points the playback notification at the engine's current player. The
         * engine replaces its ExoPlayer when settings change; a session still
         * holding the released instance could crash or control nothing.
         * Must be called on the main thread.
         */
        fun updatePlayer(player: androidx.media3.common.Player?) {
            sharedPlayer = player
            sessionPlayer = player?.let(::forSession)
            val service = activeService ?: return
            val target = sessionPlayer
            if (target == null) {
                service.mediaSession?.release()
                service.mediaSession = null
                service.stopSelf()
                return
            }
            val session = service.mediaSession
            if (session == null) service.createSession(target)
            else session.player = target
        }
    }

    override fun onCreate() {
        super.onCreate()
        activeService = this
        createNotificationChannel()
        val notificationProvider = DefaultMediaNotificationProvider(this)
        notificationProvider.setSmallIcon(R.drawable.hi_player_notification)
        setMediaNotificationProvider(notificationProvider)
        sessionPlayer?.let(::createSession)
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
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .build()
            .also { addSession(it) }
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
