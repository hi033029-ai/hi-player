package com.example.browser.gecko

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSession as AndroidSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.IBinder
import com.example.MainActivity
import com.example.R
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.MediaSession
import java.util.UUID

/** One opt-in regular tab owns the system media controls. No URL or credentials enter an Intent. */
internal class BrowserMediaControls(private val app: Context, val tab: GeckoSession) : MediaSession.Delegate {
    companion object {
        var current: BrowserMediaControls? = null
            private set
    }

    val id = UUID.randomUUID().toString()
    var playing = false
        private set
    private var target: MediaSession? = null
    private var platform: AndroidSession? = null
    private var features = 0L
    private var title = ""
    private var artist = ""
    private var canRepeat = false
    private var repeating = false
    private var canShuffle = false
    private var shuffling = false
    init {
        current = this
        tab.settings.setSuspendMediaWhenInactive(false)
        tab.mediaSessionDelegate = this
        GeckoFeatureBridge.mediaCommand(tab, "report-modes")
    }

    private val callbacks = object : AndroidSession.Callback() {
        override fun onPlay() = command("play")
        override fun onPause() = command("pause")
        override fun onSkipToNext() = command("next")
        override fun onSkipToPrevious() = command("previous")
        override fun onCustomAction(action: String, extras: Bundle?) = command(action)
    }

    override fun onActivated(session: GeckoSession, mediaSession: MediaSession) {
        target = mediaSession
    }
    override fun onFeatures(session: GeckoSession, mediaSession: MediaSession, features: Long) {
        if (target !== mediaSession) return
        this.features = features
        refresh()
    }
    override fun onMetadata(session: GeckoSession, mediaSession: MediaSession, meta: MediaSession.Metadata) {
        if (target !== mediaSession) return
        title = meta.title.orEmpty().take(300)
        artist = meta.artist.orEmpty().take(200)
        refresh()
    }
    override fun onPlay(session: GeckoSession, mediaSession: MediaSession) {
        target = mediaSession
        if (platform == null) platform = AndroidSession(app, "Hi Browser").apply {
            setCallback(callbacks)
            isActive = true
        }
        playing = true
        GeckoFeatureBridge.mediaCommand(tab, "report-modes")
        refresh()
        if (GeckoMediaPlaybackService.instance == null) {
            runCatching { app.startForegroundService(Intent(app, GeckoMediaPlaybackService::class.java)) }
        }
    }
    override fun onPause(session: GeckoSession, mediaSession: MediaSession) {
        if (target === mediaSession) { playing = false; refresh() }
    }
    override fun onStop(session: GeckoSession, mediaSession: MediaSession) {
        if (target === mediaSession) endPlayback()
    }
    override fun onDeactivated(session: GeckoSession, mediaSession: MediaSession) {
        if (target !== mediaSession) return
        endPlayback()
        target = null
        features = 0L
        title = ""
        artist = ""
    }
    fun setModes(repeatAvailable: Boolean, repeat: Boolean, shuffleAvailable: Boolean, shuffle: Boolean) {
        canRepeat = repeatAvailable
        repeating = repeat
        canShuffle = shuffleAvailable
        shuffling = shuffle
        refresh()
    }

    private fun supports(flag: Long) = features and flag != 0L
    fun command(action: String) {
        if (current !== this || platform == null) return
        when (action) {
            "play" -> if (!playing && supports(MediaSession.Feature.PLAY)) target?.play()
            "pause" -> if (playing && supports(MediaSession.Feature.PAUSE)) target?.pause()
            "next" -> if (supports(MediaSession.Feature.NEXT_TRACK)) target?.nextTrack()
            "previous" -> if (supports(MediaSession.Feature.PREVIOUS_TRACK)) target?.previousTrack()
            "shuffle" -> if (canShuffle) GeckoFeatureBridge.mediaCommand(tab, action)
            "repeat-one" -> if (canRepeat) GeckoFeatureBridge.mediaCommand(tab, action)
        }
    }

    private data class Button(val command: String, val label: String, val icon: Int, val action: Long = 0)
    private fun buttons() = buildList {
        if (supports(MediaSession.Feature.PREVIOUS_TRACK))
            add(Button("previous", "Previous", android.R.drawable.ic_media_previous, PlaybackState.ACTION_SKIP_TO_PREVIOUS))
        if (playing && supports(MediaSession.Feature.PAUSE))
            add(Button("pause", "Pause", android.R.drawable.ic_media_pause, PlaybackState.ACTION_PAUSE))
        if (!playing && supports(MediaSession.Feature.PLAY))
            add(Button("play", "Play", android.R.drawable.ic_media_play, PlaybackState.ACTION_PLAY))
        if (supports(MediaSession.Feature.NEXT_TRACK))
            add(Button("next", "Next", android.R.drawable.ic_media_next, PlaybackState.ACTION_SKIP_TO_NEXT))
        if (canShuffle) add(Button("shuffle", if (shuffling) "Turn shuffle off" else "Shuffle", R.drawable.browser_shuffle))
        if (canRepeat) add(Button("repeat-one", if (repeating) "Turn repeat off" else "Repeat one", R.drawable.browser_repeat_one))
    }

    private fun refresh() {
        val session = platform ?: return
        val buttons = buttons()
        val playback = PlaybackState.Builder().setState(
            if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
            PlaybackState.PLAYBACK_POSITION_UNKNOWN, if (playing) 1f else 0f)
        playback.setActions(buttons.fold(0L) { actions, button -> actions or button.action })
        buttons.filter { it.action == 0L }.forEach { playback.addCustomAction(it.command, it.label, it.icon) }
        session.setPlaybackState(playback.build())
        session.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, title.ifBlank { "Hi Browser" })
            .putString(MediaMetadata.METADATA_KEY_ARTIST, artist).build())
        GeckoMediaPlaybackService.instance?.render()
    }

    fun notification(service: Service): Notification? {
        val session = platform ?: return null
        val style = Notification.MediaStyle().setMediaSession(session.sessionToken)
        val builder = Notification.Builder(service, GeckoMediaPlaybackService.CHANNEL)
            .setSmallIcon(R.drawable.hi_player_notification).setContentTitle(title.ifBlank { "Hi Browser" })
            .setContentText(artist).setOnlyAlertOnce(true).setOngoing(playing)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = Intent(service, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        builder.setContentIntent(PendingIntent.getActivity(service, 4184, open, flags))
        val buttons = buttons()
        buttons.forEach { button ->
            val intent = Intent(service, GeckoMediaPlaybackService::class.java).setAction(button.command).putExtra("owner", id)
            builder.addAction(button.icon, button.label, PendingIntent.getForegroundService(service, id.hashCode(), intent, flags))
        }
        style.setShowActionsInCompactView(*buttons.indices.take(3).toIntArray())
        return builder.setStyle(style).build()
    }

    private fun endPlayback() {
        playing = false
        platform?.release()
        platform = null
        GeckoMediaPlaybackService.instance?.render()
        app.getSystemService(NotificationManager::class.java)?.cancel(GeckoMediaPlaybackService.ID)
    }
    fun close() {
        if (current !== this) return
        tab.mediaSessionDelegate = null
        tab.settings.setSuspendMediaWhenInactive(true)
        endPlayback()
        current = null
    }
}

internal class GeckoMediaPlaybackService : Service() {
    companion object {
        const val CHANNEL = "browser_media_playback"
        const val ID = 4184
        var instance: GeckoMediaPlaybackService? = null
            private set
    }
    private var foreground = false
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL, "Browser playback", NotificationManager.IMPORTANCE_LOW))
        instance = this
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val owner = BrowserMediaControls.current
        if (intent?.getStringExtra("owner") == owner?.id) intent?.action?.let { owner?.command(it) }
        render(starting = true)
        return START_NOT_STICKY
    }
    fun render(starting: Boolean = false) {
        val owner = BrowserMediaControls.current
        val notification = owner?.notification(this)
        if (notification == null) {
            if (starting) startForeground(ID, Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.hi_player_notification).setContentTitle("Hi Browser").build())
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        if (starting || owner?.playing == true) {
            if (!foreground || starting) {
                startForeground(ID, notification)
                foreground = true
            } else getSystemService(NotificationManager::class.java)?.notify(ID, notification)
        } else getSystemService(NotificationManager::class.java)?.notify(ID, notification)
        if (owner?.playing != true && foreground) {
            stopForeground(STOP_FOREGROUND_DETACH)
            foreground = false
        }
    }
    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }
}
