package com.example.downloads

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Foreground lifetime only. Networking and queueing belong entirely to Hi Browser. */
class BrowserDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null
    private var lastStartId = 0
    private var idleStop = false
    private val wakeLock by lazy {
        (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:browser-downloads").apply { setReferenceCounted(false) }
    }
    private fun keepCpuAwake() { if (!wakeLock.isHeld) wakeLock.acquire(15 * 60 * 1000L) }
    private fun releaseCpu() { if (wakeLock.isHeld) wakeLock.release() }
    private val notifications get() = getSystemService(NotificationManager::class.java)

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, "Hi Browser downloads", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Download progress and pause/resume controls"
                setShowBadge(false)
            })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        idleStop = false
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification("Preparing downloads", false).build(),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0)
        keepCpuAwake()
        BrowserDownloads.execute(applicationContext, intent) {
            scope.launch {
                if (observer?.isActive != true) observer = scope.launch {
                    while (true) {
                        delay(750)
                        val items = BrowserDownloads.downloads.value
                        val running = items.filter { it.status == BrowserDownloadStatus.RUNNING }
                        val queued = items.count { it.status == BrowserDownloadStatus.QUEUED }
                        if (running.isEmpty() && queued == 0 && !BrowserDownloads.hasPendingCommands()) {
                            val paused = items.count { it.status == BrowserDownloadStatus.PAUSED }
                            val failed = items.count { it.status == BrowserDownloadStatus.FAILED }
                            val complete = items.count { it.status == BrowserDownloadStatus.COMPLETED }
                            val text = when {
                                paused > 0 -> "$paused paused • tap Resume to continue"
                                failed > 0 -> "$failed failed • open Hi Browser to retry"
                                else -> "$complete downloads completed"
                            }
                            val note = notification(text, false, resume = paused > 0).build()
                            idleStop = true
                            releaseCpu()
                            stopForeground(STOP_FOREGROUND_DETACH)
                            runCatching { notifications.notify(NOTIFICATION_ID, note) }
                            stopSelf(lastStartId)
                            break
                        }
                        keepCpuAwake()
                        val bytes = running.sumOf { it.speedBytesPerSecond }
                        val text = if (running.size == 1) running.first().fileName else "${running.size} downloading • $queued queued"
                        val builder = notification(text, true).setSubText("${formatSpeed(bytes)}/s • Hi Browser engine")
                        val single = running.singleOrNull()
                        if (single != null && single.totalBytes > 0) {
                            builder.setProgress(100, ((single.downloadedBytes.toDouble() / single.totalBytes) * 100).toInt().coerceIn(0, 100), false)
                        } else builder.setProgress(0, 0, true)
                        runCatching { notifications.notify(NOTIFICATION_ID, builder.build()) }
                    }
                }
            }
        }
        // Interrupted transfers are recovered as paused from the private journal, not silently restarted.
        return START_NOT_STICKY
    }

    private fun notification(text: String, ongoing: Boolean, resume: Boolean = false): NotificationCompat.Builder {
        val launch = Intent(this, MainActivity::class.java).setAction("com.example.OPEN_BROWSER_DOWNLOADS").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val content = PendingIntent.getActivity(this, 701, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Hi Browser downloads").setContentText(text).setContentIntent(content)
            .setOnlyAlertOnce(true).setOngoing(ongoing).setAutoCancel(!ongoing)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS).setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (ongoing || resume) {
            val action = if (resume) BrowserDownloads.ACTION_RESUME_ALL else BrowserDownloads.ACTION_PAUSE_ALL
            val intent = Intent(this, BrowserDownloadService::class.java).setAction(action)
            val control = if (Build.VERSION.SDK_INT >= 26) PendingIntent.getForegroundService(this, if (resume) 703 else 702, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                else PendingIntent.getService(this, if (resume) 703 else 702, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(if (resume) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause, if (resume) "Resume all" else "Pause all", control)
        }
        return builder
    }

    // Android 15+ limits dataSync foreground service time. Preserve the queue and stop promptly.
    override fun onTimeout(startId: Int, fgsType: Int) {
        BrowserDownloads.pauseForServiceStop(applicationContext)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onDestroy() {
        if (!idleStop) BrowserDownloads.pauseForServiceStop(applicationContext)
        releaseCpu()
        scope.cancel()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "hi_browser_native_downloads"
        private const val NOTIFICATION_ID = 4601
        private fun formatSpeed(bytes: Long) = when {
            bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / 1048576.0)
            bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}
