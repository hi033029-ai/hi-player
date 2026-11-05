package com.example.ui.screens

import android.content.Context
import com.example.BuildConfig
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Android schedules this outside the browser UI, including after normal process death/reboot. */
class BrowserFilterUpdateWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result {
        if (BuildConfig.PLAY_STORE_BUILD) return Result.success()
        if (isStopped) return Result.retry()
        // Disabled protections stay disabled. Checks send no account/cookie/page data.
        val store = OrbitBrowserStore(applicationContext)
        val filters = !store.adBlock || OrbitBrowserFilterEngine.autoUpdateIfStale(applicationContext) { isStopped }
        val script = !store.youtubeAdSkip || SignedBlockerUpdates.update(applicationContext) { isStopped }
        val updated = filters && script
        if (isStopped) return Result.retry()
        return if (updated) Result.success() else if (runAttemptCount < 3) Result.retry() else Result.failure()
    }

    companion object {
        fun schedule(context: Context) {
            if (BuildConfig.PLAY_STORE_BUILD) {
                val manager = WorkManager.getInstance(context.applicationContext)
                manager.cancelUniqueWork("browser-blocker-refresh")
                manager.cancelUniqueWork("browser-filter-stale-check")
                manager.cancelUniqueWork("browser-filter-daily")
                return
            }
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            val manager = WorkManager.getInstance(context.applicationContext)
            manager.cancelUniqueWork("browser-filter-daily") // Superseded daily-only schedule.
            manager.enqueueUniquePeriodicWork("browser-blocker-refresh", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<BrowserFilterUpdateWorker>(6, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                    .build())
            // Checks freshness once per app launch, not a forced download per launch.
            manager.enqueueUniqueWork("browser-filter-stale-check", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<BrowserFilterUpdateWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                    .build())
        }
    }
}
