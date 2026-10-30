package com.example.tickdown.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.state.PreferencesGlanceStateDefinition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object TickdownWidgetUpdater {

    val LAST_UPDATED_KEY = longPreferencesKey("tickdown_widget_last_updated")

    fun schedulePeriodicAlarm(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager ?: return
            val intent = Intent(context, TickdownGlanceWidgetReceiver::class.java).apply {
                action = TickdownGlanceWidgetReceiver.ACTION_AUTO_REFRESH_WIDGET
            }
            val pendingIntent = android.app.PendingIntent.getBroadcast(
                context,
                1001,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val twoHoursMillis = 2L * 60 * 60 * 1000
            val triggerAt = android.os.SystemClock.elapsedRealtime() + twoHoursMillis
            alarmManager.setInexactRepeating(
                android.app.AlarmManager.ELAPSED_REALTIME,
                triggerAt,
                twoHoursMillis,
                pendingIntent
            )
        } catch (_: Exception) {
        }
    }

    fun updateAllWidgetsAsync(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            updateAllWidgets(context)
        }
    }

    suspend fun updateAllWidgets(context: Context) {
        val now = System.currentTimeMillis()
        val widget = TickdownGlanceWidget()

        try {
            val glanceManager = GlanceAppWidgetManager(context)
            val glanceIds = mutableSetOf<GlanceId>()

            try {
                glanceIds.addAll(glanceManager.getGlanceIds(TickdownGlanceWidget::class.java))
            } catch (_: Exception) {
            }

            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val component = ComponentName(context, TickdownGlanceWidgetReceiver::class.java)
                val ids = appWidgetManager.getAppWidgetIds(component)
                ids?.forEach { appWidgetId ->
                    try {
                        glanceIds.add(glanceManager.getGlanceIdBy(appWidgetId))
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
            }

            glanceIds.forEach { glanceId ->
                try {
                    // Force state change so Glance diffing engine recognizes data changes
                    updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                        prefs.toMutablePreferences().apply {
                            this[LAST_UPDATED_KEY] = now
                        }
                    }
                    widget.update(context, glanceId)
                } catch (_: Exception) {
                    // Ignore transient update errors on individual instances
                }
            }

            try {
                widget.updateAll(context)
            } catch (_: Exception) {
            }
        } catch (_: Exception) {
            // Ignore if widget manager is unavailable
        }

        // Notify standard Android AppWidgetManager for launcher synchronization
        try {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, TickdownGlanceWidgetReceiver::class.java)
            val ids = appWidgetManager.getAppWidgetIds(component)
            if (ids != null && ids.isNotEmpty()) {
                val updateIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                    this.component = component
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(updateIntent)
            }
        } catch (_: Exception) {
            // Ignore if AppWidgetManager unavailable
        }
    }
}
