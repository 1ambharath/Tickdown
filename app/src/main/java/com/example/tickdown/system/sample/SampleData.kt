package com.example.tickdown.system.sample

import com.example.tickdown.BuildConfig
import com.example.tickdown.domain.model.CountdownEvent
import com.example.tickdown.domain.model.EventStatus
import com.example.tickdown.domain.model.RecurrenceRule
import com.example.tickdown.domain.model.ReminderRule
import com.example.tickdown.domain.model.RolloverBehavior
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.ZoneId

object SampleData {

    /**
     * Curated sample events used exclusively for Compose Previews or Debug seeding.
     */
    val sampleEvents: List<CountdownEvent> by lazy {
        val today = LocalDate.now()
        val defaultZone = ZoneId.systemDefault().id

        listOf(
            CountdownEvent(
                id = 1L,
                title = "Next Gen OS Release",
                note = "Major public stable milestone deployment.",
                targetDate = today.plusDays(4),
                targetTime = LocalTime.of(18, 0),
                timeZoneId = defaultZone,
                recurrenceRule = RecurrenceRule.OneTime,
                status = EventStatus.ACTIVE,
                accentColorHex = "#2563EB",
                reminderRule = ReminderRule.HoursBefore(2),
                rolloverBehavior = RolloverBehavior.AUTOMATIC_NEXT_OCCURRENCE
            ),
            CountdownEvent(
                id = 2L,
                title = "Monthly Server Billing",
                note = "Scheduled on the 31st, adjusts on shorter months.",
                targetDate = LocalDate.of(today.year, today.month, minOf(31, today.lengthOfMonth())),
                targetTime = LocalTime.of(23, 59),
                timeZoneId = defaultZone,
                recurrenceRule = RecurrenceRule.Monthly.DayOfMonth(31),
                status = EventStatus.ACTIVE,
                accentColorHex = "#0EA5E9",
                reminderRule = ReminderRule.DaysBefore(1),
                rolloverBehavior = RolloverBehavior.REQUIRE_MANUAL_COMPLETION
            ),
            CountdownEvent(
                id = 3L,
                title = "Weekly Core Sync",
                note = "Sprint retrospective and planning.",
                targetDate = today.plusDays(1),
                targetTime = LocalTime.of(15, 30),
                timeZoneId = defaultZone,
                recurrenceRule = RecurrenceRule.Weekly(
                    daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
                    interval = 1
                ),
                status = EventStatus.ACTIVE,
                accentColorHex = null,
                reminderRule = ReminderRule.MinutesBefore(15),
                rolloverBehavior = RolloverBehavior.AUTOMATIC_NEXT_OCCURRENCE
            ),
            CountdownEvent(
                id = 4L,
                title = "Design System Freeze",
                note = "All UI tokens and components locked.",
                targetDate = today.plusDays(12),
                targetTime = null, // Date-only
                timeZoneId = defaultZone,
                recurrenceRule = RecurrenceRule.OneTime,
                status = EventStatus.ACTIVE,
                accentColorHex = "#3B82F6",
                reminderRule = ReminderRule.None,
                rolloverBehavior = RolloverBehavior.AUTOMATIC_NEXT_OCCURRENCE
            ),
            CountdownEvent(
                id = 5L,
                title = "Annual Security Audit",
                note = "Yearly compliance assessment.",
                targetDate = LocalDate.of(today.year + 1, Month.DECEMBER, 15),
                targetTime = LocalTime.of(10, 0),
                timeZoneId = defaultZone,
                recurrenceRule = RecurrenceRule.Yearly(Month.DECEMBER, 15),
                status = EventStatus.ACTIVE,
                accentColorHex = null,
                reminderRule = ReminderRule.DaysBefore(7),
                rolloverBehavior = RolloverBehavior.REQUIRE_MANUAL_COMPLETION
            )
        )
    }

    /**
     * Seeds sample data if the database is empty, only in Debug builds.
     */
    suspend fun seedIfDebug(repository: com.example.tickdown.domain.repository.CountdownRepository) {
        if (!BuildConfig.DEBUG) return
        val current = repository.observeAllEvents().first()
        if (current.isEmpty()) {
            sampleEvents.forEach { event ->
                repository.insertEvent(event.copy(id = 0))
            }
        }
    }
}
