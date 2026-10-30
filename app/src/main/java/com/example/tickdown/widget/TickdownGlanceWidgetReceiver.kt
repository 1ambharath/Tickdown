package com.example.tickdown.widget

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class TickdownGlanceWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = TickdownGlanceWidget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_AUTO_REFRESH_WIDGET,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED -> {
                TickdownWidgetUpdater.updateAllWidgetsAsync(context)
                TickdownWidgetUpdater.schedulePeriodicAlarm(context)
                WidgetAutoRefreshWorker.schedulePeriodicRefresh(context)
            }
        }
    }

    companion object {
        const val ACTION_AUTO_REFRESH_WIDGET = "com.example.tickdown.ACTION_AUTO_REFRESH_WIDGET"
    }
}
