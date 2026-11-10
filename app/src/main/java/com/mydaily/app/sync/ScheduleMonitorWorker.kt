package com.mydaily.app.sync

import android.content.Context
import android.provider.CalendarContract
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mydaily.app.data.ScheduleRepository
import com.mydaily.app.notification.CollisionNotificationManager
import com.mydaily.app.widget.MyDailyWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Background worker that monitors Android Calendar Provider changes and runs periodic
 * routine checks, updating the Home Screen Widget and sending collision notifications.
 */
class ScheduleMonitorWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val repo = ScheduleRepository(applicationContext)
        if (repo.getIcsFeeds().isNotEmpty() || repo.getGoogleTasksToken().isNotBlank()) {
            runCatching { repo.syncRemoteSources() }
        }

        CollisionNotificationManager.evaluateAndNotify(applicationContext)
        MyDailyWidgetProvider.refreshAllWidgets(applicationContext)

        // Re-arm the ContentUriTrigger so future Calendar changes continue to wake us up
        enqueueCalendarContentTrigger(applicationContext)
        Result.success()
    }

    companion object {
        private const val WORK_PERIODIC_ROUTINE = "mydaily_periodic_routine_check"
        private const val WORK_CONTENT_URI_TRIGGER = "mydaily_calendar_uri_trigger"
        private const val WORK_IMMEDIATE_CHECK = "mydaily_immediate_check"
        private const val WORK_NEXT_EXPIRATION_CHECK = "mydaily_next_expiration_check"

        fun startRoutineMonitoring(context: Context) {
            enqueuePeriodicRoutineCheck(context)
            enqueueCalendarContentTrigger(context)
        }

        fun scheduleNextExpirationCheck(context: Context, delayMillis: Long) {
            val request = OneTimeWorkRequestBuilder<ScheduleMonitorWorker>()
                .setInitialDelay(delayMillis.coerceAtLeast(5_000L), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NEXT_EXPIRATION_CHECK,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun enqueueImmediateCheck(context: Context) {
            val request = OneTimeWorkRequestBuilder<ScheduleMonitorWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_IMMEDIATE_CHECK,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun enqueuePeriodicRoutineCheck(context: Context) {
            val periodicRequest = PeriodicWorkRequestBuilder<ScheduleMonitorWorker>(
                15, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_PERIODIC_ROUTINE,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicRequest
            )
        }

        fun enqueueCalendarContentTrigger(context: Context) {
            val constraints = Constraints.Builder()
                .addContentUriTrigger(CalendarContract.Events.CONTENT_URI, true)
                .addContentUriTrigger(CalendarContract.Instances.CONTENT_URI, true)
                .addContentUriTrigger(CalendarContract.Calendars.CONTENT_URI, true)
                .setTriggerContentUpdateDelay(500, TimeUnit.MILLISECONDS)
                .setTriggerContentMaxDelay(2, TimeUnit.SECONDS)
                .build()

            val triggerRequest = OneTimeWorkRequestBuilder<ScheduleMonitorWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_CONTENT_URI_TRIGGER,
                ExistingWorkPolicy.REPLACE,
                triggerRequest
            )
        }
    }
}

