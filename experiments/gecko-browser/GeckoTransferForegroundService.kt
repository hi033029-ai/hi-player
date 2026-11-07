package com.example.browser.gecko

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Approved streams only. No source URLs, headers or cookies are stored. */
internal class GeckoTransferForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == CANCEL) cancelAll()
        if (tasks.isEmpty()) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY }
        val manager = getSystemService(NotificationManager::class.java) ?: error("Notification service unavailable")
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, "Browser downloads", NotificationManager.IMPORTANCE_LOW))
        val cancel = PendingIntent.getService(this, 4183, Intent(this, GeckoTransferForegroundService::class.java).setAction(CANCEL), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = (if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this))
            .setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("Hi Player download")
            .setContentText("Downloading an approved browser file").setOngoing(true).setProgress(0, 0, true)
            .addAction(Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancel).build()).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(ID, notification)
        return START_NOT_STICKY
    }
    override fun onTimeout(startId: Int, fgsType: Int) { cancelAll(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
    override fun onDestroy() { cancelAll(); super.onDestroy() }
    private fun cancelAll() { val actions = tasks.values.toList(); tasks.clear(); actions.forEach { runCatching(it) } }
    companion object {
        private const val CHANNEL = "gecko_stream_downloads"
        private const val ID = 4183
        private const val CANCEL = "com.example.browser.gecko.CANCEL_DOWNLOADS"
        private val tasks = ConcurrentHashMap<String, () -> Unit>()
        fun acquire(context: Context, cancel: () -> Unit): AutoCloseable {
            val app = context.applicationContext
            val key = UUID.randomUUID().toString()
            tasks[key] = cancel
            try { ContextCompat.startForegroundService(app, Intent(app, GeckoTransferForegroundService::class.java)) }
            catch (error: Exception) { tasks.remove(key); throw error }
            val closed = AtomicBoolean(false)
            return AutoCloseable {
                if (closed.compareAndSet(false, true)) {
                    tasks.remove(key)
                    runCatching { app.startService(Intent(app, GeckoTransferForegroundService::class.java)) }
                }
            }
        }
    }
}
