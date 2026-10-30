package com.example.tickdown.system.scheduler

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.tickdown.data.local.AppDatabase
import com.example.tickdown.data.local.entity.toDomain
import com.example.tickdown.domain.model.EventStatus
import com.example.tickdown.widget.TickdownWidgetUpdater
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Persistent deferrable background worker that reconciles AlarmManager schedules,
 * cleans up historical delivery records, and verifies widget synchronization.
 * Not used as a substitute for precise alarms.
 */
class ReminderReconciliationWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val UNIQUE_PERIODIC_WORK = "tickdown_reminder_reconciliation_periodic"
        private const val UNIQUE_ONE_TIME_WORK = "tickdown_reminder_reconciliation_one_time"

        /**
         * Enqueues immediate reconciliation after device reboot, app update,
         * timezone change, or permission change.
         */
        fun enqueueOneTimeReconciliation(context: Context) {
            val request = OneTimeWorkRequestBuilder<ReminderReconciliationWorker>()
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_ONE_TIME_WORK,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        /**
         * Registers a periodic maintenance work (runs every 12 hours) to ensure
         * long-term reconciliation without battery drain.
         */
        fun schedulePeriodicReconciliation(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderReconciliationWorker>(
                repeatInterval = 12,
                repeatIntervalTimeUnit = TimeUnit.HOURS
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        val context = applicationContext
        val db = AppDatabase.getDatabase(context)

        try {
            // 1. Fetch all active events and reschedule alarms
            val allEntities = db.countdownDao().getAllEventsFlow().first()
            val activeEvents = allEntities
                .map { it.toDomain() }
                .filter { it.status == EventStatus.ACTIVE }

            activeEvents.forEach { event ->
                ReminderScheduler.scheduleRemindersForEvent(context, event)
            }

            // 2. Clean up historical delivery records older than 30 days
            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            db.reminderDeliveryDao().cleanOldDeliveries(thirtyDaysAgo)

            // 3. Ensure widgets are updated
            TickdownWidgetUpdater.updateAllWidgets(context)

            return Result.success()
        } catch (_: Exception) {
            return Result.retry()
        }
    }
}
