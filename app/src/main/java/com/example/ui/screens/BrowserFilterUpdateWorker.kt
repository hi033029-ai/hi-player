package com.example.ui.screens

import android.content.Context
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
        if (isStopped) return Result.retry()
        // Disabled blocking stays disabled. No account, website URL or cookie is sent.
        if (!OrbitBrowserStore(applicationContext).adBlock) return Result.success()
        val updated = OrbitBrowserFilterEngine.autoUpdateIfStale(applicationContext) { isStopped }
        if (isStopped) return Result.retry()
        return if (updated) Result.success() else if (runAttemptCount < 3) Result.retry() else Result.failure()
    }

    companion object {
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            val manager = WorkManager.getInstance(context.applicationContext)
            manager.enqueueUniquePeriodicWork("browser-filter-daily", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<BrowserFilterUpdateWorker>(24, TimeUnit.HOURS)
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
