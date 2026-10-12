package io.github.volodiapg.fossilfeed

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.provider.CalendarContract

/** Runs the push every 15 minutes, and shortly after the calendar changes. */
class PushJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            try {
                Pusher.push(this, force = false)
            } finally {
                // content-triggered jobs fire once: schedule the next one
                schedule(this)
                jobFinished(params, false)
            }
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    companion object {
        private const val PERIODIC_ID = 1
        private const val CALENDAR_CHANGE_ID = 2

        fun schedule(ctx: Context) {
            val scheduler = ctx.getSystemService(JobScheduler::class.java)
            val component = ComponentName(ctx, PushJobService::class.java)

            if (scheduler.getPendingJob(PERIODIC_ID) == null) {
                scheduler.schedule(
                    JobInfo.Builder(PERIODIC_ID, component)
                        .setPeriodic(15 * 60 * 1000L)
                        .setPersisted(true)
                        .build()
                )
            }
            scheduler.schedule(
                JobInfo.Builder(CALENDAR_CHANGE_ID, component)
                    .addTriggerContentUri(
                        JobInfo.TriggerContentUri(CalendarContract.Events.CONTENT_URI, JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS)
                    )
                    .setTriggerContentUpdateDelay(10_000L)
                    .setTriggerContentMaxDelay(60_000L)
                    .build()
            )
        }
    }
}
