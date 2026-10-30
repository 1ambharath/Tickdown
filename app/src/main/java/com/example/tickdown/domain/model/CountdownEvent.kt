package com.example.tickdown.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class CountdownEvent(
    val id: Long = 0,
    val title: String,
    val note: String = "",
    val targetDate: LocalDate,
    val targetTime: LocalTime? = null,
    val timeZoneId: String = ZoneId.systemDefault().id,
    val recurrenceRule: RecurrenceRule = RecurrenceRule.OneTime,
    val status: EventStatus = EventStatus.ACTIVE,
    val accentColorHex: String? = null,
    val reminderRule: ReminderRule = ReminderRule.None,
    val reminders: List<EventReminder> = emptyList(),
    val rolloverBehavior: RolloverBehavior = RolloverBehavior.AUTOMATIC_NEXT_OCCURRENCE,
    val lastCompletedAt: Instant? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
) {
    val isTimed: Boolean get() = targetTime != null

    val resolvedZoneId: ZoneId
        get() = try {
            ZoneId.of(timeZoneId)
        } catch (_: Exception) {
            ZoneId.systemDefault()
        }

    /**
     * Resolves all active reminders configured for this event.
     * Merges explicit [reminders] list with legacy [reminderRule] if present.
     */
    val effectiveReminders: List<EventReminder>
        get() {
            if (reminders.isNotEmpty()) return reminders.sorted()
            return when (val r = reminderRule) {
                is ReminderRule.None -> emptyList()
                is ReminderRule.AtTimeOfEvent -> listOf(EventReminder.AT_DEADLINE)
                is ReminderRule.MinutesBefore -> listOf(EventReminder(r.minutes.toLong()))
                is ReminderRule.HoursBefore -> listOf(EventReminder((r.hours * 60).toLong()))
                is ReminderRule.DaysBefore -> listOf(EventReminder((r.days * 1440).toLong()))
            }
        }
}
