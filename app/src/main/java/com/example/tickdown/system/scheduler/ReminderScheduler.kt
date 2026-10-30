package com.example.tickdown.system.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.example.tickdown.domain.model.CountdownEvent
import com.example.tickdown.domain.model.EventStatus
import com.example.tickdown.domain.recurrence.RecurrenceEngine
import java.time.Instant
import java.time.ZonedDateTime

object ReminderScheduler {

    const val ACTION_FIRE_REMINDER = "com.example.tickdown.ACTION_FIRE_REMINDER"
    const val EXTRA_EVENT_ID = "extra_event_id"
    const val EXTRA_OFFSET_MINUTES = "extra_offset_minutes"
    const val EXTRA_OCCURRENCE_EPOCH_SEC = "extra_occurrence_epoch_sec"

    /**
     * Checks whether the app has permission to schedule exact alarms.
     * On Android 12+ (API 31+), verifies AlarmManager.canScheduleExactAlarms().
     */
    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.canScheduleExactAlarms() ?: false
        } else {
            true
        }
    }

    /**
     * Schedules all pending reminders for the next active occurrence of [event].
     * Uses exact alarms if permission is granted, otherwise gracefully falls back to inexact idle alarms.
     */
    fun scheduleRemindersForEvent(context: Context, event: CountdownEvent) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        // If event is not active or has no reminders, cancel any pending alarms
        if (event.status != EventStatus.ACTIVE || event.effectiveReminders.isEmpty()) {
            cancelRemindersForEvent(context, event)
            return
        }

        val now = ZonedDateTime.now(event.resolvedZoneId)
        val occurrenceZdt = RecurrenceEngine.resolveEffectiveOccurrence(event, now)
        val occurrenceEpochSec = occurrenceZdt.toEpochSecond()

        val canExact = canScheduleExactAlarms(context)

        event.effectiveReminders.forEach { reminder ->
            val triggerInstant = occurrenceZdt.toInstant().minusSeconds(reminder.minutesBefore * 60)

            // Only schedule reminders that are in the future
            if (triggerInstant.isAfter(Instant.now())) {
                val pendingIntent = createPendingIntent(
                    context = context,
                    eventId = event.id,
                    offsetMinutes = reminder.minutesBefore,
                    occurrenceEpochSec = occurrenceEpochSec
                )

                val triggerMillis = triggerInstant.toEpochMilli()

                try {
                    if (canExact) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerMillis,
                            pendingIntent
                        )
                    } else {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerMillis,
                            pendingIntent
                        )
                    }
                } catch (_: SecurityException) {
                    // Fallback if exact alarm permission was revoked between check and set
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerMillis,
                        pendingIntent
                    )
                }
            }
        }
    }

    /**
     * Cancels all scheduled reminder alarms for [event].
     */
    fun cancelRemindersForEvent(context: Context, event: CountdownEvent) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val occurrenceZdt = RecurrenceEngine.getAnchorZonedDateTime(event)
        val occurrenceEpochSec = occurrenceZdt.toEpochSecond()

        event.effectiveReminders.forEach { reminder ->
            val pendingIntent = createPendingIntent(
                context = context,
                eventId = event.id,
                offsetMinutes = reminder.minutesBefore,
                occurrenceEpochSec = occurrenceEpochSec
            )
            alarmManager.cancel(pendingIntent)
        }
    }

    /**
     * Creates a stable, uniquely identified PendingIntent using explicit Uri data and request code.
     */
    fun createPendingIntent(
        context: Context,
        eventId: Long,
        offsetMinutes: Long,
        occurrenceEpochSec: Long
    ): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_FIRE_REMINDER
            // Explicit URI ensures unique Intent identity in Android's Intent.filterEquals()
            data = Uri.parse("tickdown://reminder/$eventId/$offsetMinutes/$occurrenceEpochSec")
            putExtra(EXTRA_EVENT_ID, eventId)
            putExtra(EXTRA_OFFSET_MINUTES, offsetMinutes)
            putExtra(EXTRA_OCCURRENCE_EPOCH_SEC, occurrenceEpochSec)
        }

        val requestCode = generateRequestCode(eventId, offsetMinutes, occurrenceEpochSec)

        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun generateRequestCode(eventId: Long, offsetMinutes: Long, occurrenceEpochSec: Long): Int {
        var hash = 17
        hash = 31 * hash + (eventId xor (eventId ushr 32)).toInt()
        hash = 31 * hash + (offsetMinutes xor (offsetMinutes ushr 32)).toInt()
        hash = 31 * hash + ((occurrenceEpochSec % 100000) xor ((occurrenceEpochSec % 100000) ushr 32)).toInt()
        return hash
    }
}
