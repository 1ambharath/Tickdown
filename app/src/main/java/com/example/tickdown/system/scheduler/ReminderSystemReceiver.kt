package com.example.tickdown.system.scheduler

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.tickdown.widget.TickdownWidgetUpdater

class ReminderSystemReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> {
                // Enqueue persistent reconciliation with WorkManager
                ReminderReconciliationWorker.enqueueOneTimeReconciliation(context)
                // Refresh home-screen widgets
                TickdownWidgetUpdater.updateAllWidgetsAsync(context)
            }
        }
    }
}
