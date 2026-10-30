package com.example.tickdown.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Periodic WorkManager worker that ensures all home screen widgets
 * are refreshed at least every 2 hours as an automatic fallback.
 */
class WidgetAutoRefreshWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val UNIQUE_PERIODIC_WORK = "tickdown_widget_auto_refresh_2h"

        fun schedulePeriodicRefresh(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetAutoRefreshWorker>(
                repeatInterval = 2,
                repeatIntervalTimeUnit = TimeUnit.HOURS
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        try {
            TickdownWidgetUpdater.updateAllWidgets(applicationContext)
            return Result.success()
        } catch (_: Exception) {
            return Result.retry()
        }
    }
}
