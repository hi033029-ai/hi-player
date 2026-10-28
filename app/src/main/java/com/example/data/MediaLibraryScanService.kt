package com.example.data

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import kotlinx.coroutines.*

/** Android-managed background indexing, independent of the visible tab/player. */
class MediaLibraryScanService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = java.util.concurrent.ConcurrentHashMap<Int, Job>()
    override fun onStartJob(params: JobParameters): Boolean {
        jobs[params.jobId] = scope.launch {
            try {
                val video = MediaScanner.scanLocalVideos(applicationContext)
                if (video.complete) MediaLibraryCache.saveVideos(applicationContext, video.videos)
                ensureActive()
                val audio = AudioMediaScanner.scan(applicationContext)
                if (audio.complete) MediaLibraryCache.saveAudio(applicationContext, audio.tracks)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (e: Exception) { android.util.Log.w("HiLibrary", "Background scan deferred", e)
            } finally {
                jobs.remove(params.jobId)
                if (isActive) {
                    jobFinished(params, false)
                    if (params.jobId == CHANGES) scheduleChanges(applicationContext)
                }
            }
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { jobs.remove(params.jobId)?.cancel(); return true }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    companion object {
        internal const val PERIODIC = 4301
        internal const val CHANGES = 4302
        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            if (scheduler.getPendingJob(PERIODIC) == null) scheduler.schedule(JobInfo.Builder(PERIODIC, ComponentName(context, MediaLibraryScanService::class.java))
                .setPeriodic(15 * 60_000L).setPersisted(true).build())
            if (scheduler.getPendingJob(CHANGES) == null) scheduleChanges(context)
        }
        private fun scheduleChanges(context: Context) {
            context.getSystemService(JobScheduler::class.java).schedule(JobInfo.Builder(CHANGES, ComponentName(context, MediaLibraryScanService::class.java))
                .addTriggerContentUri(JobInfo.TriggerContentUri(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
                .addTriggerContentUri(JobInfo.TriggerContentUri(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
                .setTriggerContentUpdateDelay(2_000).setTriggerContentMaxDelay(10_000).build())
        }
    }
}
class MediaLibraryBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { MediaLibraryScanService.schedule(context) }
}
