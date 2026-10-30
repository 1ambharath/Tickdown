package com.example.tickdown.system.scheduler

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import com.example.tickdown.MainActivity
import com.example.tickdown.R
import com.example.tickdown.data.local.AppDatabase
import com.example.tickdown.data.local.entity.ReminderDeliveryEntity
import com.example.tickdown.data.local.entity.toDomain
import com.example.tickdown.domain.model.EventReminder
import com.example.tickdown.domain.model.EventStatus
import com.example.tickdown.domain.model.RecurrenceRule
import com.example.tickdown.domain.recurrence.RecurrenceEngine
import com.example.tickdown.widget.TickdownWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class ReminderAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "tickdown_event_reminders"
        const val CHANNEL_NAME = "Event Deadlines & Reminders"
        const val ACTION_COMPLETE_FROM_NOTIF = "com.example.tickdown.ACTION_COMPLETE_FROM_NOTIF"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getLongExtra(ReminderScheduler.EXTRA_EVENT_ID, -1L)
        val offsetMinutes = intent.getLongExtra(ReminderScheduler.EXTRA_OFFSET_MINUTES, -1L)
        val occurrenceEpochSec = intent.getLongExtra(ReminderScheduler.EXTRA_OCCURRENCE_EPOCH_SEC, -1L)

        if (eventId <= 0 || offsetMinutes < 0 || occurrenceEpochSec <= 0) {
            return
        }

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleAlarm(context, eventId, offsetMinutes, occurrenceEpochSec)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleAlarm(
        context: Context,
        eventId: Long,
        offsetMinutes: Long,
        occurrenceEpochSec: Long
    ) {
        val db = AppDatabase.getDatabase(context)

        // 1. Prevent duplicate delivery: Check if already delivered
        val alreadyDelivered = db.reminderDeliveryDao().isDelivered(
            eventId = eventId,
            offsetMinutes = offsetMinutes,
            occurrenceEpochSec = occurrenceEpochSec
        )
        if (alreadyDelivered) {
            return
        }

        // 2. Validate that event still exists and is ACTIVE
        val event = db.countdownDao().getEventById(eventId)?.toDomain()
        if (event == null || event.status != EventStatus.ACTIVE) {
            return
        }

        // 3. Validate that the occurrence being alerted is still valid
        val now = ZonedDateTime.now(event.resolvedZoneId)
        val effectiveOccurrence = RecurrenceEngine.resolveEffectiveOccurrence(event, now)
        val effectiveEpochSec = effectiveOccurrence.toEpochSecond()

        // If the event is recurring and already advanced past this occurrence by more than a day, skip
        if (event.recurrenceRule !is RecurrenceRule.OneTime && (effectiveEpochSec - occurrenceEpochSec) > 86400) {
            return
        }

        // 4. Record delivery state to prevent duplicate alerts on re-broadcasts
        db.reminderDeliveryDao().insertDelivery(
            ReminderDeliveryEntity(
                eventId = eventId,
                reminderOffsetMinutes = offsetMinutes,
                occurrenceEpochSecond = occurrenceEpochSec,
                deliveredAt = System.currentTimeMillis()
            )
        )

        // 5. Post user-facing notification if permission is granted
        postNotification(context, event, offsetMinutes, effectiveOccurrence)

        // 6. Refresh widgets to reflect current countdown state
        TickdownWidgetUpdater.updateAllWidgets(context)

        // 7. Reschedule upcoming reminders for next cycle if applicable
        ReminderScheduler.scheduleRemindersForEvent(context, event)
    }

    private fun postNotification(
        context: Context,
        event: com.example.tickdown.domain.model.CountdownEvent,
        offsetMinutes: Long,
        occurrenceZdt: ZonedDateTime
    ) {
        // Notification permission check for Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        createNotificationChannel(context)

        // Tapping notification opens Event Detail
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_VIEW_EVENT
            putExtra(MainActivity.EXTRA_EVENT_ID, event.id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            event.id.toInt(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val reminderLabel = EventReminder(offsetMinutes).label
        val dateFormatted = occurrenceZdt.format(DateTimeFormatter.ofPattern("MMM dd • HH:mm"))

        val contentText = if (offsetMinutes == 0L) {
            "Deadline reached now! ($dateFormatted)"
        } else {
            "Due in $reminderLabel ($dateFormatted)"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(event.title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                if (event.note.isNotBlank()) "$contentText\n${event.note}" else contentText
            ))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationId = (event.id * 31 + offsetMinutes).toInt()
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Gracefully ignore if notification permission revoked
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts and reminders for tracked countdown events"
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }
}
