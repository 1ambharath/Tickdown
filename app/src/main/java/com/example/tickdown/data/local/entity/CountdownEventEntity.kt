package com.example.tickdown.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.tickdown.domain.model.CountdownEvent
import com.example.tickdown.domain.model.EventReminder
import com.example.tickdown.domain.model.EventStatus
import com.example.tickdown.domain.model.RecurrenceRule
import com.example.tickdown.domain.model.ReminderRule
import com.example.tickdown.domain.model.RolloverBehavior
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month

@Entity(tableName = "countdown_events")
data class CountdownEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val note: String,
    val targetDate: String,
    val targetTime: String?,
    val timeZoneId: String,
    val recurrenceType: String,
    val recurrenceParam1: Int?,
    val recurrenceParam2: String?,
    val status: String,
    val accentColorHex: String?,
    val reminderType: String,
    val reminderParam: Int?,
    val remindersOffsets: String = "",
    val rolloverBehavior: String,
    val lastCompletedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long
)

fun CountdownEventEntity.toDomain(): CountdownEvent {
    val date = LocalDate.parse(targetDate)
    val time = targetTime?.let { LocalTime.parse(it) }

    val recurrence = when (recurrenceType) {
        "ONE_TIME" -> RecurrenceRule.OneTime
        "DAILY" -> RecurrenceRule.Daily(interval = recurrenceParam1 ?: 1)
        "WEEKLY" -> {
            val days = recurrenceParam2?.split(",")
                ?.mapNotNull { str ->
                    try { DayOfWeek.valueOf(str.trim()) } catch (_: Exception) { null }
                }?.toSet() ?: setOf(DayOfWeek.MONDAY)
            RecurrenceRule.Weekly(daysOfWeek = days.ifEmpty { setOf(DayOfWeek.MONDAY) }, interval = recurrenceParam1 ?: 1)
        }
        "MONTHLY_DAY" -> RecurrenceRule.Monthly.DayOfMonth(day = recurrenceParam1 ?: 1)
        "MONTHLY_LAST_DAY" -> RecurrenceRule.Monthly.LastDay
        "YEARLY" -> {
            val month = recurrenceParam2?.let {
                try { Month.valueOf(it) } catch (_: Exception) { Month.JANUARY }
            } ?: Month.JANUARY
            RecurrenceRule.Yearly(month = month, dayOfMonth = recurrenceParam1 ?: 1)
        }
        else -> RecurrenceRule.OneTime
    }

    val eventStatus = try {
        EventStatus.valueOf(status)
    } catch (_: Exception) {
        EventStatus.ACTIVE
    }

    val rollover = try {
        RolloverBehavior.valueOf(rolloverBehavior)
    } catch (_: Exception) {
        RolloverBehavior.AUTOMATIC_NEXT_OCCURRENCE
    }

    val reminder = when (reminderType) {
        "AT_TIME" -> ReminderRule.AtTimeOfEvent
        "MINUTES_BEFORE" -> ReminderRule.MinutesBefore(reminderParam ?: 15)
        "HOURS_BEFORE" -> ReminderRule.HoursBefore(reminderParam ?: 1)
        "DAYS_BEFORE" -> ReminderRule.DaysBefore(reminderParam ?: 1)
        else -> ReminderRule.None
    }

    val remindersList = if (remindersOffsets.isNotBlank()) {
        remindersOffsets.split(",")
            .mapNotNull { it.trim().toLongOrNull() }
            .map { EventReminder(it) }
            .sorted()
    } else {
        when (reminder) {
            is ReminderRule.None -> emptyList()
            is ReminderRule.AtTimeOfEvent -> listOf(EventReminder.AT_DEADLINE)
            is ReminderRule.MinutesBefore -> listOf(EventReminder(reminder.minutes.toLong()))
            is ReminderRule.HoursBefore -> listOf(EventReminder((reminder.hours * 60).toLong()))
            is ReminderRule.DaysBefore -> listOf(EventReminder((reminder.days * 1440).toLong()))
        }
    }

    return CountdownEvent(
        id = id,
        title = title,
        note = note,
        targetDate = date,
        targetTime = time,
        timeZoneId = timeZoneId,
        recurrenceRule = recurrence,
        status = eventStatus,
        accentColorHex = accentColorHex,
        reminderRule = reminder,
        reminders = remindersList,
        rolloverBehavior = rollover,
        lastCompletedAt = lastCompletedAt?.let { Instant.ofEpochMilli(it) },
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt)
    )
}

fun CountdownEvent.toEntity(): CountdownEventEntity {
    val (recType, p1, p2) = when (val rule = recurrenceRule) {
        is RecurrenceRule.OneTime -> Triple("ONE_TIME", null, null)
        is RecurrenceRule.Daily -> Triple("DAILY", rule.interval, null)
        is RecurrenceRule.Weekly -> Triple("WEEKLY", rule.interval, rule.daysOfWeek.joinToString(",") { it.name })
        is RecurrenceRule.Monthly.DayOfMonth -> Triple("MONTHLY_DAY", rule.day, null)
        is RecurrenceRule.Monthly.LastDay -> Triple("MONTHLY_LAST_DAY", null, null)
        is RecurrenceRule.Yearly -> Triple("YEARLY", rule.dayOfMonth, rule.month.name)
    }

    val (remType, remParam) = when (val rem = reminderRule) {
        is ReminderRule.None -> Pair("NONE", null)
        is ReminderRule.AtTimeOfEvent -> Pair("AT_TIME", null)
        is ReminderRule.MinutesBefore -> Pair("MINUTES_BEFORE", rem.minutes)
        is ReminderRule.HoursBefore -> Pair("HOURS_BEFORE", rem.hours)
        is ReminderRule.DaysBefore -> Pair("DAYS_BEFORE", rem.days)
    }

    val offsetsStr = effectiveReminders.map { it.minutesBefore }.joinToString(",")

    return CountdownEventEntity(
        id = id,
        title = title,
        note = note,
        targetDate = targetDate.toString(),
        targetTime = targetTime?.toString(),
        timeZoneId = timeZoneId,
        recurrenceType = recType,
        recurrenceParam1 = p1,
        recurrenceParam2 = p2,
        status = status.name,
        accentColorHex = accentColorHex,
        reminderType = remType,
        reminderParam = remParam,
        remindersOffsets = offsetsStr,
        rolloverBehavior = rolloverBehavior.name,
        lastCompletedAt = lastCompletedAt?.toEpochMilli(),
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli()
    )
}
